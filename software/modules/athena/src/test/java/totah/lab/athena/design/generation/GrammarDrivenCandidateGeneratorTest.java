package totah.lab.athena.design.generation;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.grammar.DesignGrammar;
import totah.lab.athena.ligand.screening.PhysicochemicalGate;

import java.util.List;import java.util.Map;import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class GrammarDrivenCandidateGeneratorTest {
 @Test void generatesWithArbitraryConfigurationAndBackend(){var g=grammar(Set.of("protected"),List.of("alkyl"));var backend=(MolecularTransformationBackend)(p,v,t,s)->List.of(new MolecularTransformationBackend.TransformationProduct(p+"-"+v.attachmentSite()+"-"+s,"configured edit",List.of("toy")));var out=new GrammarDrivenCandidateGenerator().generate(g,new GrammarDrivenCandidateGenerator.GenerationRequest("parent","graph","C-",4),backend);assertThat(out).hasSize(1);assertThat(out.getFirst().canonicalRepresentation()).contains("unrelated-site");}
 @Test void failsClosedOnNarrativeInvariantAndPlaceholderSubstituent(){var result=new GrammarDrivenCandidateGenerator().inspect(grammar(Set.of(),List.of("configuration-supplied")));assertThat(result.ready()).isFalse();assertThat(result.blockers()).anyMatch(x->x.contains("machine-readable")).anyMatch(x->x.contains("not resolved"));}
 private static DesignGrammar grammar(Set<String> protectedIds,List<String> substituents){var vector=new DesignGrammar.EditableVector("edit","unrelated-site",List.of("swap"),substituents,List.of("objective"),protectedIds,List.of("restriction"),List.of("intent"));return new DesignGrammar("toy","1",List.of(),List.of(),List.of(new DesignGrammar.ScaffoldInvariant("core","core",protectedIds,true)),List.of(vector),List.of(),List.of(),List.of(),List.of(),new DesignGrammar.ChemicalStatePolicy(-2,2,true,true),PhysicochemicalGate.Policy.mettl7Discovery(),new DesignGrammar.ExplorationPolicy(.1,.2,1),Set.of(),Map.of());}
}
