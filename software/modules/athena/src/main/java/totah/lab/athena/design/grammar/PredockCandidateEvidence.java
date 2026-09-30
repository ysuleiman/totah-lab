package totah.lab.athena.design.grammar;

import totah.lab.athena.ligand.screening.ChemicalLiabilityGate;
import totah.lab.athena.ligand.screening.PhysicochemicalGate;
import java.util.List;import java.util.Map;import java.util.Set;

/** Evidence computed by molecular/conformer tools; it intentionally contains no docking score. */
public record PredockCandidateEvidence(String candidateId,String conformerId,boolean validStructure,boolean assayStatePlausible,
 int formalCharge,PhysicochemicalGate.Descriptors descriptors,List<ChemicalLiabilityGate.Finding> liabilities,
 boolean synthesisPlausible,Set<String> satisfiedFeatureIds,Set<String> matchedCounterFeatureIds,
 Map<String,Double> measuredDistances,Map<String,Double> measuredAngles,Map<String,Boolean> scaffoldInvariantResults,
 Map<String,Boolean> templateAlignmentResults,boolean explorationCandidate,String explorationReason,List<String> provenance){
 public PredockCandidateEvidence{satisfiedFeatureIds=Set.copyOf(satisfiedFeatureIds);matchedCounterFeatureIds=Set.copyOf(matchedCounterFeatureIds);measuredDistances=Map.copyOf(measuredDistances);measuredAngles=Map.copyOf(measuredAngles);scaffoldInvariantResults=Map.copyOf(scaffoldInvariantResults);templateAlignmentResults=Map.copyOf(templateAlignmentResults);liabilities=List.copyOf(liabilities);provenance=List.copyOf(provenance);}
}
