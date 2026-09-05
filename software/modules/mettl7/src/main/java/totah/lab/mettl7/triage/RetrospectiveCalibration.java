package totah.lab.mettl7.triage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Loader and deterministic evaluator for the preserved reference panel. */
public final class RetrospectiveCalibration {
    public record CalibrationCase(Mettl7TriageInput input, NextAction expectedAction,
                                  AssessmentLevel expectedProductive,
                                  AssessmentLevel expectedAPrior,
                                  AssessmentLevel expectedBPrior) {}

    public record CaseResult(String identifier, boolean passed, List<String> mismatches) {
        public CaseResult { mismatches = List.copyOf(mismatches); }
    }

    public List<CalibrationCase> load(Path csv) throws IOException {
        try (var lines = Files.lines(csv)) {
            return lines.skip(1).filter(line -> !line.isBlank()).map(this::parse).toList();
        }
    }

    public List<CaseResult> evaluate(Path csv, Mettl7LigandTriageService service) throws IOException {
        List<CaseResult> results = new ArrayList<>();
        for (CalibrationCase calibration : load(csv)) {
            Mettl7TriageResult actual = service.assess(calibration.input());
            List<String> mismatches = new ArrayList<>();
            compare("nextAction", calibration.expectedAction(), actual.nextAction(), mismatches);
            compare("productive", calibration.expectedProductive(), actual.productiveStatePlausibility().level(), mismatches);
            compare("aPrior", calibration.expectedAPrior(), actual.aSelectivityPrior().level(), mismatches);
            compare("bPrior", calibration.expectedBPrior(), actual.bSelectivityPrior().level(), mismatches);
            results.add(new CaseResult(actual.identifier(), mismatches.isEmpty(), mismatches));
        }
        return List.copyOf(results);
    }

    private CalibrationCase parse(String line) {
        String[] v = line.split(",", -1);
        if (v.length != 24) throw new IllegalArgumentException("expected 24 calibration columns: " + line);
        ChemistryFeatures chemistry = new ChemistryFeatures(v[2], bool(v[3]), bool(v[4]), bool(v[5]),
                bool(v[6]), bool(v[7]), bool(v[8]));
        ExperimentalFeatures experimental = new ExperimentalFeatures(bool(v[9]), bool(v[10]), bool(v[11]),
                bool(v[12]), bool(v[13]));
        RecognitionFeatures recognition = new RecognitionFeatures(set(v[14]), set(v[15]), bool(v[16]),
                bool(v[17]), bool(v[18]), bool(v[19]));
        EvidenceObservation evidence = new EvidenceObservation("retrospective calibration", "reference panel",
                Confidence.MODERATE, EvidenceClass.STRUCTURAL_INFERENCE, EvidenceTiming.RETROSPECTIVE,
                "RETROSPECTIVE_CALIBRATION.csv#" + v[0]);
        Mettl7TriageInput input = new Mettl7TriageInput(v[0], v[1], chemistry, recognition, experimental,
                CofactorEvidence.none(), List.of(), List.of(evidence));
        return new CalibrationCase(input, NextAction.valueOf(v[20]), AssessmentLevel.valueOf(v[21]),
                AssessmentLevel.valueOf(v[22]), AssessmentLevel.valueOf(v[23]));
    }

    private static boolean bool(String value) { return Boolean.parseBoolean(value); }
    private static Set<String> set(String value) {
        return value.isBlank() ? Set.of() : Arrays.stream(value.split("\\|"))
                .collect(Collectors.toUnmodifiableSet());
    }
    private static void compare(String field, Object expected, Object actual, List<String> mismatches) {
        if (!expected.equals(actual)) mismatches.add(field + ": expected " + expected + " but was " + actual);
    }
}
