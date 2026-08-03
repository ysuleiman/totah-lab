package totah.lab.hephaestus.receptor.hydrogen;

import lombok.extern.slf4j.Slf4j;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.receptor.disulfide.DisulfideDetector;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Adds hydrogens to all residues in one receptor chain.
 *
 * <p>Hydrogen optimization is intentionally handled by a separate
 * {@code HydrogenOptimizationOperation}.</p>
 */
@Slf4j
public final class ReceptorHydrogenator {

    private final HydrogenPositionCalculator positionCalculator;
    private final HydrogenAtomFactory atomFactory;

    public ReceptorHydrogenator() {
        this(
                new HydrogenPositionCalculator(),
                new HydrogenAtomFactory());
    }

    public ReceptorHydrogenator(
            HydrogenPositionCalculator positionCalculator,
            HydrogenAtomFactory atomFactory) {

        this.positionCalculator = Objects.requireNonNull(
                positionCalculator,
                "positionCalculator");

        this.atomFactory = Objects.requireNonNull(
                atomFactory,
                "atomFactory");
    }

    public List<Residue> hydrogenate(
            Chain chain,
            ProtonationConfig config,
            Map<String, String> amberTemplates) {

        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(config, "config");

        return hydrogenate(
                chain,
                config,
                amberTemplates,
                detectDisulfideResidueKeys(
                        chain.id(),
                        chain.residues(),
                        config));
    }

