package totah.lab.athena.pocket.compare.residue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResidueChemistryClassifierTest {

    private final ResidueChemistryClassifier classifier =
            new ResidueChemistryClassifier();

    @ParameterizedTest
    @CsvSource({
            "CYS,CYSTEINE", "GLY,GLYCINE",
            "PHE,AROMATIC", "TYR,AROMATIC", "TRP,AROMATIC",
            "ALA,HYDROPHOBIC", "VAL,HYDROPHOBIC",
            "LEU,HYDROPHOBIC", "ILE,HYDROPHOBIC",
            "MET,HYDROPHOBIC", "PRO,HYDROPHOBIC",
            "SER,POLAR", "THR,POLAR", "ASN,POLAR", "GLN,POLAR",
            "LYS,POSITIVE", "ARG,POSITIVE", "HIS,POSITIVE",
            "ASP,NEGATIVE", "GLU,NEGATIVE", "UNK,OTHER"
    })
    void preservesAthenaClassificationWhileUsingGaiaCategories(
            String residueName,
            ResidueChemistry expected
    ) {
        assertEquals(expected, classifier.classifyName(residueName));
    }
}
