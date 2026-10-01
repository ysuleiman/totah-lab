package totah.lab.athena.design.reasoning;

import org.junit.jupiter.api.Test;
import totah.lab.aether.matrix.FragmentInteractionCalculator;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.generation.MolecularDesignTree.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.design.reasoning.DesignKnowledge.*;

/** Receipt fixtures test adapters without running chemistry or scientific campaigns. */
class ReviewedEvidenceAdaptersTest {
    private static Reference ref(String id) { return new Reference(id,"1"); }
    private static DesignState subject() {
        var graph=new MolecularGraph(List.of(new MolecularGraph.Atom("c","C",null,0,0,false,"UNSPECIFIED",null,Map.of())),List.of(),Map.of());
        return new DesignState("s","n",null,graph,new Provenance(ref("root"),List.of(),List.of(),ref("input"),List.of(),"synthetic",List.of(),false),0);
    }
    private static Evidence chemistry(boolean valid, List<BackendEvidence.GraphChange> changes) {
        var s=subject();
        return ReviewedEvidenceAdapters.chemistryValidation(ref("e"),ref("receipt"),ref("review"),s,ref("system"),"synthetic",Map.of(),
                new MolecularSanitizer.Result(s.graph(),valid,new BackendEvidence("test-backend","1","sanitize",Map.of("c","c"),changes,List.of("receipt detail"))));
    }
    @Test void sanitizerAdapterNeverClaimsBiologicalSuccessOrDecisionGradeEvidence() {
        var e=chemistry(true,List.of());
        assertEquals(ScientificStatus.SCREENING_ONLY,e.qualification().status());
        assertEquals("toolkit-chemical-validation",e.qualification().semantics().endpoint().id());
        assertEquals("valid",e.value().category()); assertTrue(e.limitations().contains("receipt detail"));
        assertEquals(ref("receipt"),e.source());
        assertFalse(new Requirement(e.qualification().semantics(),Set.of(EvidenceKind.COMPUTATIONAL),Set.of(ScientificStatus.VALIDATED_REFERENCE)).accepts(e));
    }
    @Test void sanitizerInvalidityOrUnauthorizedNormalizationCannotProduceSupport() {
        assertNull(chemistry(false,List.of()).value());
        var e=chemistry(true,List.of(new BackendEvidence.GraphChange("charge","0","1",BackendEvidence.Disposition.UNAUTHORIZED_MEANINGFUL_CHANGE)));
        assertEquals(ScientificStatus.UNAVAILABLE,e.qualification().status()); assertNull(e.value());
    }
    @Test void sanitizerReceiptMustMatchReviewedState() {
        assertThrows(IllegalArgumentException.class,()->ReviewedEvidenceAdapters.chemistryValidation(ref("e"),ref("source"),ref("review"),subject(),ref("system"),"synthetic",Map.of(),
                new MolecularSanitizer.Result(new MolecularGraph(List.of(),List.of(),Map.of()),true,new BackendEvidence("test","1","sanitize",Map.of(),List.of(),List.of()))));
    }
    private static FragmentInteractionCalculator.Result result(boolean valid,String hash) {
        var components=new ArrayList<FragmentInteractionCalculator.Component>();
        for(String method:List.of("RHF","PBE")) for(String role:List.of("AB","A_GHOST_B","B_GHOST_A"))
            components.add(new FragmentInteractionCalculator.Component(method,role,valid?"CONVERGED":"NONCONVERGED",valid?OptionalDouble.of(-1):OptionalDouble.empty(),
                    "component-receipt","system-hash","requested","canonical",false,true,"no permutation",List.of(),valid?"":"not converged"));
        return new FragmentInteractionCalculator.Result(OptionalDouble.of(-.1),OptionalDouble.of(-.1),OptionalDouble.of(-.01),valid?OptionalDouble.of(-.11):OptionalDouble.empty(),
                components,List.of("d3-receipt", "d3-a", "d3-b"),valid?List.of():List.of("SCF failure"),hash);
    }
    private static Evidence energy(boolean valid,String hash) {
        return ReviewedEvidenceAdapters.fragmentEnergy(ref("e"),ref("review"),subject(),ref("system"),"synthetic",Map.of("geometry","frozen"),result(valid,hash));
    }
    @Test void aetherScreeningProtocolEndpointUnitsAndSourceLimitationsSurvive() {
        var e=energy(true,"receipt-1");
        assertEquals(ScientificStatus.SCREENING_ONLY,e.qualification().status()); assertEquals(-.11,e.value().lower());
        assertEquals("hartree",e.qualification().semantics().unit()); assertEquals("receipt-1",e.source().id());
        assertTrue(e.limitations().contains("component-receipt")); assertTrue(e.limitations().contains("d3-receipt"));
        assertTrue(e.qualification().uncertainty().contains("not zero physical uncertainty"));
        assertEquals(FragmentInteractionCalculator.PROTOCOL,e.qualification().semantics().method().parameters().get("protocol"));
        assertEquals(e.qualification().semantics(),energy(true,"receipt-2").qualification().semantics());
    }
    @Test void aetherFailureIsUnavailableNotContradictoryEnergy() {
        var e=energy(false,"failed"); assertEquals(ScientificStatus.UNAVAILABLE,e.qualification().status()); assertNull(e.value());
        assertTrue(e.limitations().contains("SCF failure"));
        assertFalse(new Requirement(e.qualification().semantics(),Set.of(EvidenceKind.COMPUTATIONAL),Set.of(ScientificStatus.SCREENING_ONLY)).accepts(e));
    }

    @Test void partialOrDuplicateComponentReceiptsCannotBecomeUsableEnergy() {
        var full=result(true,"partial");
        var partial=new FragmentInteractionCalculator.Result(full.rhfCpHartree(),full.pbeCpHartree(),full.d3DeltaHartree(),full.pbeD3CpHartree(),
                Collections.nCopies(6,full.components().getFirst()),full.dispersionReceipts(),List.of(),full.receiptHash());
        var adapted=ReviewedEvidenceAdapters.fragmentEnergy(ref("e"),ref("review"),subject(),ref("system"),"synthetic",Map.of(),partial);
        assertNull(adapted.value()); assertEquals(ScientificStatus.UNAVAILABLE,adapted.qualification().status());
    }
}
