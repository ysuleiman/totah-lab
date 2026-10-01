package totah.lab.mettl7.evidence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.design.grammar.DesignGrammarJsonCodec;
import totah.lab.athena.design.reasoning.DesignKnowledge;
import totah.lab.daedalus.PipelineFactory;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.design.generation.MolecularDesignTree.*;
import static totah.lab.athena.design.reasoning.DesignKnowledge.*;
import static totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.*;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7EvidenceCatalogTest {
    private final Mettl7EvidenceCatalog catalog = new Mettl7EvidenceCatalog();

    @Test
    void preservesOpposedFunctionalAnchorsWithoutChangingTriageRules() {
        assertThat(catalog.find("2,3-dichloro-alpha-methylbenzylamine (DCMB)"))
                .get().extracting(Mettl7CompoundEvidence::currentClassification)
                .isEqualTo("EXPERIMENTALLY_A_SELECTIVE_INHIBITOR");
        assertThat(catalog.find("netarsudil")).get().satisfies(evidence -> {
            assertThat(evidence.currentClassification()).isEqualTo("EXPERIMENTALLY_B_SELECTIVE_INHIBITOR");
            assertThat(evidence.supersededClassifications()).containsExactly("B_COMPATIBLE_ONLY");
            assertThat(evidence.experimentalB()).contains("20 uM");
        });
    }

    @Test
    void preservesSharedProductiveSubstratesAndExactAssayConcentrations() {
        assertThat(catalog.find("7alpha-thiospironolactone (TSL)"))
                .get().extracting(Mettl7CompoundEvidence::experimentalA)
                .asString().contains("125 uM");
        assertThat(catalog.find("captopril"))
                .get().extracting(Mettl7CompoundEvidence::experimentalB)
                .asString().contains("500 uM");
    }

    @Test
    void preservesMutationNetworkRatherThanMasterSwitchClaim() {
        assertThat(catalog.find("2,3-dichloro-alpha-methylbenzylamine (DCMB)"))
                .get().extracting(Mettl7CompoundEvidence::mutationalEvidence)
                .asString().contains("Y47S", "F199G", "F43L", "S47Y", "does not confer");
    }

    @TempDir Path temporary;
    private static Reference ref(String id) { return new Reference(id, "1"); }
    private DesignState existingState() throws Exception {
        String existing=Files.readString(Path.of(System.getProperty("basedir"), "src/test/resources/reasoning/CAPTOPRIL_RSH.sdf"));
        var molecule=new com.actelion.research.chem.StereoMolecule();
        assertTrue(new com.actelion.research.chem.MolfileParser().parse(molecule,existing));
        // This evidence concerns chemical subject, not the prepared 3D state. OCL's existing
        // SMILES writer defines the reviewer-supplied chemical state; no atom/coordinate transfer is asserted.
        String smiles=new com.actelion.research.chem.IsomericSmilesCreator(molecule).getSmiles();
        var graph = new OclMolecularBackend().decodeStructure("SMILES",smiles);
        return new DesignState("existing-captopril", "existing-captopril", null, graph,
                new Provenance(ref("catalog-attribution"), List.of(), List.of(), ref("existing-prepared-state"),
                        List.of(), "Reviewer chemical-subject attribution derived from existing source; no coordinate/atom lineage claim", List.of(), false), 0);
    }
    private SourceReview review(DesignState state) {
        return new SourceReview(ref("catalog-review"), ref("test-reviewer"), ref("catalog-scope-review"),
                "2026-10-01T16:00:00Z", true, "Review of existing catalog only; no assay rerun",
                "captopril", state, catalog.sharedProductSemantics(), ScientificStatus.SCREENING_ONLY,
                UncertaintyKind.QUALITATIVE, "Replicate/error estimates unavailable", Mettl7EvidenceCatalog.PRODUCT_REPORT_CLAIM,
                "Chemical-subject association is reviewer supplied; raw chromatograms and assay temperature/duration unavailable");
    }
    @Test void existingExperimentalCatalogReachesGovernedReasoningWithItsExactLimits() throws Exception {
        var state=existingState();var interpreted=catalog.reviewSharedProductReport(ref("catalog-observation"),review(state));
        var e=interpreted.evidence();
        assertEquals(EvidenceKind.EXPERIMENTAL,e.kind());assertEquals(ScientificStatus.SCREENING_ONLY,e.qualification().status());
        assertTrue(e.limitations().contains("59cf114c47fc1d7f7f43b45d25c1b8a0deb7209b5ed5ee027df054d6fe3daf59"));
        assertTrue(e.limitations().contains("not raw instrument data"));assertTrue(e.qualification().uncertainty().contains("unavailable"));
        var source=new ObjectMapper().readValue(interpreted.rawJson(),Mettl7CompoundEvidence.class);
        assertEquals(catalog.find("captopril").orElseThrow(),source);
        var requirement=new Requirement(catalog.sharedProductSemantics(),Set.of(EvidenceKind.EXPERIMENTAL),Set.of(ScientificStatus.SCREENING_ONLY));
        assertTrue(requirement.accepts(e));
        assertFalse(new Requirement(catalog.sharedProductSemantics(),Set.of(EvidenceKind.COMPUTATIONAL),Set.of(ScientificStatus.SCREENING_ONLY)).accepts(e));
        var sem=catalog.sharedProductSemantics();
        assertFalse(new Requirement(new Semantics(sem.method(),ref("direct-binding"),sem.system(),sem.conditions(),sem.unit(),sem.valueKind()),
                Set.of(EvidenceKind.EXPERIMENTAL),Set.of(ScientificStatus.SCREENING_ONLY)).accepts(e));
        var criterion=new Criterion("reported-product", "Assay catalog report only", false, requirement,
                new Value(null,null,"reported-product-in-both"),interpreted.policy(),e.source());
        String site=state.graph().atoms().stream().filter(a->a.element().equals("S")).findFirst().orElseThrow().id();
        // Disabled mechanical rule demonstrates evidence consumption without proposing any compound.
        var rule=new ReplacementRule(ref("disabled-rule"),"S","O",false,ref("test-policy"),"No candidate design authorized");
        var h=new Hypothesis(ref("catalog-question"),null,"Retain catalog assay scope",e.context(),List.of(e.reference()),List.of(),state,
                new SiteRequirement("recorded-site",Set.of(site)),List.of(),rule.reference(),List.of(),"No new molecular design",
                null,"No extrapolation to binding or selectivity",List.of(criterion),List.of(new EvidenceRequirement(e.reference(),requirement)),null,List.of());
        var knowledge=new DesignKnowledge(QUALIFIED_SCHEMA,ref("catalog-knowledge"),List.of(e),List.of(h),List.of(rule),List.of());
        Path snapshot,journal;
        var factory=new PipelineFactory(temporary);
        try(var run=factory.openDesignReasoningRun(new OclMolecularBackend())) {
            run.registerInterpretation(interpreted);snapshot=run.saveKnowledge("knowledge.json",knowledge);
            assertTrue(run.plan(snapshot,List.of(h.reference()),Set.of(EvidenceKind.EXPERIMENTAL),state).isEmpty());
            journal=run.runDirectory().resolve("reasoning.jsonl");
            assertEquals(interpreted,new DesignGrammarJsonCodec().readInterpretation(run.registryDirectory(),e.reference()));
        }
        try(var replay=factory.openDesignReasoningRun(new OclMolecularBackend())) {assertEquals(knowledge,replay.load(snapshot));}
        String lines=Files.readString(journal);
        assertTrue(lines.contains("INELIGIBLE_TRANSFORMATION"));assertFalse(lines.contains("NON_COMPARABLE_EVIDENCE"));
        assertTrue(lines.indexOf("interpretation")<lines.indexOf("planning-decision"));
        assertTrue(lines.contains("Mettl7CompoundEvidence"));assertTrue(lines.contains(Mettl7EvidenceCatalog.PRODUCT_REPORT_POLICY));
        assertTrue(lines.contains("500 uM"));
    }
    @Test void downstreamReviewCannotStrengthenClaimOrChangeAssayConditions() throws Exception {
        var mapper=new ObjectMapper();var reviewed=review(existingState());
        for(String field:List.of("claimBoundary","scientificSubject","approve","conditions")) {
            com.fasterxml.jackson.databind.node.ObjectNode node=mapper.valueToTree(reviewed);
            if(field.equals("approve"))node.put(field,false);
            else if(field.equals("conditions"))((com.fasterxml.jackson.databind.node.ObjectNode)node.path("semantics")).putObject("conditions");
            else node.put(field,"invented stronger claim");
            var changed=mapper.treeToValue(node,SourceReview.class);
            assertThrows(IllegalArgumentException.class,()->catalog.reviewSharedProductReport(ref("bad"),changed));
        }
    }
}
