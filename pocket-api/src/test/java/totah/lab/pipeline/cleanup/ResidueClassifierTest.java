package totah.lab.pipeline.cleanup;

import org.junit.jupiter.api.Test;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.ResidueClassificationEvidence;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResidueClassifierTest {

    private final ResidueClassifier classifier = new ResidueClassifier();

    @Test
    void classifiesRepresentativeComponentPanel() {
        List<ReferenceCase> panel = List.of(
                new ReferenceCase(residue("ALA", atom("CA", "C")),
                        ResidueKind.STANDARD_AMINO_ACID),
                new ReferenceCase(residue("HOH", atom("O", "O")),
                        ResidueKind.WATER),
                new ReferenceCase(residue("ZN", atom("ZN", "Zn")),
                        ResidueKind.ION_OR_METAL),
                new ReferenceCase(withEvidence(
                        residue("TYS", atom("S", "S")),
                        true, false, true, false, "TYR", "lPeptideLinking", "peptide"),
                        ResidueKind.MODIFIED_AMINO_ACID),
                new ReferenceCase(withEvidence(
                        residue("QWE", atom("C1", "C"), atom("N1", "N")),
                        true, false, false, false, null, "nonPolymer", null),
                        ResidueKind.NON_POLYMER),
                new ReferenceCase(residue("UNK", atom("C1", "C"), atom("N1", "N")),
                        ResidueKind.UNKNOWN));

        for (ReferenceCase reference : panel) {
            assertEquals(
                    reference.expected(),
                    classifier.classify(reference.residue()),
                    reference.residue().getName());
        }
    }

    @Test
    void fallsBackToStableNamesWhenCcdEvidenceIsUnavailable() {
        Residue residue = withEvidence(
                residue("CYS", atom("CA", "C")),
                false, false, false, false, null, null, null);

        assertEquals(ResidueKind.STANDARD_AMINO_ACID, classifier.classify(residue));
    }

    private Residue withEvidence(
            Residue residue,
            boolean available,
            boolean standard,
            boolean polymeric,
            boolean water,
            String parentComponentId,
            String residueType,
            String polymerType) {
        return residue.toBuilder()
                .residueClassificationEvidence(new ResidueClassificationEvidence(
                        available,
                        standard,
                        polymeric,
                        water,
                        parentComponentId,
                        residueType,
                        polymerType))
                .build();
    }

    private Residue residue(String name, Atom... atoms) {
        return Residue.builder()
                .name(name)
                .chain("A")
                .number(1)
                .insertionCode(' ')
                .atoms(List.of(atoms))
                .build();
    }

    private Atom atom(String name, String element) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(0.0, 0.0, 0.0))
                .occupancy(1.0)
                .bFactor(20.0)
                .charge(0.0)
                .element(Element.builder()
                        .symbol(element)
                        .atomicNumber(0)
                        .atomicMass(0.0)
                        .covalentRadius(0.0)
                        .vdwRadius(0.0)
                        .build())
                .build();
    }

    private record ReferenceCase(Residue residue, ResidueKind expected) {
    }
}
