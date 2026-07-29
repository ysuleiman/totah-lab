package totah.lab.http.biohub;

import totah.lab.http.biohub.model.ComplexToken;
import totah.lab.http.biohub.model.MolecularComplexPrediction;
import totah.lab.ligand.Ligand;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;

import java.util.List;
import java.util.Objects;

public final class BiohubComplexMapper {

    public Structure proteinStructure(
            MolecularComplexPrediction prediction,
            String chain) {
        Objects.requireNonNull(prediction, "prediction");
        List<Residue> residues = prediction.tokens().stream()
                .filter(token -> token.chain().equals(chain))
                .map(this::toResidue)
                .toList();
        if (residues.isEmpty()) {
            throw new IllegalArgumentException(
                    "Prediction contains no protein chain " + chain
            );
        }
        return new Structure(residues);
    }

    public Ligand ligand(
            MolecularComplexPrediction prediction,
            String chain) {
        Objects.requireNonNull(prediction, "prediction");
        var atoms = prediction.tokens().stream()
                .filter(token -> token.chain().equals(chain))
                .flatMap(token -> token.atoms().stream())
                .map(atomComplex -> atomComplex.atom())
                .toList();
        return new Ligand(prediction.ligandCcd(), atoms);
    }

    private Residue toResidue(ComplexToken token) {
        return new Residue(
                token.residueName(),
                token.chainPosition(),
                token.chain(),
                null,
                token.atoms().stream()
                        .map(atomComplex -> atomComplex.atom())
                        .toList()
        );
    }
}
