package totah.lab.athena.pocket.evidence;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.geometry.Point3D;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PocketEvidenceModelTest {

    private static final EvidenceMethod FPOCKET =
            new EvidenceMethod("fpocket", "4.2.2");
    private static final EvidenceMethod DERIVATION =
            new EvidenceMethod("pocket-evidence", "1", Map.of("contactCutoff", "4.0"));

    @Test
    void buildsEvidenceWithoutAssessmentOrMasterScore() {
        PocketEvidence evidence = new PocketEvidence(
                experimentalStructure(), pocketGeometry(), residueContext(),
                EvidenceChannel.empty(
                        EvidenceOrigin.SOURCE_OBSERVED, DERIVATION),
                EvidenceChannel.empty(
                        EvidenceOrigin.SOURCE_REPORTED, DERIVATION),
                provenance());

        assertEquals(EvaluationStatus.EMPTY, evidence.ligandEvidence().status());
        assertEquals("pocket1", evidence.pocket().fpocketId());
        assertEquals("1ABC", evidence.structure().accession());
    }

    @Test
    void rejectsIncorrectOriginsForReportedAndDerivedChannels() {
        assertThrows(IllegalArgumentException.class, () -> new PocketGeometryEvidence(
                "pocket1",
                EvidenceChannel.present(1, EvidenceOrigin.DERIVED, FPOCKET),
                EvidenceChannel.notEvaluated("not parsed"),
                EvidenceChannel.notEvaluated("not parsed"),
                EvidenceChannel.notEvaluated("not parsed"),
                EvidenceChannel.notEvaluated("not parsed"),
                EvidenceChannel.notEvaluated("not computed"),
                EvidenceChannel.notEvaluated("not computed")));
    }

    @Test
    void keepsIdealCoordinatesDistinctAndValidatesCcdTopology() {
        ComponentAtomDefinition carbon =
                new ComponentAtomDefinition("C1", "C", 0, false);
        assertThrows(IllegalArgumentException.class,
                () -> new ComponentChemistryEvidence(
                        "SAM", "2026-08-08", List.of(carbon),
                        List.of(new ComponentBondDefinition(
                                "C1", "N1", BondOrder.SINGLE, false)),
                        List.of(), unavailable(), unavailable(), unavailable()));

        ComponentChemistryEvidence chemistry = new ComponentChemistryEvidence(
                "SAM", "2026-08-08", List.of(carbon), List.of(),
                List.of(new IdealComponentCoordinate("C1", new Point3D(0, 0, 0))),
                unavailable(), unavailable(), unavailable());
        assertEquals(1, chemistry.idealCoordinates().size());
    }

    @Test
    void predictedStructureCannotCarryExperimentalResolution() {
        assertThrows(IllegalArgumentException.class, () -> new StructureEvidence(
                "AF-Q1", "AlphaFold", "A", 1, null,
                StructureEvidence.StructureKind.PREDICTED, "v6",
                EvidenceChannel.notApplicable("predicted"),
                EvidenceChannel.present(2.0,
                        EvidenceOrigin.SOURCE_REPORTED, DERIVATION),
                EvidenceChannel.notEvaluated("not loaded")));
    }

    @Test
    void experimentalResolutionAndPredictedConfidenceCannotBeConfused() {
        PredictedModelConfidence confidence = new PredictedModelConfidence(
                "pLDDT", Map.of("mean", 91.2));

        assertThrows(IllegalArgumentException.class, () -> new StructureEvidence(
                "1ABC", "RCSB", "A", 1, "1",
                StructureEvidence.StructureKind.EXPERIMENTAL, null,
                EvidenceChannel.present("X-RAY DIFFRACTION",
                        EvidenceOrigin.SOURCE_REPORTED, DERIVATION),
                EvidenceChannel.present(1.8,
                        EvidenceOrigin.SOURCE_REPORTED, DERIVATION),
                EvidenceChannel.present(confidence,
                        EvidenceOrigin.SOURCE_REPORTED, DERIVATION)));
    }

    @Test
    void ligandOccurrenceIdentityKeepsInsertionAndAlternateLocationSeparate() {
        LigandOccurrenceId id = new LigandOccurrenceId(
                "1ABC", "1", 2, "B", "SAM", "501", "A", "B");

        assertEquals("A", id.insertionCode());
        assertEquals("B", id.alternateLocation());
    }

    private StructureEvidence experimentalStructure() {
        return new StructureEvidence("1ABC", "RCSB", "A", 1, "1",
                StructureEvidence.StructureKind.EXPERIMENTAL, null,
                EvidenceChannel.present("X-RAY DIFFRACTION",
                        EvidenceOrigin.SOURCE_REPORTED, DERIVATION),
                EvidenceChannel.present(1.8,
                        EvidenceOrigin.SOURCE_REPORTED, DERIVATION),
                EvidenceChannel.notApplicable("experimental structure"));
    }

    private PocketGeometryEvidence pocketGeometry() {
        return new PocketGeometryEvidence("pocket1",
                EvidenceChannel.present(1, EvidenceOrigin.SOURCE_REPORTED, FPOCKET),
                EvidenceChannel.present(42.0, EvidenceOrigin.SOURCE_REPORTED, FPOCKET),
                EvidenceChannel.present(Map.of("druggability", 0.8),
                        EvidenceOrigin.SOURCE_REPORTED, FPOCKET),
                EvidenceChannel.present(500.0,
                        EvidenceOrigin.SOURCE_REPORTED, FPOCKET),
                EvidenceChannel.present(List.of(new Point3D(1, 2, 3)),
                        EvidenceOrigin.SOURCE_OBSERVED, FPOCKET),
                EvidenceChannel.present(new Point3D(1, 2, 3),
                        EvidenceOrigin.DERIVED, DERIVATION),
                EvidenceChannel.notEvaluated("not computed"));
    }

    private ResidueContextEvidence residueContext() {
        return new ResidueContextEvidence(
                EvidenceChannel.empty(
                        EvidenceOrigin.SOURCE_OBSERVED, DERIVATION),
                unavailable(), unavailable(),
                unavailable(), unavailable());
    }

    private EvidenceProvenance provenance() {
        return new EvidenceProvenance("RCSB", "1ABC-assembly1", "2026-08-08",
                DERIVATION, Instant.parse("2026-08-08T00:00:00Z"), Map.of());
    }

    private static <T> EvidenceChannel<T> unavailable() {
        return EvidenceChannel.notEvaluated("not computed");
    }
}
