package totah.lab.hermes.component;

import org.junit.jupiter.api.Test;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.file.mmcif.BoundComponentAtom;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LigandClassifierTest {

    private final LigandClassifier classifier = new LigandClassifier();

    @Test
    void classifiesSolvent() {
        assertThat(classifier.classify("HOH", List.of()))
                .isEqualTo(LigandClassification.SOLVENT);
        assertThat(classifier.classify("wat", List.of()))
                .isEqualTo(LigandClassification.SOLVENT);
        assertThat(classifier.classify("DOD", List.of()))
                .isEqualTo(LigandClassification.SOLVENT);
    }

    @Test
    void classifiesMetalIons() {
        assertThat(classifier.classify("ZN", List.of()))
                .isEqualTo(LigandClassification.METAL_ION);
        assertThat(classifier.classify("mg", List.of()))
                .isEqualTo(LigandClassification.METAL_ION);
        assertThat(classifier.classify("FE2", List.of()))
                .isEqualTo(LigandClassification.METAL_ION);
    }

    @Test
    void classifiesBufferAdditives() {
        assertThat(classifier.classify("GOL", organicAtoms(6)))
                .isEqualTo(LigandClassification.BUFFER_ADDITIVE);
        assertThat(classifier.classify("SO4", List.of()))
                .isEqualTo(LigandClassification.BUFFER_ADDITIVE);
        assertThat(classifier.classify("TRS", organicAtoms(10)))
                .isEqualTo(LigandClassification.BUFFER_ADDITIVE);
    }

    @Test
    void classifiesCofactorsIncludingSamAndSah() {
        assertThat(classifier.classify("SAM", organicAtoms(27)))
                .isEqualTo(LigandClassification.COFACTOR);
        assertThat(classifier.classify("SAH", organicAtoms(26)))
                .isEqualTo(LigandClassification.COFACTOR);
        assertThat(classifier.classify("sah", organicAtoms(26)))
                .isEqualTo(LigandClassification.COFACTOR);
        assertThat(classifier.classify("FAD", organicAtoms(53)))
                .isEqualTo(LigandClassification.COFACTOR);
        assertThat(classifier.classify("ATP", organicAtoms(31)))
                .isEqualTo(LigandClassification.COFACTOR);
    }

    @Test
    void curatedListsTakePrecedenceOverOrganicFallback() {
        // GOL has carbon and more than 3 atoms, but the buffer list wins.
        assertThat(classifier.classify("GOL", organicAtoms(6)))
                .isEqualTo(LigandClassification.BUFFER_ADDITIVE);
        // SAM likewise stays a cofactor, never a generic organic ligand.
        assertThat(classifier.classify("SAM", organicAtoms(27)))
                .isEqualTo(LigandClassification.COFACTOR);
    }

    @Test
    void keepsModifiedPolymerResiduesOutOfOrganicLigands() {
        assertThat(classifier.classify("MSE", organicAtoms(8)))
                .isEqualTo(LigandClassification.POLYMER_MODIFICATION);
        assertThat(classifier.classify("MLY", organicAtoms(10)))
                .isEqualTo(LigandClassification.POLYMER_MODIFICATION);
        assertThat(classifier.classify("TPO", organicAtoms(11)))
                .isEqualTo(LigandClassification.POLYMER_MODIFICATION);
    }

    @Test
    void recognizesDatasetCofactorsAndAdditivesBeforeOrganicFallback() {
        assertThat(classifier.classify("MTA", organicAtoms(24)))
                .isEqualTo(LigandClassification.COFACTOR);
        assertThat(classifier.classify("UMP", organicAtoms(20)))
                .isEqualTo(LigandClassification.COFACTOR);
        assertThat(classifier.classify("BME", organicAtoms(4)))
                .isEqualTo(LigandClassification.BUFFER_ADDITIVE);
        assertThat(classifier.classify("PG0", organicAtoms(12)))
                .isEqualTo(LigandClassification.BUFFER_ADDITIVE);
    }

    @Test
    void unlistedMultiAtomOrganicFallsBackToOrganicLigand() {
        assertThat(classifier.classify("L99", organicAtoms(12)))
                .isEqualTo(LigandClassification.ORGANIC_LIGAND);
    }

    @Test
    void unlistedWithoutOrganicEvidenceIsUnknown() {
        // Single atom, no carbon.
        assertThat(classifier.classify("UNX", List.of(atom("X", "XE"))))
                .isEqualTo(LigandClassification.UNKNOWN);
        // Carbon but too few atoms to be a real molecule.
        assertThat(classifier.classify("UN1",
                List.of(atom("C1", "C"), atom("O1", "O"))))
                .isEqualTo(LigandClassification.UNKNOWN);
        // No atom evidence at all.
        assertThat(classifier.classify("ZZZ", List.of()))
                .isEqualTo(LigandClassification.UNKNOWN);
    }

    private static List<BoundComponentAtom> organicAtoms(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> atom("C" + index, index % 2 == 0 ? "C" : "N"))
                .toList();
    }

    private static BoundComponentAtom atom(String name, String element) {
        return new BoundComponentAtom(name, name, element,
                new Point3D(0, 0, 0), 1.0, null, null, null, null);
    }
}
