package totah.lab.athena.design.grammar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.ligand.screening.PhysicochemicalGate;
import totah.lab.gaia.geometry.Point3D;
import java.nio.file.Path;
import java.util.List;import java.util.Map;import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class PredockGrammarEngineTest {
 @Test void executesTwoUnrelatedGrammarsWithoutSourceChangesAndPreservesDimensions(){
  DesignGrammar first=grammar("target-one","feature-X","site-alpha"), second=grammar("target-two","feature-Y","site-999");
  PredockCandidateEvidence evidence=evidence(Set.of("feature-Y"));PredockGrammarEngine engine=new PredockGrammarEngine();
  var a=engine.evaluate(first,evidence);var b=engine.evaluate(second,evidence);
  assertThat(a.positiveRecognition().getFirst().passed()).isFalse();
  assertThat(b.positiveRecognition().getFirst().passed()).isTrue();
  assertThat(b.counterRecognitionFeaturesMatched()).containsExactly("counter-any");
 assertThat(PredockCandidateEvidence.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName).noneMatch(n->n.toLowerCase().contains("vina")||n.toLowerCase().contains("docking"));
 }
 @Test void genericGrammarRoundTripsWithoutTargetAdapter(@TempDir Path directory) throws Exception {
  var expected=grammar("unrelated-target","different-count-feature","renumbered-site");var path=directory.resolve("grammar.json");var codec=new DesignGrammarJsonCodec();codec.writeGrammar(path,expected);var actual=codec.readGrammar(path);
  assertThat(actual).isEqualTo(expected);
 assertThat(new PredockGrammarEngine().evaluate(actual,evidence(Set.of("different-count-feature"))).positiveRecognition().getFirst().passed()).isTrue();
 }
 @Test void alignsConfiguredFeatureTemplatesWithoutTargetKnowledge(){var candidate=Map.of("f-a",new Point3D(2,3,4),"f-b",new Point3D(3,3,4),"f-c",new Point3D(2,4,4));var template=Map.of("f-a",new Point3D(0,0,0),"f-b",new Point3D(1,0,0),"f-c",new Point3D(0,1,0));var result=new FeatureTemplateAlignmentEvaluator().evaluate("arbitrary-template",candidate,template,1e-8);assertThat(result.passed()).isTrue();assertThat(result.rmsd()).isLessThan(1e-8);}
 private static DesignGrammar grammar(String id,String feature,String site){var f=new DesignGrammar.MolecularFeature(feature,DesignGrammar.FeatureType.CUSTOM,DesignGrammar.FeatureRole.POSITIVE_RECOGNITION,site,Map.of());return new DesignGrammar(id,"1",List.of(new DesignGrammar.FeatureGroup("positive",DesignGrammar.Requirement.OPTIONAL,1,List.of(f),List.of())),List.of(),List.of(),List.of(new DesignGrammar.EditableVector("vector",site,List.of("swap"),List.of("polar"),List.of("orient"),Set.of(),List.of(),List.of())),List.of(),List.of(),List.of(),List.of(),new DesignGrammar.ChemicalStatePolicy(-1,1,true,true),totah.lab.athena.ligand.screening.PhysicochemicalGate.Policy.mettl7Discovery(),new DesignGrammar.ExplorationPolicy(.1,.2,1),Set.of("template"),Map.of());}
 private static PredockCandidateEvidence evidence(Set<String> features){return new PredockCandidateEvidence("candidate","conf-1",true,true,0,new PhysicochemicalGate.Descriptors(300,0,1,3,2,50,2,2,20,.3),List.of(),true,features,Set.of("counter-any"),Map.of(),Map.of(),Map.of(),Map.of("template",true),false,"",List.of("toy"));}
}
