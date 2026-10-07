package totah.lab.daedalus.system;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.S1NitrogenAcceptanceTest.*;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT;

class S1QualifiedProducerTest {
    @TempDir Path temp;
    static void qualifyScope(Fixture f,Path temp)throws Exception {
        var scopes=f.inputs.stream().filter(e->e.evidenceType().equals("athena:event-source")).toList();
        int scopeIndex=0;for(var scope:scopes){
        var old=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(scope.readPayload());
        var p=B01FunctionalGroupAcceptanceTest.JSON.readTree(old.measurements().get("sourceProtocol"));
        var source=f.inputs.stream().filter(e->e.payloadSha256().equals(p.path("sha256").asText())).findFirst().orElseThrow();
        Path protocol=Path.of("software/modules/daedalus/src/test/resources/i03-s1/synthetic-source-protocol.txt");assertArrayEquals(Files.readAllBytes(protocol),source.readPayload());
        var base=f.manifest;
        var scopeManifest=new RuleManifest("athena-rule/2","ATHENA.I03.SP3_SOURCE_SCOPE","1.0.0","SYNTHETIC_SOURCE_SCOPE",base.family(),base.tier(),"fixture.source-scope","1",SystemGraphCertificate.Status.NOT_EVALUATED,false,List.of(),base.requiredChemistry(),List.of(),base.measurementsProduced(),base.classificationStates(),base.parameters(),List.of(new RuleManifest.Source(protocol.toString(),source.payloadSha256(),"synthetic source protocol only"),new RuleManifest.Source("sha256:"+scope.payloadSha256(),scope.payloadSha256(),"Exact synthetic scope interpretation, including original source pins and actual producer identity")),List.of(),List.of("engineering source assertion only"),base.negativeCoverage());
        var q=S1ResearchFixtures.qualify(scopeManifest,f.state,f.selected,temp,"scope-authority-"+scopeIndex++,Map.of(scope.payloadSha256(),scope.readPayload()));
        f.inputs.addAll(q.artifacts());
        }
    }
    @Test void producerAndScopeNeedSeparateQualifications()throws Exception {
        var f=new Fixture("primary");var q=S1ResearchFixtures.qualify(f.manifest,f.state,f.selected,temp,"s1-authority");f.manifest=q.manifest();f.inputs.addAll(q.artifacts());
        assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,f.result().getFirst().status());
        qualifyScope(f,temp);assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,f.result().getFirst().status());
    }
    @Test void scopeAuthorityWithoutProducerStillNotEvaluated()throws Exception {var f=new Fixture("primary");qualifyScope(f,temp);assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,f.result().getFirst().status());}
    @Test void changedSelectionCannotReplayProducerReceipt()throws Exception {var f=new Fixture("primary");var q=S1ResearchFixtures.qualify(f.manifest,f.state,f.selected,temp,"s1-authority");f.manifest=q.manifest();f.inputs.addAll(q.artifacts());f.selected.add(f.state.components().getFirst().correspondenceAlternatives().getFirst().get("a0"));assertThrows(IllegalArgumentException.class,f::result);}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"DOUBLE","TRIPLE"})
    void qualifiedMultipleBondBoundaryIsModelNegativeOnly(String order)throws Exception {
        var g=B01FunctionalGroupAcceptanceTest.fixture("amine");var bond=g.graph().bonds().getFirst();var b=new totah.lab.athena.design.backend.MolecularGraph.Bond(bond.id(),bond.firstAtomId(),bond.secondAtomId(),totah.lab.athena.design.backend.MolecularGraph.BondOrder.valueOf(order),false,bond.stereochemistry(),bond.properties());
        var graph=new totah.lab.athena.design.backend.MolecularGraph(g.graph().atoms(),List.of(b),g.graph().properties());var f=new Fixture("primary",List.of(new B01FunctionalGroupAcceptanceTest.Fixture(graph,Map.of("a0",order.equals("DOUBLE")?2:1,"a1",order.equals("DOUBLE")?1:0))));
        var q=S1ResearchFixtures.qualify(f.manifest,f.state,f.selected,temp,"s1");f.manifest=q.manifest();f.inputs.addAll(q.artifacts());qualifyScope(f,temp);var r=f.result().getFirst();assertEquals("NOT_SP3",r.measurements().get("hybridization"));assertEquals(EvidenceInterpretation.Status.ABSENT_FALSE,r.status());assertTrue(r.reasons().toString().contains("bounded S1 incompatibility"));
    }

}
