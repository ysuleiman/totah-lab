package totah.lab.athena.interaction;

import totah.lab.athena.interaction.perception.AromaticRing;
import totah.lab.athena.interaction.perception.AromaticRingPerception;
import totah.lab.athena.interaction.perception.ChargedGroup;
import totah.lab.athena.interaction.perception.ChargedGroupPerception;
import totah.lab.athena.interaction.perception.HydrophobicAtomPerception;
import totah.lab.athena.interaction.perception.HydrophobicAtoms;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Thin orchestrator over the perception and detector layer: perceives
 * each structure separately (never partitioning a merged perception
 * list), runs all six detectors, applies the PLIP precedence graph via
 * {@link InteractionRefinements#refineAll}, and returns an immutable
 * {@link InteractionProfile} carrying refined and raw interactions, the
 * thresholds used, and per-side perception provenance summaries.
 *
 * <p>Input model: the ligand is always its own {@link Structure} (the
 * gaia {@code Ligand} wrapper is unwrapped by the caller via
 * {@code ligand.structure()}). The environment ("everything that is not
 * the ligand") comes in three supported shapes:
 * <ul>
 *   <li>{@link #profile(Structure, Structure)} — a prepared receptor
 *   structure. It may already contain fixed-cofactor residues (e.g. the
 *   hephaestus {@code ReceptorAssembly} merge carries SAM as a HETATM
 *   residue on its own chain); those are profiled as ordinary
 *   environment residues, so SAM-ligand interactions appear with the
 *   SAM residue as {@link Interaction#residue()}.</li>
 *   <li>{@link #profile(Structure, Structure, Structure)} — a separate
 *   fixed-cofactor structure (e.g. SAM) profiled as a second
 *   environment, never as the ligand. Its interactions are detected
 *   against the ligand exactly like receptor-side ones, and the result
 *   carries {@link InteractionProfile#cofactorResidues()} so callers
 *   can tell cofactor-owned records apart.</li>
 *   <li>{@link #profileComplex(Structure, Predicate)} — one merged
 *   structure (receptor + cofactors + ligand, e.g. a complex PDBQT)
 *   plus a ligand-residue selector. The complex is split by residue;
 *   covalent bonds crossing the ligand/environment boundary are dropped
 *   (documented, never silently kept).</li>
 * </ul>
 *
 * <p>Binding-site preselection follows PLIP pass 2 only: an environment
 * residue is kept iff any of its heavy atoms lies within
 * {@code bindingSiteCutoff} (default {@value #DEFAULT_BINDING_SITE_CUTOFF}
 * angstroms, PLIP BS_DIST) of any ligand heavy atom, inclusive. Residue
 * objects (and therefore atom identity) are preserved; bonds are
 * restricted to kept residues. The coarse residue-centroid pass of PLIP
 * is a pure optimization and is not reproduced. The ligand structure is
 * never preselected.
 *
 * <p>When a separate cofactor is given, receptor and cofactor are
 * profiled as two independent environment runs (perception, detection,
 * and refinement each applied per run) and concatenated — receptor
 * records first. Cross-environment precedence effects (e.g. a
 * cofactor-ligand salt bridge suppressing a receptor-ligand H-bond)
 * are deliberately not modeled.
 */
public final class InteractionProfiler {

    /** Default binding-site preselection cutoff (PLIP BS_DIST). */
    public static final double DEFAULT_BINDING_SITE_CUTOFF = 7.5;

    private final InteractionThresholds thresholds;
    private final double bindingSiteCutoff;
    private final HydrophobicAtomPerception hydrophobicPerception =
            new HydrophobicAtomPerception();
    private final AromaticRingPerception ringPerception =
            new AromaticRingPerception();
    private final ChargedGroupPerception chargedGroupPerception =
            new ChargedGroupPerception();
    private final HydrophobicContactDetector hydrophobicContactDetector =
            new HydrophobicContactDetector();
    private final PiStackingDetector piStackingDetector =
            new PiStackingDetector();
    private final PiCationDetector piCationDetector =
            new PiCationDetector();
    private final SaltBridgeDetector saltBridgeDetector =
            new SaltBridgeDetector();
    private final HalogenBondDetector halogenBondDetector =
            new HalogenBondDetector();
    private final HydrogenBondDetector hydrogenBondDetector =
            new HydrogenBondDetector();

    /** Profiler with {@link InteractionThresholds#athenaDefaults()}. */
    public InteractionProfiler() {
        this(InteractionThresholds.athenaDefaults());
    }

    /** Profiler with the given thresholds and the default binding-site cutoff. */
    public InteractionProfiler(InteractionThresholds thresholds) {
        this(thresholds, DEFAULT_BINDING_SITE_CUTOFF);
    }

    public InteractionProfiler(
            InteractionThresholds thresholds,
            double bindingSiteCutoff) {

        this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
        if (!Double.isFinite(bindingSiteCutoff) || bindingSiteCutoff <= 0.0) {
            throw new IllegalArgumentException(
                    "bindingSiteCutoff must be finite and positive");
        }
        this.bindingSiteCutoff = bindingSiteCutoff;
    }

    /**
     * Profiles a ligand against a receptor structure. The receptor may
     * already contain fixed-cofactor residues (merged assembly); they are
     * profiled as environment.
     */
    public InteractionProfile profile(Structure receptor, Structure ligand) {
        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(ligand, "ligand");

        SideResult result = profileSide(
                receptor, ligand, PerceptionSummary.RECEPTOR);
        return new InteractionProfile(
                result.refined(),
                result.raw(),
                Set.of(),
                thresholds,
                List.of(result.summary(),
                        perceiveSide(ligand, PerceptionSummary.LIGAND)));
    }

    /**
     * Profiles a ligand against a receptor plus a separately passed fixed
     * cofactor (e.g. SAM). The cofactor is profiled as a second
     * environment, never as the ligand; its residues are reported in
     * {@link InteractionProfile#cofactorResidues()}.
     */
    public InteractionProfile profile(
            Structure receptor,
            Structure ligand,
            Structure cofactor) {

        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(ligand, "ligand");
        Objects.requireNonNull(cofactor, "cofactor");

        SideResult receptorResult = profileSide(
                receptor, ligand, PerceptionSummary.RECEPTOR);
        SideResult cofactorResult = profileSide(
                cofactor, ligand, PerceptionSummary.COFACTOR);

        List<Interaction> refined = new ArrayList<>(
                receptorResult.refined());
        refined.addAll(cofactorResult.refined());
        List<Interaction> raw = new ArrayList<>(receptorResult.raw());
        raw.addAll(cofactorResult.raw());

        return new InteractionProfile(
                List.copyOf(refined),
                List.copyOf(raw),
                residueIds(cofactor),
                thresholds,
                List.of(receptorResult.summary(),
                        perceiveSide(ligand, PerceptionSummary.LIGAND),
                        cofactorResult.summary()));
    }

    /**
     * Profiles one merged structure (receptor + cofactors + ligand) by
     * splitting out the residues matched by {@code ligandSelector} as the
     * ligand; everything else is environment. Bonds crossing the
     * ligand/environment boundary are dropped. The complex's connectivity
     * provenance is preserved on both parts.
     */
    public InteractionProfile profileComplex(
            Structure complex,
            Predicate<ResidueId> ligandSelector) {

        Objects.requireNonNull(complex, "complex");
        Objects.requireNonNull(ligandSelector, "ligandSelector");

        Structure[] parts = split(complex, ligandSelector);
        if (parts[1].isEmpty()) {
            throw new IllegalArgumentException(
                    "ligandSelector matched no residue of the complex");
        }
        return profile(parts[0], parts[1]);
    }

    private SideResult profileSide(
            Structure environment,
            Structure ligand,
            String sideLabel) {

        Structure site = preselect(environment, ligand);

        HydrophobicAtoms environmentHydrophobic =
                hydrophobicPerception.perceive(site);
        HydrophobicAtoms ligandHydrophobic =
                hydrophobicPerception.perceive(ligand);
        List<AromaticRing> environmentRings = ringPerception.perceive(site);
        List<AromaticRing> ligandRings = ringPerception.perceive(ligand);
        List<ChargedGroup> environmentGroups =
                chargedGroupPerception.perceive(site);
        List<ChargedGroup> ligandGroups =
                chargedGroupPerception.perceive(ligand);

        List<Interaction> saltBridges = saltBridgeDetector.detect(
                environmentGroups, ligandGroups, thresholds);
        List<Interaction> hydrogenBonds = hydrogenBondDetector.detect(
                site, ligand, thresholds);
        List<Interaction> piStacks = piStackingDetector.detect(
                environmentRings, ligandRings, thresholds);
        List<Interaction> piCations = piCationDetector.detect(
                environmentGroups, environmentRings,
                ligandGroups, ligandRings, thresholds);
        List<Interaction> hydrophobicContacts =
                hydrophobicContactDetector.detect(site,
                        environmentHydrophobic, ligandHydrophobic,
                        thresholds);
        List<Interaction> halogenBonds = halogenBondDetector.detect(
                site, ligand, thresholds);

        List<Interaction> raw = new ArrayList<>();
        raw.addAll(saltBridges);
        raw.addAll(hydrogenBonds);
        raw.addAll(piStacks);
        raw.addAll(piCations);
        raw.addAll(hydrophobicContacts);
        raw.addAll(halogenBonds);

        List<Interaction> refined = InteractionRefinements.refineAll(
                saltBridges, hydrogenBonds, piStacks, piCations,
                hydrophobicContacts, halogenBonds, ligand);

        return new SideResult(
                List.copyOf(raw),
                refined,
                summary(sideLabel, environmentHydrophobic,
                        environmentRings, environmentGroups));
    }

    private PerceptionSummary perceiveSide(Structure ligand, String side) {
        HydrophobicAtoms hydrophobic = hydrophobicPerception.perceive(ligand);
        return summary(side, hydrophobic,
                ringPerception.perceive(ligand),
                chargedGroupPerception.perceive(ligand));
    }

    private static PerceptionSummary summary(
            String side,
            HydrophobicAtoms hydrophobic,
            List<AromaticRing> rings,
            List<ChargedGroup> groups) {

        return new PerceptionSummary(
                side,
                hydrophobic.provenance(),
                hydrophobic.atoms().size(),
                rings.size(),
                (int) rings.stream().filter(AromaticRing::degraded).count(),
                groups.size(),
                (int) groups.stream().filter(ChargedGroup::degraded)
                        .count());
    }

    /**
     * PLIP pass-2 preselection: keeps environment residues with any heavy
     * atom within {@code bindingSiteCutoff} of any ligand heavy atom
     * (inclusive). Atom identity is preserved.
     */
    private Structure preselect(Structure environment, Structure ligand) {
        List<Point3D> ligandPoints = heavyPositions(ligand);
        if (ligandPoints.isEmpty()) {
            return environment;
        }
        double cutoffSquared = bindingSiteCutoff * bindingSiteCutoff;

        List<Chain> keptChains = new ArrayList<>();
        Set<ResidueId> keptResidues = new LinkedHashSet<>();
        for (Chain chain : environment.getChains()) {
            List<Residue> kept = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                boolean near = residue.getAtoms().stream()
                        .filter(Atom::isHeavyAtom)
                        .map(Atom::getPosition)
                        .anyMatch(position -> ligandPoints.stream()
                                .anyMatch(ligandPoint ->
                                        position.distanceSquared(ligandPoint)
                                                <= cutoffSquared));
                if (near) {
                    kept.add(residue);
                    keptResidues.add(new ResidueId(chain.id(),
                            residue.getNumber(),
                            residue.getInsertionCode()));
                }
            }
            if (!kept.isEmpty()) {
                keptChains.add(new Chain(chain.id(), kept));
            }
        }
        List<Bond> keptBonds = environment.getBonds().stream()
                .filter(bond -> keptResidues.contains(residueOf(bond.atom1()))
                        && keptResidues.contains(residueOf(bond.atom2())))
                .toList();
        return new Structure(keptChains, keptBonds,
                environment.getConnectivityMetadata().provenance());
    }

    /**
     * Splits a merged complex into [environment, ligand] by residue.
     * Bonds crossing the boundary are dropped.
     */
    private static Structure[] split(
            Structure complex,
            Predicate<ResidueId> ligandSelector) {

        Set<ResidueId> ligandResidues = new LinkedHashSet<>();
        for (Chain chain : complex.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId id = new ResidueId(chain.id(),
                        residue.getNumber(), residue.getInsertionCode());
                if (ligandSelector.test(id)) {
                    ligandResidues.add(id);
                }
            }
        }
        List<Chain> environmentChains = new ArrayList<>();
        List<Chain> ligandChains = new ArrayList<>();
        for (Chain chain : complex.getChains()) {
            List<Residue> environmentResidues = new ArrayList<>();
            List<Residue> ligandResiduesInChain = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                ResidueId id = new ResidueId(chain.id(),
                        residue.getNumber(), residue.getInsertionCode());
                if (ligandResidues.contains(id)) {
                    ligandResiduesInChain.add(residue);
                } else {
                    environmentResidues.add(residue);
                }
            }
            if (!environmentResidues.isEmpty()) {
                environmentChains.add(
                        new Chain(chain.id(), environmentResidues));
            }
            if (!ligandResiduesInChain.isEmpty()) {
                ligandChains.add(
                        new Chain(chain.id(), ligandResiduesInChain));
            }
        }
        List<Bond> environmentBonds = new ArrayList<>();
        List<Bond> ligandBonds = new ArrayList<>();
        for (Bond bond : complex.getBonds()) {
            boolean firstLigand = ligandResidues.contains(
                    residueOf(bond.atom1()));
            boolean secondLigand = ligandResidues.contains(
                    residueOf(bond.atom2()));
            if (firstLigand && secondLigand) {
                ligandBonds.add(bond);
            } else if (!firstLigand && !secondLigand) {
                environmentBonds.add(bond);
            }
            // Boundary-crossing bonds are dropped deliberately.
        }
        return new Structure[] {
                new Structure(environmentChains, environmentBonds,
                        complex.getConnectivityMetadata().provenance()),
                new Structure(ligandChains, ligandBonds,
                        complex.getConnectivityMetadata().provenance())};
    }

    private static Set<ResidueId> residueIds(Structure structure) {
        Set<ResidueId> ids = new LinkedHashSet<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                ids.add(new ResidueId(chain.id(), residue.getNumber(),
                        residue.getInsertionCode()));
            }
        }
        return Set.copyOf(ids);
    }

    private static List<Point3D> heavyPositions(Structure structure) {
        List<Point3D> positions = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                for (Atom atom : residue.getAtoms()) {
                    if (atom.isHeavyAtom()) {
                        positions.add(atom.getPosition());
                    }
                }
            }
        }
        return positions;
    }

    private static ResidueId residueOf(AtomReference reference) {

        return new ResidueId(reference.chainId(), reference.residueNumber(),
                reference.insertionCode() == ' '
                        ? null : reference.insertionCode());
    }

    private record SideResult(
            List<Interaction> raw,
            List<Interaction> refined,
            PerceptionSummary summary) {
    }
}
