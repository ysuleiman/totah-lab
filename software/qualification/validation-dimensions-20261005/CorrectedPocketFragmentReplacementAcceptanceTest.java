package totah.lab.athena.design.backend.ocl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.generation.*;
import totah.lab.athena.fragment.*;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.*;
import totah.lab.gaia.structure.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.design.generation.MolecularDesignTree.*;
import static totah.lab.athena.design.generation.MolecularDesignGraphGenerator.*;
import static totah.lab.athena.fragment.FragmentReplacementBrief.*;
import static totah.lab.athena.fragment.PocketFragmentReplacement.*;

/** Synthetic coordinates only; real OCL chemistry, graph execution and journal. */
class CorrectedPocketFragmentReplacementAcceptanceTest {
    @TempDir Path temporary;
    private final OclMolecularBackend backend = OclMolecularBackend.forChemicalStateValidation();
    private static Reference ref(String s) { return new Reference(s, "synthetic-v1"); }
    private static Provenance context() {
        return new Provenance(ref("hypothesis"), List.of(ref("synthetic-evidence")), List.of(), ref("supplied-rule"),
                List.of(ref("supplied-fragment-resource")), "test geometry, not activity", List.of(), false);
    }
    private static MolecularGraph.Atom atom(String id, String element, double x, double y, double z) {
        return new MolecularGraph.Atom(id, element, null, 0, 0, false, "UNSPECIFIED", new MolecularGraph.Coordinates(x,y,z), Map.of());
    }
    private static MolecularGraph.Bond bond(String id, String a, String b) {
        return new MolecularGraph.Bond(id,a,b,MolecularGraph.BondOrder.SINGLE,false,"UNSPECIFIED",Map.of());
    }
    private static MolecularGraph parent() {
        return new MolecularGraph(List.of(atom("a","C",-1.5,0,0),atom("b","C",0,0,0),atom("old","S",1.8,0,0)),
                List.of(bond("ab","a","b"),bond("bo","b","old")),Map.of("source","synthetic"));
    }
    private static Frame frame() { return new Frame(new Point3D(0,0,0),new Vector3D(1,0,0),new Vector3D(0,1,0)); }
    private static Candidate candidate() {
        var fragment = new MolecularGraph(List.of(atom("f","C",0,0,0),atom("g","O",0.8,1.2,0)),List.of(bond("fg","f","g")),Map.of());
        var edit = new GraphEdit("supplied", "end", GraphEdit.Type.SUBSTITUENT_REPLACEMENT, Set.of("old"),Set.of(),"b",fragment,null,
                MolecularGraph.BondOrder.SINGLE, Map.of("fragmentAnchorAtomId","f"));
        var auth = new GraphEditTransactionEngine.Authorization("end",Set.of(GraphEdit.Type.SUBSTITUENT_REPLACEMENT),Set.of("old","b"),Set.of("a"),Set.of("ab"));
        return new Candidate(new AuthorizedEdit(edit,auth,0,context()),frame());
    }
    private static Pocket pocket(String id, double x, double y, double z) {
        return new Pocket(ref(id), new Structure(List.of(new Chain("P", List.of(new Residue("ALA",1,
                List.of(Atom.builder().name("CA").element(Element.C).position(new Point3D(x,y,z)).build())))))));
    }
    private static FragmentReplacementBrief brief(List<Double> angles, List<DistanceObjective> objectives) {
        return new FragmentReplacementBrief(ref("brief"),Set.of("old"),"b",Set.of("a","b"),CoordinatePolicy.FIXED,Set.of("a","b"),0,
                frame(),1.5,0.05,angles,0.7,objectives);
    }
    private static DistanceObjective objective(boolean hard, String ligand, double min, double max) {
        return new DistanceObjective(ref("distance"),ref("explicit-objective-evidence"),"synthetic interval only",Set.of(ligand),
                Set.of(new AtomReference("P",1,' ',"CA")),min,max,hard);
    }
    private PocketFragmentReplacement adapter() { return new PocketFragmentReplacement(new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(),backend,backend)); }
    private Result run(FragmentReplacementBrief b, List<Candidate> c, List<Pocket> p, Path journal) throws Exception {
        try (var sink = new Journal(journal)) {
            return adapter().execute(parent(),context(),b,c,p,new MolecularSanitizer.SanitizationPolicy(Set.of(),true),sink);
        }
    }
    private Result run(FragmentReplacementBrief b, List<Pocket> pockets) throws Exception {
        return run(b,List.of(candidate()),pockets,temporary.resolve(UUID.randomUUID()+".jsonl"));
    }

    @Test void placementUsesExistingExecutorPreservesChemistryAndCompleteJournal() throws Exception {
        var file = temporary.resolve("trace.jsonl");
        var result = run(brief(List.of(0.0,180.0),List.of()),List.of(candidate()),List.of(pocket("far",10,0,0)),file);
        assertEquals(List.of(Outcome.ACCEPTED,Outcome.DEDUPLICATED),result.tree().attempts().stream().map(Attempt::outcome).toList());
        assertEquals(3,result.tree().states().size());
        for (var attempt : result.tree().attempts()) {
            assertEquals(parent(),attempt.parent());
            assertEquals(parent().atom("a"),attempt.resultingProduct().atom("a"));
            assertEquals(parent().atom("b"),attempt.resultingProduct().atom("b"));
            assertEquals(attempt.resultingProduct(),attempt.finalDelta().replay(parent()));
            assertEquals(List.of("a","b","f","g"),attempt.resultingProduct().atoms().stream().map(MolecularGraph.Atom::id).toList());
            assertEquals(1.5,attempt.resultingProduct().atom("f").orElseThrow().coordinates().x(),1e-6);
            assertEquals(ref("hypothesis"),attempt.operation().provenance().hypothesis());
            assertTrue(attempt.operation().provenance().resources().contains(METHOD));
            assertTrue(attempt.operation().provenance().resources().contains(ref("far")));
            assertEquals(candidate().operation().authorization(),attempt.operation().authorization());
            assertTrue(attempt.geometryEvidence().stream().anyMatch(s -> s.startsWith("binding-affinity:NOT_EVALUATED")));
        }
        var a = result.tree().attempts().getFirst().resultingProduct().atom("g").orElseThrow().coordinates();
        var b = result.tree().attempts().getLast().resultingProduct().atom("g").orElseThrow().coordinates();
        assertEquals(1.2,a.y(),1e-6); assertEquals(-1.2,b.y(),1e-6);
        assertNotNull(result.tree().attempts().getLast().representativeMapping());
        var json = new ObjectMapper(); int receipts = 0;
        for (String line : Files.readAllLines(file)) {
            var node = json.readTree(line);
            if (node.path("event").asText().equals("receipt")) {
                var restored = json.treeToValue(node.path("data"),Attempt.class);
                assertEquals(result.tree().attempts().get(receipts++),restored);
                var request = json.readTree(restored.operation().edit().parameters().get("placement.request"));
                assertEquals("supplied",request.path("originalOperation").path("edit").path("editId").asText());
                assertEquals(10,request.path("pocket").path("atoms").get(0).path("position").path("x").asDouble());
                assertEquals(brief(List.of(0.0,180.0),List.of()),json.treeToValue(request.path("brief"),FragmentReplacementBrief.class));
                assertEquals(candidate().operation(),json.treeToValue(request.path("originalOperation"),AuthorizedEdit.class));
                assertEquals(frame(),json.treeToValue(request.path("sourceFrame"),Frame.class));
            }
        }
        assertEquals(2,receipts);
        assertThrows(UnsupportedOperationException.class,() -> result.assessments().clear());
    }

    @Test void independentPocketsAndReorderedInputsReplayDeterministically() throws Exception {
        var b = brief(List.of(180.0,0.0),List.of());
        var p = List.of(pocket("z",10,0,0),pocket("a",12,0,0));
        var one = run(b,List.of(candidate()),p,temporary.resolve("one.jsonl"));
        var two = run(b,List.of(candidate()),p.reversed(),temporary.resolve("two.jsonl"));
        assertEquals(one,two);
        assertEquals(Files.readString(temporary.resolve("one.jsonl")),Files.readString(temporary.resolve("two.jsonl")));
        assertEquals(4,one.tree().attempts().size()); assertEquals(5,one.tree().states().size());
    }

    @Test void translatedRotatedSourceFramePreservesSuppliedInternalGeometry() throws Exception {
        var c = candidate(); var e = c.operation().edit();
        var transformed = new MolecularGraph(List.of(atom("f","C",4,5,6),atom("g","O",2.8,5.8,6)),e.fragment().bonds(),e.fragment().properties());
        var edit = new GraphEdit(e.editId(),e.editableVectorId(),e.type(),e.affectedAtomIds(),e.affectedBondIds(),e.anchorAtomId(),transformed,null,e.replacementBondOrder(),e.parameters());
        var source = new Frame(new Point3D(4,5,6),new Vector3D(0,1,0),new Vector3D(-1,0,0));
        var result = run(brief(List.of(0.0),List.of()),List.of(new Candidate(new AuthorizedEdit(edit,c.operation().authorization(),0,context()),source)),
                List.of(pocket("far",10,0,0)),temporary.resolve("rotated.jsonl"));
        var attempt = result.tree().attempts().getFirst();
        assertEquals(Outcome.ACCEPTED,attempt.outcome(),attempt.reason());
        var p = attempt.resultingProduct().atom("g").orElseThrow().coordinates();
        assertEquals(2.3,p.x(),1e-6); assertEquals(1.2,p.y(),1e-6); assertEquals(0,p.z(),1e-6);
    }

    @Test void aClashingOrientationIsReceiptedWithoutDiscardingOtherOrientation() throws Exception {
        var result = run(brief(List.of(0.0,180.0),List.of()),List.of(pocket("wall",2.3,3.0,0)));
        assertEquals(List.of(Outcome.GEOMETRY_FAILURE,Outcome.ACCEPTED),result.tree().attempts().stream().map(Attempt::outcome).toList());
        var rejected = result.tree().attempts().getFirst();
        assertNotNull(rejected.graphReceipt()); assertNotNull(rejected.finalDelta());
        assertTrue(rejected.geometryEvidence().stream().anyMatch(s -> s.startsWith("pocket-clashes:FAIL")));
    }

    @Test void unmetHardObjectiveRejectsButSoftObjectiveReportsFailureWithoutRanking() throws Exception {
        var p = List.of(pocket("far",10,0,0));
        var hard = run(brief(List.of(0.0),List.of(objective(true,"g",2,3))),p);
        var soft = run(brief(List.of(0.0),List.of(objective(false,"g",2,3))),p);
        assertEquals(Outcome.GEOMETRY_FAILURE,hard.tree().attempts().getFirst().outcome());
        assertEquals(Outcome.ACCEPTED,soft.tree().attempts().getFirst().outcome());
        assertTrue(soft.assessments().values().iterator().next().stream().anyMatch(a -> a.status()==Status.FAIL && !a.hard()));
        assertTrue(soft.tree().attempts().getFirst().operation().provenance().evidence().contains(ref("explicit-objective-evidence")));
    }

    @Test void missingSoftSelectionFailsClosedRatherThanInventingEvaluation() throws Exception {
        var result = run(brief(List.of(0.0),List.of(objective(false,"unknown",0,20))),List.of(pocket("far",10,0,0)));
        assertEquals(Outcome.GEOMETRY_FAILURE,result.tree().attempts().getFirst().outcome(),result.tree().attempts().getFirst().reason());
        assertTrue(result.tree().attempts().getFirst().geometryEvidence().stream().anyMatch(s -> s.contains("missing selection")));
    }

    @Test void unsupportedInheritedConstraintIsNotSilentlyMarkedEvaluated() throws Exception {
        var c = candidate(); var p = context();
        var changed = new Provenance(p.hypothesis(),p.evidence(),p.retainedAnchors(),p.rule(),p.resources(),p.intendedEffect(),List.of(ref("unknown-constraint")),false);
        var result = run(brief(List.of(0.0),List.of()),List.of(new Candidate(new AuthorizedEdit(c.operation().edit(),c.operation().authorization(),0,changed),frame())),
                List.of(pocket("far",10,0,0)),temporary.resolve("unknown.jsonl"));
        assertEquals(Outcome.GEOMETRY_FAILURE,result.tree().attempts().getFirst().outcome(),result.tree().attempts().getFirst().reason());
    }

    @Test void authorizationCannotBeBypassedByPlacement() throws Exception {
        var c = candidate();
        var denied = new GraphEditTransactionEngine.Authorization("end",Set.of(GraphEdit.Type.SUBSTITUENT_REPLACEMENT),Set.of("old"),Set.of("b"),Set.of());
        var result = run(brief(List.of(0.0),List.of()),List.of(new Candidate(new AuthorizedEdit(c.operation().edit(),denied,0,context()),frame())),
                List.of(pocket("far",10,0,0)),temporary.resolve("denied.jsonl"));
        assertEquals(Outcome.AUTHORIZATION_REJECTED,result.tree().attempts().getFirst().outcome());
        assertEquals(Status.NOT_EVALUATED,result.assessments().values().iterator().next().getFirst().status());
    }

    @Test void invalidFrameCandidateIsRejectedWithOriginalSource() throws Exception {
        var bad = new Candidate(candidate().operation(),new Frame(new Point3D(9,0,0),new Vector3D(1,0,0),new Vector3D(0,1,0)));
        var result = run(brief(List.of(0.0),List.of()),List.of(bad),List.of(pocket("far",10,0,0)),temporary.resolve("frame.jsonl"));
        var attempt = result.tree().attempts().getFirst();
        assertNotEquals(Outcome.ACCEPTED,attempt.outcome());
        assertNotEquals(Outcome.DEDUPLICATED,attempt.outcome());
        assertTrue(attempt.operation().edit().parameters().get("placement.failure").contains("source frame"));
    }

    @Test void intraligandOneFourClashIsNotHiddenByResidueOrBondAngleExclusions() throws Exception {
        var c = candidate(); var e = c.operation().edit();
        var f = new MolecularGraph(List.of(atom("f","C",0,0,0),atom("g","O",-2.5,0.8,0)),List.of(bond("fg","f","g")),Map.of());
        var changed = new GraphEdit(e.editId(),e.editableVectorId(),e.type(),e.affectedAtomIds(),e.affectedBondIds(),e.anchorAtomId(),f,null,e.replacementBondOrder(),e.parameters());
        var result = run(brief(List.of(0.0),List.of()),List.of(new Candidate(new AuthorizedEdit(changed,c.operation().authorization(),0,context()),frame())),
                List.of(pocket("far",10,0,0)),temporary.resolve("internal.jsonl"));
        assertEquals(Outcome.GEOMETRY_FAILURE,result.tree().attempts().getFirst().outcome(),result.tree().attempts().getFirst().reason());
        assertTrue(result.tree().attempts().getFirst().geometryEvidence().stream().anyMatch(s -> s.startsWith("intraligand-clashes:FAIL")));
    }

    @Test void journalFailurePropagatesInsteadOfReportingCompletedGeneration() {
        assertThrows(IOException.class,() -> adapter().execute(parent(),context(),brief(List.of(0.0),List.of()),List.of(candidate()),List.of(pocket("far",10,0,0)),
                new MolecularSanitizer.SanitizationPolicy(Set.of(),true),a -> { throw new IOException("disk unavailable"); }));
    }

    @Test void chemistryOnlyFixedAndBoundedCoordinatePoliciesAreIndependent() throws Exception {
        // Deliberately perturb a retained coordinate AFTER real OCL validation to exercise the
        // existing executor's permitted coordinate-only backend path. No production relaxer added.
        MolecularSanitizer moving = (graph, policy) -> {
            var checked = backend.sanitize(graph,policy);
            if (graph.atom("f").isEmpty()) return checked;
            var atoms = graph.atoms().stream().map(a -> a.id().equals("a")
                    ? atom("a","C",-1.5,0.1,0) : a).toList();
            return new MolecularSanitizer.Result(new MolecularGraph(atoms,graph.bonds(),graph.properties()),checked.valid(),checked.evidence());
        };
        var adapter = new PocketFragmentReplacement(new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(),moving,backend));
        // The graph engine's protected-atom policy itself locks coordinates; this fixture uses
        // only the new explicit chemical lock so that the two policies can be tested separately.
        var c = candidate();
        var auth = new GraphEditTransactionEngine.Authorization("end",Set.of(GraphEdit.Type.SUBSTITUENT_REPLACEMENT),Set.of("old","b"),Set.of(),Set.of("ab"));
        var candidate = new Candidate(new AuthorizedEdit(c.operation().edit(),auth,0,context()),frame());
        for (var coordinatePolicy : CoordinatePolicy.values()) {
            var b = new FragmentReplacementBrief(ref("brief"),Set.of("old"),"b",Set.of("a","b"),coordinatePolicy,
                    coordinatePolicy == CoordinatePolicy.CHEMISTRY_ONLY ? Set.of() : Set.of("a","b"),0.2,
                    frame(),1.5,0.05,List.of(0.0),0.7,List.of());
            var result = adapter.execute(parent(),context(),b,List.of(candidate),List.of(pocket("far",10,0,0)),
                    new MolecularSanitizer.SanitizationPolicy(Set.of(),true), a -> {});
            var attempt = result.tree().attempts().getFirst();
            assertEquals(coordinatePolicy == CoordinatePolicy.FIXED ? Outcome.GEOMETRY_FAILURE : Outcome.ACCEPTED,attempt.outcome(),attempt.reason());
            assertFalse(attempt.validationDelta().chemicalGraphChanged());
            assertTrue(attempt.geometryEvidence().stream().anyMatch(s -> s.startsWith("chemical-preservation/a:PASS")));
        }
    }

    @Test void middleRemovalCannotSneakThroughTheSingleAttachmentContract() throws Exception {
        var c = candidate(); var e = c.operation().edit();
        var middle = new GraphEdit(e.editId(),e.editableVectorId(),e.type(),Set.of("b"),Set.of(),"a",e.fragment(),null,e.replacementBondOrder(),e.parameters());
        var auth = new GraphEditTransactionEngine.Authorization("end",Set.of(GraphEdit.Type.SUBSTITUENT_REPLACEMENT),Set.of("a","b"),Set.of(),Set.of());
        var b = new FragmentReplacementBrief(ref("brief"),Set.of("b"),"a",Set.of("a"),CoordinatePolicy.CHEMISTRY_ONLY,Set.of(),0,
                new Frame(new Point3D(-1.5,0,0),new Vector3D(1,0,0),new Vector3D(0,1,0)),1.5,0.05,List.of(0.0),0.7,List.of());
        var result = run(b,List.of(new Candidate(new AuthorizedEdit(middle,auth,0,context()),frame())),List.of(pocket("far",10,0,0)),temporary.resolve("middle.jsonl"));
        var attempt = result.tree().attempts().getFirst();
        assertEquals(Outcome.INVALID_TOPOLOGY,attempt.outcome());
        assertTrue(attempt.operation().edit().parameters().get("placement.failure").contains("one attachment boundary"));
    }

    @Test void pocketWithUnknownElementCannotSilentlyPassClashEvaluation() {
        var unknown = Atom.builder().name("X").position(new Point3D(0,0,0)).build();
        var structure = new Structure(List.of(new Chain("P",List.of(new Residue("UNK",1,List.of(unknown))))));
        assertThrows(IllegalArgumentException.class,() -> new Pocket(ref("unknown"),structure));
    }

    @Test void removedAtomIdCannotBeReusedToFabricateRetainedLineage() throws Exception {
        var c = candidate(); var e = c.operation().edit();
        var fragment = new MolecularGraph(List.of(atom("old","C",0,0,0),atom("g","O",0.8,1.2,0)),List.of(bond("fg","old","g")),Map.of());
        var edit = new GraphEdit(e.editId(),e.editableVectorId(),e.type(),e.affectedAtomIds(),e.affectedBondIds(),e.anchorAtomId(),fragment,null,e.replacementBondOrder(),Map.of("fragmentAnchorAtomId","old"));
        var result = run(brief(List.of(0.0),List.of()),List.of(new Candidate(new AuthorizedEdit(edit,c.operation().authorization(),0,context()),frame())),
                List.of(pocket("far",10,0,0)),temporary.resolve("reused-id.jsonl"));
        var attempt = result.tree().attempts().getFirst();
        assertNotEquals(Outcome.ACCEPTED,attempt.outcome());
        assertNotEquals(Outcome.DEDUPLICATED,attempt.outcome());
        assertTrue(attempt.operation().edit().parameters().get("placement.failure").contains("reuse parent atom IDs"));
        assertEquals(1,result.tree().states().size());
    }

    @Test void invalidBriefAndOversizedBatchAreRejectedBeforeExecution() {
        assertThrows(IllegalArgumentException.class,() -> new Frame(new Point3D(0,0,0),new Vector3D(1,0,0),new Vector3D(2,0,0)));
        assertThrows(IllegalArgumentException.class,() -> brief(List.of(0.0,0.0),List.of()));
        assertThrows(IllegalArgumentException.class,() -> adapter().execute(parent(),context(),brief(List.of(0.0),List.of()),
                Collections.nCopies(257,candidate()),List.of(pocket("far",10,0,0)),new MolecularSanitizer.SanitizationPolicy(Set.of(),true),a -> fail("must not execute")));
    }
}
