package totah.lab.hermes.biohub;

import totah.lab.http.biohub.model.ComplexToken;
import totah.lab.http.biohub.model.MolecularComplexPrediction;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

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
        return new Structure(List.of(new Chain(chain, residues)));
    }

    public Ligand ligand(
            MolecularComplexPrediction prediction,
            String chain) {
        Objects.requireNonNull(prediction, "prediction");
        List<Residue> residues = prediction.tokens().stream()
                .filter(token -> token.chain().equals(chain))
                .map(this::toResidue)
                .toList();
        if (residues.isEmpty()) {
            throw new IllegalArgumentException(
                    "Prediction contains no ligand chain " + chain);
        }
        String component = prediction.ligandCcd();
        return new Ligand(
                component,
                component,
                component,
                null,
                null,
                null,
                new Structure(List.of(new Chain(chain, residues))));
    }

    private Residue toResidue(ComplexToken token) {
        return new Residue(
                token.residueName(),
                token.chainPosition(),
                null,
                token.atoms().stream()
                        .map(atomComplex -> atomComplex.atom())
                        .toList()
        );
    }
}
