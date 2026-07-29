package totah.lab.protein.analysis;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class LigandPocketCalculator {

    public LigandDefinedPocket calculate(
            MolecularComplexPrediction prediction,
            String proteinChain,
            String ligandChain,
            double cutoff
    ) {
        Objects.requireNonNull(prediction, "prediction");
        if (!Double.isFinite(cutoff) || cutoff <= 0.0) {
            throw new IllegalArgumentException("cutoff must be positive");
        }
        List<ComplexAtom> ligandAtoms = prediction.tokens().stream()
                .filter(token -> token.chain().equals(ligandChain))
                .filter(token -> token.residueName().equals(
                        prediction.ligandCcd()
                ))
                .flatMap(token -> token.atoms().stream())
                .toList();
        if (ligandAtoms.isEmpty()) {
            throw new IllegalArgumentException(
                    "Prediction contains no requested ligand chain"
            );
        }

        List<LigandPocketResidue> residues = prediction.tokens().stream()
                .filter(token -> token.chain().equals(proteinChain))
                .map(token -> contact(token, ligandAtoms, cutoff))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(
                        LigandPocketResidue::residueNumber
                ))
                .toList();
        return new LigandDefinedPocket(
                proteinChain,
                ligandChain,
                prediction.ligandCcd(),
                cutoff,
                residues
        );
    }

    private LigandPocketResidue contact(
            ComplexToken residue,
            List<ComplexAtom> ligandAtoms,
            double cutoff
    ) {
        double minimumDistance = Double.POSITIVE_INFINITY;
        int contactCount = 0;
        for (ComplexAtom residueAtom : residue.atoms()) {
            for (ComplexAtom ligandAtom : ligandAtoms) {
                double distance = distance(residueAtom, ligandAtom);
                minimumDistance = Math.min(minimumDistance, distance);
                if (distance <= cutoff) {
                    contactCount++;
                }
            }
        }
        if (contactCount == 0) {
            return null;
        }
        return new LigandPocketResidue(
                residue.index(),
                residue.chain(),
                residue.chainPosition(),
                residue.residueName(),
                minimumDistance,
                contactCount
        );
    }

    private double distance(ComplexAtom first, ComplexAtom second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
