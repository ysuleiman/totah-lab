package totah.lab.athena.design.generation;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.design.generation.MolecularDesignTree.*;
import static totah.lab.athena.design.generation.MolecularDesignGraphGenerator.*;

/** Acceptance of execution guarantees, independent of a particular chemical toolkit. */
class DesignProvenanceAcceptanceTest {
    private static final BackendEvidence EVIDENCE = new BackendEvidence("fixture", "1", "validation", Map.of(), List.of(), List.of());
    private static final MolecularSanitizer SANITIZER = (g,p) -> new MolecularSanitizer.Result(g,true,EVIDENCE);
    private static final CanonicalIdentityService IDENTITY = g -> new CanonicalIdentityService.Result(
            g.atoms().stream().map(a -> a.element()+a.formalCharge()).sorted().toList()+"/"+g.bonds().stream().map(b -> b.order().name()).sorted().toList(), EVIDENCE);
    private static final GeometryValidator GEOMETRY = (p,e,g) -> new GeometryResult(true,List.of("evaluated"),Set.copyOf(e.provenance().geometryConstraints()));

    @Test void multipleHypothesesRetainStatesAndSymmetricLineage() throws Exception {
        var first = substitute("first", "N", context("h1",List.of()));
        var second = substitute("second", "N", context("h2",List.of()));
        var tree = run(chain(),List.of(second,first));
        assertEquals(3,tree.states().size()); assertEquals(2,tree.nodes().size());
        var duplicate = tree.attempts().get(1);
        assertEquals(Outcome.DEDUPLICATED,duplicate.outcome());
        assertTrue(duplicate.representativeMapping().ambiguous());
        assertEquals(2,duplicate.representativeMapping().alternatives().size());
        assertTrue(duplicate.representativeMapping().exhaustive());
        assertEquals(3,duplicate.parentToRepresentativeAtoms().size());
        assertEquals(2,duplicate.parentToRepresentativeBonds().size());
        assertNotEquals(tree.states().get(1).provenance(),tree.states().get(2).provenance());
        assertEquals(duplicate.resultingProduct(),duplicate.finalDelta().replay(duplicate.parent()));
        assertEquals("h2",duplicate.operation().provenance().hypothesis().id());
    }

    @Test void topologyReplacementUsesSamePathAndComposesRenamedAtomsAndBonds() throws Exception {
        var tree = run(chain(),List.of(replace("one","x"),replace("two","y")));
        assertEquals(List.of(Outcome.ACCEPTED,Outcome.DEDUPLICATED),tree.attempts().stream().map(Attempt::outcome).toList());
        var second = tree.attempts().get(1);
        assertNotNull(second.topologyReceipt()); assertNull(second.graphReceipt());
        assertEquals("x",second.representativeMapping().selected().atoms().get("y"));
        assertEquals("x",second.parentToRepresentativeAtoms().get("b"));
        assertEquals("x-left",second.representativeMapping().selected().bonds().get("y-left"));
        assertEquals(second.attemptedProduct(),second.topologyReceipt().delta().replay(chain()));
        assertTrue(second.resultingProduct().atom("y").isPresent()); // derivation keeps its own IDs
    }

    @Test void parentAtomOrderingDoesNotInventLineage() throws Exception {
        var reordered = new MolecularGraph(chain().atoms().reversed(),chain().bonds().reversed(),Map.of());
        var normal = run(chain(),List.of(replace("one","x"),replace("two","y")));
        var reversed = run(reordered,List.of(replace("one","x"),replace("two","y")));
        assertEquals(normal.attempts().get(1).representativeMapping(),reversed.attempts().get(1).representativeMapping());
        assertEquals(reordered,reversed.attempts().getFirst().finalDelta().before());
        assertEquals(normal.attempts().get(1).parentToRepresentativeAtoms(),reversed.attempts().get(1).parentToRepresentativeAtoms());
    }

