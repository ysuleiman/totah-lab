package totah.lab.athena.design.generation;

import totah.lab.athena.design.grammar.ExecutableScaffoldGrammar;

import java.util.ArrayList;
import java.util.List;

/** Graph-only transformation primitives for validation; performs no candidate advancement. */
public final class IndexedGraphDryRunTransformer {
 public ExecutableScaffoldGrammar.IndexedGraph substituteElement(ExecutableScaffoldGrammar.IndexedGraph graph,String atomId,String element){List<ExecutableScaffoldGrammar.IndexedAtom>atoms=graph.atoms().stream().map(a->a.id().equals(atomId)?new ExecutableScaffoldGrammar.IndexedAtom(a.id(),a.sourceIndex(),element,a.aromatic(),a.formalCharge(),a.stereochemistry()):a).toList();if(atoms.equals(graph.atoms()))throw new IllegalArgumentException("atom not found");return new ExecutableScaffoldGrammar.IndexedGraph(atoms,graph.bonds());}
 public ExecutableScaffoldGrammar.IndexedGraph growSingleBond(ExecutableScaffoldGrammar.IndexedGraph graph,String anchorId,String newAtomId,String element){if(graph.atoms().stream().noneMatch(a->a.id().equals(anchorId)))throw new IllegalArgumentException("anchor not found");if(graph.atoms().stream().anyMatch(a->a.id().equals(newAtomId)))throw new IllegalArgumentException("new atom exists");var atoms=new ArrayList<>(graph.atoms());atoms.add(new ExecutableScaffoldGrammar.IndexedAtom(newAtomId,0,element,false,0,"UNSPECIFIED"));var bonds=new ArrayList<>(graph.bonds());bonds.add(new ExecutableScaffoldGrammar.IndexedBond(anchorId,newAtomId,"SINGLE",false,"UNSPECIFIED"));return new ExecutableScaffoldGrammar.IndexedGraph(atoms,bonds);}
 public ExecutableScaffoldGrammar.IndexedGraph pruneTerminal(ExecutableScaffoldGrammar.IndexedGraph graph,String atomId){long degree=graph.bonds().stream().filter(b->b.firstAtomId().equals(atomId)||b.secondAtomId().equals(atomId)).count();if(degree!=1)throw new IllegalArgumentException("pruning requires terminal atom");return new ExecutableScaffoldGrammar.IndexedGraph(graph.atoms().stream().filter(a->!a.id().equals(atomId)).toList(),graph.bonds().stream().filter(b->!b.firstAtomId().equals(atomId)&&!b.secondAtomId().equals(atomId)).toList());}
}
