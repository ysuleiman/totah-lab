package totah.lab.athena.pocket.evidence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceChannelTest {

    private static final EvidenceMethod METHOD =
            new EvidenceMethod("test-method", "1.0");

    @Test
    void distinguishesEvaluatedEmptyFromEveryUnavailableState() {
        EvidenceChannel<List<String>> present = EvidenceChannel.present(
                List.of("SAM"), EvidenceOrigin.SOURCE_OBSERVED, METHOD);
        EvidenceChannel<List<String>> empty = EvidenceChannel.empty(
                EvidenceOrigin.SOURCE_OBSERVED, METHOD);
        EvidenceChannel<List<String>> skipped =
                EvidenceChannel.notEvaluated("search was not requested");
        EvidenceChannel<List<String>> irrelevant =
                EvidenceChannel.notApplicable("predicted structure has no bound ligand");
        EvidenceChannel<List<String>> failed =
                EvidenceChannel.failed("MMCIF_PARSE_FAILED", "atom-site loop was malformed");

        assertEquals(EvaluationStatus.PRESENT, present.status());
        assertEquals(List.of("SAM"),
                assertInstanceOf(EvidenceChannel.Present.class, present).value());
        assertEquals(EvaluationStatus.EMPTY, empty.status());
        assertEquals(EvaluationStatus.NOT_EVALUATED, skipped.status());
        assertEquals(EvaluationStatus.NOT_APPLICABLE, irrelevant.status());
        assertEquals(EvaluationStatus.FAILED, failed.status());
    }

    @Test
    void rejectsNullEvaluatedValuesAndBlankFailureMetadata() {
        assertThrows(NullPointerException.class, () -> EvidenceChannel.present(
                null, EvidenceOrigin.DERIVED, METHOD));
        assertThrows(IllegalArgumentException.class, () -> EvidenceChannel.present(
                List.of(), EvidenceOrigin.DERIVED, METHOD));
        assertThrows(IllegalArgumentException.class,
                () -> EvidenceChannel.failed(" ", "failed"));
        assertThrows(IllegalArgumentException.class,
                () -> EvidenceChannel.notEvaluated(" "));
    }

    @Test
    void evaluatedEmptyRetainsOriginAndMethodWithoutInventingAValue() {
        EvidenceChannel.Empty<String> empty = EvidenceChannel.empty(
                EvidenceOrigin.DERIVED, METHOD);

        assertEquals(EvaluationStatus.EMPTY, empty.status());
        assertEquals(EvidenceOrigin.DERIVED, empty.origin());
        assertEquals(METHOD, empty.method());
    }

    @Test
    void snapshotsEvaluatedCollections() {
        List<String> mutable = new ArrayList<>(List.of("SAM"));
        EvidenceChannel.Present<List<String>> evaluated = EvidenceChannel.present(
                mutable, EvidenceOrigin.SOURCE_OBSERVED, METHOD);
        mutable.add("SAH");

        assertEquals(List.of("SAM"), evaluated.value());
        assertThrows(UnsupportedOperationException.class,
                () -> evaluated.value().add("ATP"));
    }
}
