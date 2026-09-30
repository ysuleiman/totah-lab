package totah.lab.athena.design.grammar;

import java.util.List;

/** Complete dimension-preserving result. There is deliberately no aggregate score. */
public record PredockEvidenceVector(String candidateId,String conformerId,String grammarId,String grammarVersion,
 List<RuleEvidence> hardRules,List<RuleEvidence> positiveRecognition,List<RuleEvidence> distanceEnvelopes,
 List<RuleEvidence> angleConstraints,List<String> alternativeFeaturesUsed,List<String> counterRecognitionFeaturesMatched,
 List<RuleEvidence> scaffoldConstraints,List<RuleEvidence> templateCompatibility,RuleEvidence chemicalState,
 RuleEvidence physicochemical,RuleEvidence liability,RuleEvidence synthesis,ExplorationEvidence exploration,
 boolean hardRulesPass,List<String> provenance){
 public PredockEvidenceVector{hardRules=List.copyOf(hardRules);positiveRecognition=List.copyOf(positiveRecognition);distanceEnvelopes=List.copyOf(distanceEnvelopes);angleConstraints=List.copyOf(angleConstraints);alternativeFeaturesUsed=List.copyOf(alternativeFeaturesUsed);counterRecognitionFeaturesMatched=List.copyOf(counterRecognitionFeaturesMatched);scaffoldConstraints=List.copyOf(scaffoldConstraints);templateCompatibility=List.copyOf(templateCompatibility);provenance=List.copyOf(provenance);}
 public record RuleEvidence(String id,boolean evaluated,boolean passed,String observed,String reason){}
 public record ExplorationEvidence(boolean requested,boolean allowed,String reason,int softExceptionsUsed){}
}