    @Test void canonicalCollisionWithoutGraphProofIsRejected() throws Exception {
        CanonicalIdentityService collision = g -> new CanonicalIdentityService.Result("same",EVIDENCE);
        var tree=execute(chain(),List.of(substitute("edit","N",context("h",List.of()))),SANITIZER,collision,GEOMETRY,a->{},config(20,1,20));
        assertEquals(Outcome.LINEAGE_MAPPING_FAILURE,tree.attempts().getFirst().outcome());
        assertEquals(1,tree.states().size());
    }

    @Test void rejectedAuthorizationHasAnOperationAndParentReceipt() throws Exception {
        var op=substitute("edit","N",context("h",List.of()));
        var denied=new AuthorizedEdit(op.edit(),new GraphEditTransactionEngine.Authorization("wrong",Set.of(),Set.of(),Set.of(),Set.of()),0,op.provenance());
        var receipt=run(chain(),List.of(denied)).attempts().getFirst();
        assertEquals(Outcome.AUTHORIZATION_REJECTED,receipt.outcome()); assertEquals(denied,receipt.operation());
        assertEquals(chain(),receipt.parent()); assertNull(receipt.resultingStateId());
    }

    @Test void disconnectedProductHasExplicitTopologyReceipt() throws Exception {
        var edit=new GraphEdit("prune","v",GraphEdit.Type.SUBSTITUENT_PRUNING,Set.of("b"),Set.of(),null,null,null,null,Map.of());
        var receipt=run(chain(),List.of(authorized(edit,context("h",List.of())))).attempts().getFirst();
        assertEquals(Outcome.INVALID_TOPOLOGY,receipt.outcome()); assertNotNull(receipt.graphReceipt());
        assertEquals(2,receipt.attemptedProduct().atoms().size());
    }

    @Test void retainedAnchorRejectsChemicalChangeAndIncidentDeletionEvenWhenAdditionsAllowed() throws Exception {
        var anchor=new RetainedAnchor("anchor",Set.of("b"),Set.of(),true);
        assertEquals(Outcome.AUTHORIZATION_REJECTED,run(chain(),List.of(substitute("edit","N",context("h",List.of(anchor))))).attempts().getFirst().outcome());
        var prune=new GraphEdit("prune","v",GraphEdit.Type.SUBSTITUENT_PRUNING,Set.of("a"),Set.of(),null,null,null,null,Map.of());
        assertEquals(Outcome.AUTHORIZATION_REJECTED,run(chain(),List.of(authorized(prune,context("h",List.of(anchor))))).attempts().getFirst().outcome());
    }

    @Test void invalidChemistryBackendFailureAndUnexpectedFailureAreDifferent() throws Exception {
        var op=List.of(substitute("edit","N",context("h",List.of())));
        MolecularSanitizer invalid=(g,p)->new MolecularSanitizer.Result(g,!g.atom("b").orElseThrow().element().equals("N"),EVIDENCE);
        MolecularSanitizer failed=(g,p)->{if(g.atom("b").orElseThrow().element().equals("N"))throw new MolecularBackendException("backend failed");return SANITIZER.sanitize(g,p);};
        MolecularSanitizer crashed=(g,p)->{if(g.atom("b").orElseThrow().element().equals("N"))throw new IllegalStateException("crashed");return SANITIZER.sanitize(g,p);};
        assertEquals(Outcome.INVALID_CHEMISTRY,execute(chain(),op,invalid,IDENTITY,GEOMETRY,a->{},config(20,1,20)).attempts().getFirst().outcome());
        assertEquals(Outcome.BACKEND_VALIDATION_FAILURE,execute(chain(),op,failed,IDENTITY,GEOMETRY,a->{},config(20,1,20)).attempts().getFirst().outcome());
        assertEquals(Outcome.UNEXPECTED_EXECUTION_FAILURE,execute(chain(),op,crashed,IDENTITY,GEOMETRY,a->{},config(20,1,20)).attempts().getFirst().outcome());
    }

