package totah.lab.athena.design.generation;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.grammar.ExecutableScaffoldGrammar;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class IndexedGraphDryRunTransformerTest {
 @Test void constructsThreeGraphOnlyDryRuns(){var a=new ExecutableScaffoldGrammar.IndexedAtom("A",1,"C",false,0,"UNSPECIFIED");var b=new ExecutableScaffoldGrammar.IndexedAtom("B",2,"C",false,0,"UNSPECIFIED");var bond=new ExecutableScaffoldGrammar.IndexedBond("A","B","SINGLE",false,"UNSPECIFIED");var graph=new ExecutableScaffoldGrammar.IndexedGraph(List.of(a,b),List.of(bond));var t=new IndexedGraphDryRunTransformer();assertThat(t.substituteElement(graph,"A","N").atoms().getFirst().element()).isEqualTo("N");assertThat(t.growSingleBond(graph,"A","C","F").atoms()).hasSize(3);assertThat(t.pruneTerminal(graph,"B").atoms()).hasSize(1);}
}
