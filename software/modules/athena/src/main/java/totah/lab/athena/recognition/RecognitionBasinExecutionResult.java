package totah.lab.athena.recognition;

import java.util.List;

public record RecognitionBasinExecutionResult(List<BasinResult> basins,
        List<PairResult> pairAdmissions, List<RecognitionBasinBuildResult.EvidenceInsufficiency> insufficiencies,
        RecognitionRecurrencePolicy recurrencePolicy) {
    public RecognitionBasinExecutionResult { basins=List.copyOf(basins);pairAdmissions=List.copyOf(pairAdmissions);insufficiencies=List.copyOf(insufficiencies); }
    public record BasinResult(RecognitionBasin basin, boolean recurrent) { }
    public record PairResult(String firstPoseId,String secondPoseId,RecognitionPairAdmission admission) { }
}