    @Test void backendCannotSilentlyChangeChargeEvenWhenItClaimsValid() throws Exception {
        MolecularSanitizer mutation=(g,p)->{
            if(!g.atom("b").orElseThrow().element().equals("N"))return SANITIZER.sanitize(g,p);
            var changed=new MolecularGraph(g.atoms().stream().map(a->new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),1,a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),a.coordinates(),a.properties())).toList(),g.bonds(),g.properties());
            return new MolecularSanitizer.Result(changed,true,EVIDENCE);
        };
        var receipt=execute(chain(),List.of(substitute("edit","N",context("h",List.of()))),mutation,IDENTITY,GEOMETRY,a->{},config(20,1,20)).attempts().getFirst();
        assertEquals(Outcome.INVALID_CHEMISTRY,receipt.outcome()); assertTrue(receipt.validationDelta().chemicalGraphChanged());
    }

    @Test void missingGeometryEvaluationFailsExplicitly() throws Exception {
        var p=context("h",List.of());
        var withGeometry=new Provenance(p.hypothesis(),p.evidence(),p.retainedAnchors(),p.rule(),p.resources(),p.intendedEffect(),List.of(new Reference("geometry","1")),false);
        var tree=execute(chain(),List.of(substitute("edit","N",withGeometry)),SANITIZER,IDENTITY,(a,b,c)->new GeometryResult(true,List.of(),Set.of()),a->{},config(20,1,20));
        assertEquals(Outcome.GEOMETRY_FAILURE,tree.attempts().getFirst().outcome());
    }

    @Test void enumerationIsDeterministicAndEveryKnownBudgetBlockedOperationIsReceipted() throws Exception {
        var ops=List.of(substitute("z","O",context("h",List.of())),substitute("a","N",context("h",List.of())));
        var config=config(20,1,1);
        var first=execute(chain(),ops,SANITIZER,IDENTITY,GEOMETRY,a->{},config);
        var second=execute(chain(),ops.reversed(),SANITIZER,IDENTITY,GEOMETRY,a->{},config);
        assertEquals(first,second);assertEquals(2,first.attempts().size());
        assertEquals(Outcome.SEARCH_BUDGET_TERMINATION,first.attempts().get(1).outcome());
        assertEquals(TerminationReason.MAXIMUM_ATTEMPTS,first.termination().reason());
        assertEquals(1,first.termination().unexecutedOperations());
    }

    @Test void depthAndNodeLimitsProduceReceipts() throws Exception {
        var ops=List.of(substitute("a","N",context("h",List.of())));
        for(var config:List.of(config(1,1,10),config(10,0,10))) {
            var tree=execute(chain(),ops,SANITIZER,IDENTITY,GEOMETRY,a->{},config);
            assertEquals(Outcome.SEARCH_BUDGET_TERMINATION,tree.attempts().getFirst().outcome());
            assertEquals(0,tree.termination().attemptedOperations());
        }
    }

    @Test void persistenceCallbacksCaptureRootStatesAttemptsAndTerminationAndIoErrorsPropagate() throws Exception {
        var calls=new ArrayList<String>();
        var sink=new OutcomeSink(){
            public void started(MolecularGraph g,Provenance p){calls.add("root");assertEquals("root",p.hypothesis().id());}
            public void state(DesignState s){calls.add("state");}
            public void planned(String id,DesignState p,AuthorizedEdit e){calls.add("planned");}
            public void record(Attempt a){calls.add("receipt");}
            public void terminated(Termination t){calls.add("termination");}
        };
        var ops=List.of(substitute("a","N",context("h",List.of())));
        execute(chain(),ops,SANITIZER,IDENTITY,GEOMETRY,sink,config(20,1,20));
        assertEquals(List.of("root","state","planned","receipt","state","termination"),calls);
        assertThrows(IOException.class,()->execute(chain(),ops,SANITIZER,IDENTITY,GEOMETRY,a->{throw new IOException("disk full");},config(20,1,20)));
    }

    @Test void malformedTopologyIsRejectedEvenWhenDisconnectedGraphsAllowed() throws Exception {
        var malformed=new MolecularGraph(chain().atoms(),List.of(bond("bad","a","missing")),Map.of());
        var config=new Configuration(GenerationStrategy.ENUMERATIVE,20,1,20,true,new MolecularSanitizer.SanitizationPolicy(Set.of(),true));
        var tree=execute(malformed,List.of(),SANITIZER,IDENTITY,GEOMETRY,a->{},config);
        assertEquals(Outcome.INVALID_TOPOLOGY,tree.attempts().getFirst().outcome());
    }

    @Test void sameOperationIdCanCarryDistinctHypotheses() throws Exception {
        var tree=run(chain(),List.of(substitute("same","N",context("h1",List.of())),substitute("same","N",context("h2",List.of()))));
        assertEquals(3,tree.states().size());
        assertNotEquals(tree.attempts().get(0).attemptId(),tree.attempts().get(1).attemptId());
        assertEquals("root",tree.attempts().get(1).parentDesignState().provenance().hypothesis().id());
    }

    @Test void plannerAndMissingBackendEvidenceFailuresProduceReceipts() throws Exception {
        var generator=new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(),SANITIZER,IDENTITY);
        var tree=generator.generateTraced(chain(),context("root",List.of()),config(20,1,20),s->{throw new IllegalStateException("planner failed");},a->{},GEOMETRY);
        assertEquals(TerminationReason.PLANNER_FAILURE,tree.termination().reason());
        assertEquals(Outcome.UNEXPECTED_EXECUTION_FAILURE,tree.attempts().getFirst().outcome());
        var invalid=execute(chain(),List.of(),(g,p)->new MolecularSanitizer.Result(g,true,null),IDENTITY,GEOMETRY,a->{},config(20,1,20));
        assertEquals(Outcome.BACKEND_VALIDATION_FAILURE,invalid.attempts().getFirst().outcome());
    }

    @Test void coordinateOnlyBackendChangesRemainDistinctAndReachGeometryEvaluation() throws Exception {
        MolecularSanitizer coordinates=(g,p)->{
            if(!g.atom("b").orElseThrow().element().equals("N"))return SANITIZER.sanitize(g,p);
            var moved=new MolecularGraph(g.atoms().stream().map(a->new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),new MolecularGraph.Coordinates(1,2,3),a.properties())).toList(),g.bonds(),g.properties());
            return new MolecularSanitizer.Result(moved,true,EVIDENCE);
        };
        var receipt=execute(chain(),List.of(substitute("edit","N",context("h",List.of()))),coordinates,IDENTITY,GEOMETRY,a->{},config(20,1,20)).attempts().getFirst();
        assertEquals(Outcome.ACCEPTED,receipt.outcome());
        assertFalse(receipt.validationDelta().chemicalGraphChanged());assertEquals(3,receipt.validationDelta().coordinates().size());
        assertEquals(List.of("evaluated"),receipt.geometryEvidence());
    }

    @Test void durableJournalRoundTripsCompleteReceiptsAndRefusesOverwrite(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var path=directory.resolve("run.jsonl");
        MolecularDesignTree tree;
        try(var journal=new Journal(path)) {
            tree=execute(chain(),List.of(replace("one","x"),replace("two","y")),SANITIZER,IDENTITY,GEOMETRY,journal,config(20,1,20));
        }
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var lines=java.nio.file.Files.readAllLines(path);
        var receipts=new ArrayList<Attempt>();
        long sequence=0;
        for(var line:lines) {
            var entry=mapper.readTree(line);
            assertEquals(Journal.SCHEMA,entry.get("schema").asText());
            assertEquals(sequence++,entry.get("sequence").asLong());
            if(entry.get("event").asText().equals("receipt"))receipts.add(mapper.treeToValue(entry.get("data"),Attempt.class));
        }
        assertEquals(tree.attempts(),receipts);
        assertEquals(receipts.get(1).resultingProduct(),receipts.get(1).finalDelta().replay(receipts.get(1).parent()));
        assertEquals("terminated",mapper.readTree(lines.getLast()).get("event").asText());
        assertThrows(java.nio.file.FileAlreadyExistsException.class,()->new Journal(path));
        assertEquals(lines,java.nio.file.Files.readAllLines(path));
    }

    @Test void durableJournalRetainsRejectedAndBudgetBlockedOperations(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var path=directory.resolve("rejected.jsonl");
        var anchor=new RetainedAnchor("anchor",Set.of("b"),Set.of(),false);
        try(var journal=new Journal(path)) {
            execute(chain(),List.of(substitute("a","N",context("h",List.of(anchor))),substitute("z","O",context("h",List.of()))),SANITIZER,IDENTITY,GEOMETRY,journal,config(20,1,1));
        }
        String stored=java.nio.file.Files.readString(path);
        assertTrue(stored.contains("AUTHORIZATION_REJECTED"));assertTrue(stored.contains("SEARCH_BUDGET_TERMINATION"));
    }

    static MolecularDesignTree run(MolecularGraph root,List<AuthorizedEdit> edits)throws IOException {
        return execute(root,edits,SANITIZER,IDENTITY,GEOMETRY,a->{},config(20,1,20));
    }
    static MolecularDesignTree execute(MolecularGraph root,List<AuthorizedEdit> edits,MolecularSanitizer sanitizer,CanonicalIdentityService identity,GeometryValidator geometry,OutcomeSink sink,Configuration config)throws IOException {
        return new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(),sanitizer,identity).generateTraced(root,context("root",List.of()),config,s->s.depth()==0?edits:List.of(),sink,geometry);
    }
    static Configuration config(int nodes,int depth,int attempts){return new Configuration(GenerationStrategy.ENUMERATIVE,nodes,depth,attempts,false,new MolecularSanitizer.SanitizationPolicy(Set.of(),true));}
    static Provenance context(String hypothesis,List<RetainedAnchor> anchors){return new Provenance(new Reference(hypothesis,"v1"),List.of(new Reference("evidence","v2")),anchors,new Reference("rule","v3"),List.of(new Reference("resource","sha256:fixture")),"opaque interaction intent",List.of(),false);}
    static AuthorizedEdit substitute(String id,String element,Provenance context){return authorized(new GraphEdit(id,"v",GraphEdit.Type.ATOM_SUBSTITUTION,Set.of("b"),Set.of(),null,null,element,null,Map.of()),context);}
    static AuthorizedEdit authorized(GraphEdit e,Provenance p){return new AuthorizedEdit(e,new GraphEditTransactionEngine.Authorization("v",Set.of(e.type()),Set.of("a","b","c"),Set.of(),Set.of()),0,p);}
    static AuthorizedEdit replace(String id,String target){var e=new TopologyEdit(id,TopologyEdit.Type.INDEXED_SUBGRAPH_REPLACEMENT,Set.of("b"),new MolecularGraph(List.of(atom(target,"N")),List.of(),Map.of()),List.of(new TopologyEdit.Attachment("a",target,MolecularGraph.BondOrder.SINGLE,target+"-left"),new TopologyEdit.Attachment("c",target,MolecularGraph.BondOrder.SINGLE,target+"-right")),Set.of(),List.of(),Map.of("b",target),TopologyEdit.StereoDisposition.PRESERVE_UNAFFECTED);return new AuthorizedEdit(e,new TopologyEditTransactionEngine.Authorization(Set.of(e.type()),Set.of("a","b","c"),Set.of(),Set.of()),0,context(id,List.of()));}
    static MolecularGraph chain(){return new MolecularGraph(List.of(atom("a","C"),atom("b","C"),atom("c","C")),List.of(bond("ab","a","b"),bond("bc","b","c")),Map.of());}
    static MolecularGraph.Atom atom(String id,String element){return new MolecularGraph.Atom(id,element,null,0,0,false,"UNSPECIFIED",null,Map.of());}
    static MolecularGraph.Bond bond(String id,String a,String b){return new MolecularGraph.Bond(id,a,b,MolecularGraph.BondOrder.SINGLE,false,"UNSPECIFIED",Map.of());}
}
