package totah.lab.hephaestus.receptor.assembly;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedLigand;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Posed coordinates paired with the exact prepared ligand chemistry from
 * which they were produced.
 */
public record LigandPose(
        String id,
        PreparedLigand preparedLigand,
        Ligand posedLigand,
        Map<String, String> provenance) {

    public LigandPose {
        id = requireNonBlank(id, "id");
        Objects.requireNonNull(preparedLigand, "preparedLigand");
        Objects.requireNonNull(posedLigand, "posedLigand");
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
        requireSameAtomLayout(
                preparedLigand.ligand(),
                posedLigand);
    }

    /** Returns prepared chemistry with the validated pose coordinates. */
    public PreparedLigand preparedPose() {
        List<Point3D> positions = posedLigand.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .map(Atom::getPosition)
                .toList();
        return preparedLigand.withLigand(positionedLigand(
                preparedLigand.ligand(), positions));
    }

    /** Creates a pose by applying ordered coordinates to prepared atoms. */
    public static LigandPose fromCoordinates(
            String id,
            PreparedLigand preparedLigand,
            List<Point3D> positions,
            Map<String, String> provenance) {

        Objects.requireNonNull(preparedLigand, "preparedLigand");
        return new LigandPose(
                id,
                preparedLigand,
                positionedLigand(preparedLigand.ligand(), positions),
                provenance);
    }

    private static Ligand positionedLigand(
            Ligand prepared,
            List<Point3D> positions) {

        Objects.requireNonNull(positions, "positions");
        int expectedAtomCount = prepared.structure().getAtomCount();
        if (positions.size() != expectedAtomCount) {
            throw new IllegalArgumentException(
                    "Pose coordinate count " + positions.size()
                            + " does not match prepared atom count "
                            + expectedAtomCount);
        }

        List<Chain> positionedChains = new java.util.ArrayList<>(
                prepared.structure().getChainCount());
        int positionIndex = 0;

        for (int chainIndex = 0;
                chainIndex < prepared.structure().getChainCount();
                chainIndex++) {
            Chain preparedChain = prepared.structure().getChains()
                    .get(chainIndex);
            List<Residue> positionedResidues = new java.util.ArrayList<>(
                    preparedChain.residueCount());

            for (int residueIndex = 0;
                    residueIndex < preparedChain.residueCount();
                    residueIndex++) {
                Residue preparedResidue = preparedChain.residues()
                        .get(residueIndex);
                List<Atom> positionedAtoms = new java.util.ArrayList<>(
                        preparedResidue.getAtomCount());

                for (int atomIndex = 0;
                        atomIndex < preparedResidue.getAtomCount();
                        atomIndex++) {
                    Atom preparedAtom = preparedResidue.getAtoms()
                            .get(atomIndex);
                    positionedAtoms.add(preparedAtom.toBuilder()
                            .position(Objects.requireNonNull(
                                    positions.get(positionIndex++),
                                    "positions must not contain null"))
                            .build());
                }
                positionedResidues.add(preparedResidue.toBuilder()
                        .atoms(positionedAtoms)
                        .build());
            }
            positionedChains.add(new Chain(
                    preparedChain.id(),
                    positionedResidues));
        }

        Structure preparedStructure = prepared.structure();
        Structure positionedStructure = new Structure(
                positionedChains,
                preparedStructure.bonds(),
                preparedStructure.getConnectivityMetadata());
        Ligand positionedLigand = new Ligand(
                prepared.id(),
                prepared.name(),
                prepared.componentCode().orElse(null),
                prepared.smiles().orElse(null),
                prepared.inchiKey().orElse(null),
                prepared.formalCharge(),
                positionedStructure);
        return positionedLigand;
    }

    private static void requireSameAtomLayout(
            Ligand prepared,
            Ligand posed) {

        List<Chain> preparedChains = prepared.structure().getChains();
        List<Chain> posedChains = posed.structure().getChains();
        if (preparedChains.size() != posedChains.size()) {
            throw incompatible("chain count");
        }

        for (int chainIndex = 0;
                chainIndex < preparedChains.size();
                chainIndex++) {
            Chain preparedChain = preparedChains.get(chainIndex);
            Chain posedChain = posedChains.get(chainIndex);
            if (!preparedChain.id().equals(posedChain.id())) {
                throw incompatible("chain order or identity");
            }
            requireSameResidueLayout(
                    preparedChain.residues(),
                    posedChain.residues());
        }
    }

    private static void requireSameResidueLayout(
            List<Residue> prepared,
            List<Residue> posed) {

        if (prepared.size() != posed.size()) {
            throw incompatible("residue count");
        }
        for (int residueIndex = 0;
                residueIndex < prepared.size();
                residueIndex++) {
            Residue preparedResidue = prepared.get(residueIndex);
            Residue posedResidue = posed.get(residueIndex);
            if (!preparedResidue.getName().equals(posedResidue.getName())
                    || preparedResidue.getNumber()
                    != posedResidue.getNumber()
                    || !Objects.equals(
                    preparedResidue.getInsertionCode(),
                    posedResidue.getInsertionCode())) {
                throw incompatible("residue order or identity");
            }
            requireSameAtoms(
                    preparedResidue.getAtoms(),
                    posedResidue.getAtoms());
        }
    }

    private static void requireSameAtoms(
            List<Atom> prepared,
            List<Atom> posed) {

        if (prepared.size() != posed.size()) {
            throw incompatible("atom count");
        }
        for (int atomIndex = 0; atomIndex < prepared.size(); atomIndex++) {
            Atom preparedAtom = prepared.get(atomIndex);
            Atom posedAtom = posed.get(atomIndex);
            if (!preparedAtom.getName().equals(posedAtom.getName())
                    || preparedAtom.getElement() != posedAtom.getElement()) {
                throw incompatible("atom order or identity");
            }
        }
    }

    private static IllegalArgumentException incompatible(String detail) {
        return new IllegalArgumentException(
                "Posed ligand does not preserve prepared " + detail);
    }

    private static String requireNonBlank(
            String value,
            String fieldName) {

        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }
        return normalized;
    }
}