    /**
     * Hydrogenates one chain of a structure, detecting disulfide bonds
     * across the whole structure so a cysteine whose disulfide partner is in
     * another chain is not protonated on SG.
     *
     * <p>Only the residues of the named chain are hydrogenated; the other
     * chains participate solely in disulfide detection.</p>
     */
    public List<Residue> hydrogenate(
            Structure structure,
            String chainId,
            ProtonationConfig config,
            Map<String, String> amberTemplates) {

        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(chainId, "chainId");

        Chain chain = structure.findChain(chainId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Chain '" + chainId + "' not found in structure"));

        return hydrogenate(
                chain,
                config,
                amberTemplates,
                detectDisulfideResidueKeys(structure, config));
    }

    /**
     * Hydrogenates one chain of a structure without residue-state templates.
     *
     * <p>Unlike {@link #hydrogenate(Chain, ProtonationConfig)}, disulfide
     * detection here sees the whole structure, so cross-chain disulfides are
     * respected.</p>
     */
    public List<Residue> hydrogenate(
            Structure structure,
            String chainId,
            ProtonationConfig config) {

        return hydrogenate(
                structure,
                chainId,
                config,
                Map.of());
    }

    private List<Residue> hydrogenate(
            Chain chain,
            ProtonationConfig config,
            Map<String, String> amberTemplates,
            Set<String> disulfideResidueKeys) {

        Map<String, String> templates =
                amberTemplates == null
                        ? Map.of()
                        : Map.copyOf(amberTemplates);

        List<Residue> residues =
                stripExistingHydrogens(chain.residues());

        SpatialClashChecker clashChecker =
                buildClashChecker(
                        residues,
                        config.voxelGridSize());

        List<Atom> metalAtoms =
                findMetalAtoms(residues);

        HydrogenationContext context =
                new HydrogenationContext(
                        config,
                        chain.id(),
                        residues,
                        clashChecker,
                        positionCalculator,
                        atomFactory,
                        disulfideResidueKeys,
                        metalAtoms,
                        templates);

        log.info(
                "Hydrogenating chain {} with {} residues at pH {}",
                chain.id(),
                residues.size(),
                config.ph());

        log.info(
                "Found {} metal atom(s); coordination guard cutoff is {} Å",
                metalAtoms.size(),
                config.metalCoordinationCutoff());

        log.info(
                "Detected {} disulfide bond(s)",
                disulfideResidueKeys.size() / 2);

        List<Residue> protonated =
                new ArrayList<>(residues.size());

        for (int index = 0; index < residues.size(); index++) {
            Residue residue = residues.get(index);

            List<Atom> atoms =
                    new ArrayList<>(residue.getAtoms());

            ResidueHydrogenator.hydrogenateBackbone(
                    chain.id(),
                    residue,
                    index,
                    atoms,
                    context);

            ResidueHydrogenator.hydrogenateSideChain(
                    chain.id(),
                    residue,
                    atoms,
                    context);

            protonated.add(
                    residue.toBuilder()
                            .atoms(atoms)
                            .build());
        }

        return List.copyOf(protonated);
    }

    /**
     * Hydrogenates one chain without residue-state templates.
     *
     * <p>Limitation: disulfide detection here only sees this chain, so a
     * cysteine disulfide-bonded to a different chain is protonated on SG.
     * Use {@link #hydrogenate(Structure, String, ProtonationConfig)} for
     * structure-wide disulfide detection. The default pipeline is unaffected:
     * it runs {@code ResidueStateAssignmentOperation} first, which detects
     * disulfides across the whole structure and passes CYX templates via
     * {@link #hydrogenate(Chain, ProtonationConfig, Map)}. Callers needing
     * cross-chain disulfide handling should supply those templates or use
     * the preparation pipeline.</p>
     */
    public List<Residue> hydrogenate(
            Chain chain,
            ProtonationConfig config) {

        return hydrogenate(
                chain,
                config,
                Map.of());
    }

    private List<Residue> stripExistingHydrogens(
            List<Residue> incoming) {

        Objects.requireNonNull(incoming, "incoming");

        List<Residue> strippedResidues =
                new ArrayList<>(incoming.size());

        int strippedHydrogenCount = 0;

        for (Residue residue : incoming) {
            Objects.requireNonNull(
                    residue,
                    "incoming must not contain null residues");

            List<Atom> heavyAtoms =
                    new ArrayList<>(residue.getAtomCount());

            for (Atom atom : residue.getAtoms()) {
                if (atom == null) {
                    continue;
                }

                if (atom.getElement() == Element.H) {
                    strippedHydrogenCount++;
                } else {
                    heavyAtoms.add(atom);
                }
            }

            strippedResidues.add(
                    residue.toBuilder()
                            .atoms(heavyAtoms)
                            .build());
        }

        log.info(
                "Stripped {} existing hydrogen atom(s)",
                strippedHydrogenCount);

        return List.copyOf(strippedResidues);
    }

    private SpatialClashChecker buildClashChecker(
            List<Residue> residues,
            double voxelGridSize) {

        SpatialClashChecker checker =
                new SpatialClashChecker(voxelGridSize);

        for (Residue residue : residues) {
            checker.addAll(residue.getAtoms());
        }

        return checker;
    }

    private List<Atom> findMetalAtoms(
            List<Residue> residues) {

        List<Atom> metals = new ArrayList<>();

        for (Residue residue : residues) {
            for (Atom atom : residue.getAtoms()) {
                if (atom == null || atom.getElement() == null) {
                    continue;
                }

                String symbol =
                        atom.getElement()
                                .symbol()
                                .toUpperCase(Locale.ROOT);

                if (ProtonationConfig.METAL_ELEMENTS.contains(symbol)) {
                    metals.add(atom);
                }
            }
        }

        return List.copyOf(metals);
    }

    private Set<String> detectDisulfideResidueKeys(
            String chainId,
            List<Residue> residues,
            ProtonationConfig config) {

        if (!config.detectDisulfides()) {
            return Set.of();
        }

        Set<Residue> detectedResidues =
                DisulfideDetector.findDisulfideBonds(
                        residues,
                        config.disulfideCutoff());

        Set<String> keys = new LinkedHashSet<>();

        for (Residue residue : detectedResidues) {
            keys.add(residueKey(chainId, residue));
        }

        return Set.copyOf(keys);
    }

    private Set<String> detectDisulfideResidueKeys(
            Structure structure,
            ProtonationConfig config) {

        if (!config.detectDisulfides()) {
            return Set.of();
        }

        List<Residue> allResidues = new ArrayList<>();
        Map<Residue, String> residueChains = new IdentityHashMap<>();

        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                allResidues.add(residue);
                residueChains.put(residue, chain.id());
            }
        }

        Set<Residue> detectedResidues =
                DisulfideDetector.findDisulfideBonds(
                        allResidues,
                        config.disulfideCutoff());

        Set<String> keys = new LinkedHashSet<>();

        for (Residue residue : detectedResidues) {
            keys.add(residueKey(residueChains.get(residue), residue));
        }

        return Set.copyOf(keys);
    }

    private String residueKey(
            String chainId,
            Residue residue) {

        Character insertionCode =
                residue.getInsertionCode();

        return chainId
                + ":"
                + residue.getNumber()
                + (insertionCode == null
                || Character.isWhitespace(insertionCode)
                ? ""
                : insertionCode);
    }
}
