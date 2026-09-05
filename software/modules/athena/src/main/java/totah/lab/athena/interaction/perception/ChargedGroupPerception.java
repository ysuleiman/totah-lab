package totah.lab.athena.interaction.perception;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Perceives positively and negatively charged groups, following PLIP.
 *
 * <p>Protein side (residue-name shortcut, as PLIP does): ARG (NE,NH1,NH2),
 * LYS (NZ) and HIS (ND1,NE2 — pH-dependent, noted on the result) are
 * perceived as positive; ASP (OD1,OD2) and GLU (OE1,OE2) as negative. The
 * charge center is the centroid of the template atoms; a residue missing
 * any template atom is skipped rather than guessed. Provenance:
 * {@link PerceptionProvenance#PROTEIN_TEMPLATE}.</p>
 *
 * <p>Ligand side (any other residue), bond-graph rules over
 * EXPLICIT/INFERRED connectivity:</p>
 * <ul>
 *   <li>carboxylate: carbon bonded to exactly 2 oxygens (negative),</li>
 *   <li>guanidinium: carbon bonded to exactly 3 nitrogens (positive),</li>
 *   <li>amine: nitrogen with bond degree 4 — quaternary or protonated
 *       (positive),</li>
 *   <li>sulfonium: sulfur bonded to exactly 3 carbons (positive — SAM has
 *       one).</li>
 * </ul>
 *
 * <p>gaia's {@link Atom} carries no per-atom formal charge, so the
 * PLIP formal-charge disjuncts (and phosphate/sulfate detection, which
 * needs per-atom formal charges on the oxygens) are not implemented; the
 * bond-degree rules above cover the protonated states. Provenance:
 * {@link PerceptionProvenance#BOND_GRAPH}.</p>
 *
 * <p>When connectivity is PARTIAL/ABSENT (or no bonds exist for the
 * residue), perception degrades to the legacy heuristic of
 * DefaultLigandInteractionAnalyzer: if the per-residue partial-charge sum
 * is at least 0.5 e in magnitude, a single pseudo-group of type
 * {@link ChargedGroupType#CHARGE_SUM} is emitted with provenance
 * {@link PerceptionProvenance#CHARGE_SUM_FALLBACK}.</p>
 *
 * <p>Output order is deterministic: structure traversal order.</p>
 */
public final class ChargedGroupPerception {

    /** Minimum |partial-charge sum| for the degraded charge-sum fallback. */
    public static final double CHARGE_SUM_THRESHOLD = 0.5;

    /** Protein templates: residue name → (type, sign, atom names). */
    private static final Map<String, Template> PROTEIN_TEMPLATES = Map.of(
            "ARG", new Template(ChargedGroupType.RESIDUE_ARG,
                    ChargeSign.POSITIVE, List.of("NE", "NH1", "NH2"),
                    "standard ARG guanidinium side-chain template"),
            "LYS", new Template(ChargedGroupType.RESIDUE_LYS,
                    ChargeSign.POSITIVE, List.of("NZ"),
                    "standard LYS ammonium side-chain template"),
            "HIS", new Template(ChargedGroupType.RESIDUE_HIS,
                    ChargeSign.POSITIVE, List.of("ND1", "NE2"),
                    "standard HIS imidazole side-chain template; "
                            + "pH-dependent, protonated state assumed"),
            "ASP", new Template(ChargedGroupType.RESIDUE_ASP,
                    ChargeSign.NEGATIVE, List.of("OD1", "OD2"),
                    "standard ASP carboxylate side-chain template"),
            "GLU", new Template(ChargedGroupType.RESIDUE_GLU,
                    ChargeSign.NEGATIVE, List.of("OE1", "OE2"),
                    "standard GLU carboxylate side-chain template"));

    /**
     * Perceives the charged groups of a structure.
     *
     * @param structure structure to inspect
     * @return perceived charged groups in deterministic order
     */
    public List<ChargedGroup> perceive(Structure structure) {
        Objects.requireNonNull(structure, "structure");

        boolean connectivityUsable = switch (
                structure.getConnectivityMetadata().provenance()) {
            case EXPLICIT, INFERRED -> true;
            case PARTIAL, ABSENT -> false;
        };
        Map<AtomReference, Set<AtomReference>> neighbors =
                neighbors(structure);

        List<ChargedGroup> groups = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId owner = new ResidueId(
                        chain.id(),
                        residue.getNumber(),
                        residue.getInsertionCode());
                Template template = PROTEIN_TEMPLATES.get(residue.getName());
                if (template != null) {
                    perceiveTemplate(residue, owner, template, groups);
                } else if (connectivityUsable) {
                    perceiveFromBonds(chain.id(), residue, owner,
                            neighbors, groups);
                } else {
                    perceiveChargeSumFallback(residue, owner,
                            structure.getConnectivityMetadata().provenance(),
                            groups);
                }
            }
        }
        return List.copyOf(groups);
    }

    private void perceiveTemplate(
            Residue residue,
            ResidueId owner,
            Template template,
            List<ChargedGroup> groups) {

        List<Atom> atoms = new ArrayList<>(template.atomNames().size());
        for (String atomName : template.atomNames()) {
            var atom = residue.findAtom(atomName);
            if (atom.isEmpty()) {
                return; // incomplete template: skipped, never guessed
            }
            atoms.add(atom.get());
        }
        groups.add(new ChargedGroup(
                template.sign(),
                template.type(),
                owner,
                atoms,
                centroid(atoms),
                PerceptionProvenance.PROTEIN_TEMPLATE,
                template.note()));
    }

    private void perceiveFromBonds(
            String chainId,
            Residue residue,
            ResidueId owner,
            Map<AtomReference, Set<AtomReference>> neighbors,
            List<ChargedGroup> groups) {

        Map<AtomReference, Atom> atomsByReference =
                residueAtomIndex(chainId, residue);

        for (Map.Entry<AtomReference, Atom> entry
                : atomsByReference.entrySet()) {
            AtomReference reference = entry.getKey();
            Atom atom = entry.getValue();
            Set<AtomReference> atomNeighbors =
                    neighbors.getOrDefault(reference, Set.of());

            List<Atom> oxygenNeighbors = heavyNeighborsOf(
                    atomNeighbors, atomsByReference, Element.O);
            List<Atom> nitrogenNeighbors = heavyNeighborsOf(
                    atomNeighbors, atomsByReference, Element.N);
            List<Atom> carbonNeighbors = heavyNeighborsOf(
                    atomNeighbors, atomsByReference, Element.C);

            if (atom.getElement() == Element.C
                    && oxygenNeighbors.size() == 2) {
                List<Atom> groupAtoms = new ArrayList<>();
                groupAtoms.add(atom);
                groupAtoms.addAll(oxygenNeighbors);
                groups.add(new ChargedGroup(
                        ChargeSign.NEGATIVE,
                        ChargedGroupType.CARBOXYLATE,
                        owner,
                        groupAtoms,
                        centroid(groupAtoms),
                        PerceptionProvenance.BOND_GRAPH,
                        "carbon bonded to exactly 2 oxygens"));
            } else if (atom.getElement() == Element.C
                    && nitrogenNeighbors.size() == 3) {
                List<Atom> groupAtoms = new ArrayList<>();
                groupAtoms.add(atom);
                groupAtoms.addAll(nitrogenNeighbors);
                groups.add(new ChargedGroup(
                        ChargeSign.POSITIVE,
                        ChargedGroupType.GUANIDINIUM,
                        owner,
                        groupAtoms,
                        centroid(groupAtoms),
                        PerceptionProvenance.BOND_GRAPH,
                        "carbon bonded to exactly 3 nitrogens"));
            } else if (atom.getElement() == Element.N
                    && atomNeighbors.size() == 4) {
                List<Atom> groupAtoms = new ArrayList<>();
                groupAtoms.add(atom);
                groupAtoms.addAll(heavyNeighbors(
                        atomNeighbors, atomsByReference));
                groups.add(new ChargedGroup(
                        ChargeSign.POSITIVE,
                        ChargedGroupType.AMINE,
                        owner,
                        groupAtoms,
                        centroid(groupAtoms),
                        PerceptionProvenance.BOND_GRAPH,
                        "nitrogen with bond degree 4 (quaternary or "
                                + "protonated amine)"));
            } else if (atom.getElement() == Element.S
                    && carbonNeighbors.size() == 3) {
                List<Atom> groupAtoms = new ArrayList<>();
                groupAtoms.add(atom);
                groupAtoms.addAll(carbonNeighbors);
                groups.add(new ChargedGroup(
                        ChargeSign.POSITIVE,
                        ChargedGroupType.SULFONIUM,
                        owner,
                        groupAtoms,
                        centroid(groupAtoms),
                        PerceptionProvenance.BOND_GRAPH,
                        "sulfur bonded to exactly 3 carbons"));
            }
        }
    }

    private void perceiveChargeSumFallback(
            Residue residue,
            ResidueId owner,
            ConnectivityProvenance connectivity,
            List<ChargedGroup> groups) {

        double chargeSum = residue.getAtoms().stream()
                .mapToDouble(Atom::getCharge)
                .sum();
        if (Math.abs(chargeSum) < CHARGE_SUM_THRESHOLD) {
            return;
        }
        List<Atom> atoms = residue.getAtoms().stream()
                .filter(Atom::isHeavyAtom)
                .toList();
        if (atoms.isEmpty()) {
            return;
        }
        groups.add(new ChargedGroup(
                chargeSum > 0.0 ? ChargeSign.POSITIVE : ChargeSign.NEGATIVE,
                ChargedGroupType.CHARGE_SUM,
                owner,
                atoms,
                centroid(atoms),
                PerceptionProvenance.CHARGE_SUM_FALLBACK,
                "connectivity " + connectivity
                        + "; degraded to per-residue partial-charge sum "
                        + String.format("%.3f", chargeSum) + " e"));
    }

    private static List<Atom> heavyNeighborsOf(
            Set<AtomReference> neighbors,
            Map<AtomReference, Atom> atomsByReference,
            Element element) {

        List<Atom> result = new ArrayList<>();
        for (AtomReference reference : neighbors) {
            Atom neighbor = atomsByReference.get(reference);
            if (neighbor != null && neighbor.getElement() == element) {
                result.add(neighbor);
            }
        }
        return result;
    }

    private static List<Atom> heavyNeighbors(
            Set<AtomReference> neighbors,
            Map<AtomReference, Atom> atomsByReference) {

        List<Atom> result = new ArrayList<>();
        for (AtomReference reference : neighbors) {
            Atom neighbor = atomsByReference.get(reference);
            if (neighbor != null && neighbor.isHeavyAtom()) {
                result.add(neighbor);
            }
        }
        return result;
    }

    private static Map<AtomReference, Atom> residueAtomIndex(
            String chainId,
            Residue residue) {

        char insertionCode = residue.getInsertionCode() == null
                ? ' '
                : residue.getInsertionCode();
        Map<AtomReference, Atom> index = new LinkedHashMap<>();
        for (Atom atom : residue.getAtoms()) {
            index.put(
                    new AtomReference(
                            chainId,
                            residue.getNumber(),
                            insertionCode,
                            atom.getName()),
                    atom);
        }
        return index;
    }

    private static Map<AtomReference, Set<AtomReference>> neighbors(
            Structure structure) {

        Map<AtomReference, Set<AtomReference>> neighbors = new HashMap<>();
        for (Bond bond : structure.bonds()) {
            neighbors.computeIfAbsent(bond.atom1(), key -> new HashSet<>())
                    .add(bond.atom2());
            neighbors.computeIfAbsent(bond.atom2(), key -> new HashSet<>())
                    .add(bond.atom1());
        }
        return neighbors;
    }

    private static Point3D centroid(List<Atom> atoms) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Atom atom : atoms) {
            x += atom.getPosition().x();
            y += atom.getPosition().y();
            z += atom.getPosition().z();
        }
        return new Point3D(
                x / atoms.size(), y / atoms.size(), z / atoms.size());
    }

    private record Template(
            ChargedGroupType type,
            ChargeSign sign,
            List<String> atomNames,
            String note) {
    }
}
