package totah.lab.athena.design.grammar;

import totah.lab.athena.ligand.screening.ChemicalLiabilityGate;
import totah.lab.athena.ligand.screening.PhysicochemicalGate;
import java.util.ArrayList;import java.util.List;

/** Generic deterministic evaluator of configured grammar evidence. */
public final class PredockGrammarEngine {
 private final ChemicalLiabilityGate liabilities=new ChemicalLiabilityGate();
 public PredockEvidenceVector evaluate(DesignGrammar grammar,PredockCandidateEvidence c){
  var hard=new ArrayList<PredockEvidenceVector.RuleEvidence>();
  boolean state=c.assayStatePlausible()&&c.formalCharge()>=grammar.chemicalStatePolicy().minimumFormalCharge()&&c.formalCharge()<=grammar.chemicalStatePolicy().maximumFormalCharge();
  var stateRule=rule("CHEMICAL_STATE",true,state,Integer.toString(c.formalCharge()),"assay-state and configured charge bounds");
  var phys=new PhysicochemicalGate(grammar.physicochemicalPolicy()).evaluate(c.descriptors());var physRule=rule("PHYSICOCHEMICAL",true,phys.accepted(),c.descriptors().toString(),String.join(";",phys.reasons()));
  var liab=liabilities.evaluate(c.liabilities());var liabilityRule=rule("LIABILITY",true,liab.accepted(),Integer.toString(liab.findings().size()),liab.accepted()?"Athena liability gate clear":"Athena liability gate findings");
  var synth=rule("SYNTHESIS",true,c.synthesisPlausible(),Boolean.toString(c.synthesisPlausible()),"configured synthesis assessment");
  for(var configured:grammar.hardConstraints()){
   PredockEvidenceVector.RuleEvidence evaluated=switch(configured.type()){
    case VALID_STRUCTURE->rule(configured.id(),true,c.validStructure(),Boolean.toString(c.validStructure()),configured.definition());
    case CHEMICAL_STATE->rename(stateRule,configured.id(),configured.definition());
    case PHYSICOCHEMICAL->rename(physRule,configured.id(),configured.definition());
    case LIABILITY->rename(liabilityRule,configured.id(),configured.definition());
    case SYNTHESIS->rename(synth,configured.id(),configured.definition());
    case SCAFFOLD_CONNECTIVITY->rule(configured.id(),false,false,"",configured.definition());
    case CUSTOM->rule(configured.id(),false,false,"",configured.definition());
   };
   hard.add(evaluated);
  }
  var scaffold=new ArrayList<PredockEvidenceVector.RuleEvidence>();for(var x:grammar.scaffoldInvariants()){boolean v=c.scaffoldInvariantResults().getOrDefault(x.id(),false);var e=rule(x.id(),c.scaffoldInvariantResults().containsKey(x.id()),v,Boolean.toString(v),x.definition());scaffold.add(e);if(x.required())hard.add(e);}
  var positive=new ArrayList<PredockEvidenceVector.RuleEvidence>();var alternatives=new ArrayList<String>();int softMiss=0;
  var directFeatureGroupPass=new java.util.LinkedHashMap<String,Boolean>();for(var g:grammar.positiveFeatureGroups()){long n=g.features().stream().filter(f->c.satisfiedFeatureIds().contains(f.id())).count();directFeatureGroupPass.put(g.id(),n>=g.minimumSatisfied());}
  for(var g:grammar.positiveFeatureGroups()){long n=g.features().stream().filter(f->c.satisfiedFeatureIds().contains(f.id())).count();boolean alternativePass=g.alternativeGroupIds().stream().anyMatch(id->directFeatureGroupPass.getOrDefault(id,false));boolean pass=directFeatureGroupPass.get(g.id())||alternativePass;var evidence=rule(g.id(),true,pass,n+"/"+g.features().size(),g.requirement().name()+(alternativePass?"; configured alternative satisfied":""));positive.add(evidence);if(g.requirement()==DesignGrammar.Requirement.REQUIRED)hard.add(evidence);else if(!pass)softMiss++;g.features().stream().filter(f->f.role()==DesignGrammar.FeatureRole.ALTERNATIVE&&c.satisfiedFeatureIds().contains(f.id())).map(DesignGrammar.MolecularFeature::id).forEach(alternatives::add);}
  var distances=new ArrayList<PredockEvidenceVector.RuleEvidence>();for(var x:grammar.distanceEnvelopes()){Double v=c.measuredDistances().get(x.id());boolean ok=v!=null&&v>=x.minimum()&&v<=x.maximum();distances.add(rule(x.id(),v!=null,ok,v==null?"":v.toString(),x.minimum()+".."+x.maximum()));if(!ok&&!x.hard())softMiss++;if(x.hard())hard.add(distances.getLast());}
  var angles=new ArrayList<PredockEvidenceVector.RuleEvidence>();for(var x:grammar.angleConstraints()){Double v=c.measuredAngles().get(x.id());boolean ok=v!=null&&v>=x.minimumDegrees()&&v<=x.maximumDegrees();angles.add(rule(x.id(),v!=null,ok,v==null?"":v.toString(),x.minimumDegrees()+".."+x.maximumDegrees()));if(!ok&&!x.hard())softMiss++;if(x.hard())hard.add(angles.getLast());}
  var templates=grammar.templateIds().stream().sorted().map(id->{boolean v=c.templateAlignmentResults().getOrDefault(id,false);return rule(id,c.templateAlignmentResults().containsKey(id),v,Boolean.toString(v),"configured conformer/template alignment");}).toList();if(templates.stream().noneMatch(PredockEvidenceVector.RuleEvidence::passed))softMiss++;
  boolean hardPass=hard.stream().filter(PredockEvidenceVector.RuleEvidence::evaluated).allMatch(PredockEvidenceVector.RuleEvidence::passed)&&hard.stream().allMatch(PredockEvidenceVector.RuleEvidence::evaluated);
  boolean explorationAllowed=c.explorationCandidate()&&hardPass&&softMiss<=grammar.explorationPolicy().maximumSoftExceptions();
  return new PredockEvidenceVector(c.candidateId(),c.conformerId(),grammar.id(),grammar.version(),hard,positive,distances,angles,alternatives,c.matchedCounterFeatureIds().stream().sorted().toList(),scaffold,templates,stateRule,physRule,liabilityRule,synth,new PredockEvidenceVector.ExplorationEvidence(c.explorationCandidate(),explorationAllowed,c.explorationReason(),softMiss),hardPass,c.provenance());
 }
 private static PredockEvidenceVector.RuleEvidence rule(String id,boolean evaluated,boolean passed,String observed,String reason){return new PredockEvidenceVector.RuleEvidence(id,evaluated,passed,observed,reason==null?"":reason);}
 private static PredockEvidenceVector.RuleEvidence rename(PredockEvidenceVector.RuleEvidence evidence,String id,String definition){String configured=definition==null?"":definition.trim();String detail=evidence.reason()==null?"":evidence.reason().trim();String reason=configured.isEmpty()?detail:detail.isEmpty()?configured:configured+"; "+detail;return new PredockEvidenceVector.RuleEvidence(id,evidence.evaluated(),evidence.passed(),evidence.observed(),reason);}
}
