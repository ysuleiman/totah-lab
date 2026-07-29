package totah.lab.protein.analysis;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LigandPocketCalculatorTest {

    @Test
    void derivesProteinResiduesWithinLigandAtomCutoff() {
        MolecularComplexPrediction prediction =
                new MolecularComplexPrediction(
                        "BIOHUB_ESMFOLD2",
                        "model",
                        "SAM",
                        Instant.parse("2026-07-29T22:00:00Z"),
                        0.8,
                        0.7,
                        List.of(
                                token(0, "A", 1, "MET", 0.0),
                                token(1, "A", 2, "GLY", 8.0),
                                token(2, "L", 1, "SAM", 3.0)
                        )
                );

        LigandDefinedPocket pocket = new LigandPocketCalculator().calculate(
                prediction,
                "A",
                "L",
                4.0
        );

        assertEquals(1, pocket.residues().size());
        assertEquals(1, pocket.residues().getFirst().residueNumber());
        assertEquals(3.0, pocket.residues().getFirst().minimumDistance());
    }

    private ComplexToken token(
            int index,
            String chain,
            int position,
            String residue,
            double x
    ) {
        return new ComplexToken(
                index,
                chain,
                position,
                residue,
                0.9,
                List.of(new ComplexAtom("CA", "C", false, x, 0.0, 0.0))
        );
    }
}
