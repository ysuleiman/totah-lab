package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import totah.lab.athena.system.SystemStateView;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.EvidenceExchange;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.envelope;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.system;
import static totah.lab.daedalus.system.ContinuousGeometryAcceptanceTest.*;

/** Synthetic intramolecular attribution witness, not an H-bond or favorable geometry claim. */
class RoleGeometryAttributionTest {
    static JsonNode exercise(boolean reverse) throws Exception {
        var fixture=ChemicalRoleAcceptanceTest.chemical("acetamide");
        var state=system(List.of(fixture.graph()),true,false);
        var reports=RoleCoverageClosureTest.roles(fixture,false);
        var donor=reports.get("DONOR.AMIDE_NH2");
        var acceptor=reports.get("ACCEPTOR.CARBONYL_O");
        var d=donor.get("occurrences").get(0);var a=acceptor.get("occurrences").get(0);
        var donorId=d.get("roleCorrespondenceAlternatives").get(0).get("donor").get(0).asText();
        var acceptorId=a.get("memberAtomIds").get(0).asText();
        // Resolve against the existing stable structure correspondence, never positional numbering.
        var component=state.components().getFirst();
        assertEquals(1,component.correspondenceAlternatives().size()); // This fixture is unambiguous.
        var mapping=component.correspondenceAlternatives().getFirst();
        var dr=mapping.get(donorId);var ar=mapping.get(acceptorId);
        assertNotNull(dr);assertNotNull(ar);
        var plan=plan(state);group(plan,"donor",dr);group(plan,"acceptor",ar);
        var de=envelope(state,"athena:group-identities",donor);
        var ae=envelope(state,"athena:group-identities",acceptor);
        ((ObjectNode)plan.get("groups").get(0)).set("sourceReferences",node(List.of(de.reference())));
        ((ObjectNode)plan.get("groups").get(1)).set("sourceReferences",node(List.of(ae.reference())));
        op(plan,"VECTOR",reverse?ar:dr,reverse?dr:ar);
        matrix(plan,"PAIR_MATRIX",reverse?"acceptor":"donor",reverse?"donor":"acceptor");
        var report=run(state,plan);
        assertEquals(plan,report.get("plan"));
        var exchange=new EvidenceExchange();
        for(var input:List.of(de,ae,envelope(state,"athena:rule-measurements",report))) {
            assertEquals(input,exchange.decodeRecord(exchange.encodeRecord(input)));
        }
        assertTrue(d.get("explicitHydrogenAtomIds").isEmpty()); // No invented D-H orientation.
        assertEquals("a3",donorId);assertEquals("a2",acceptorId);
        return report;
    }
    @Test void rolesSourceReferencesAndGeometrySurviveExchange()throws Exception {exercise(false);}
    @Test void reversingPartnersReversesVectorWithoutInventingNewMechanism()throws Exception {
        var forward=exercise(false);var reverse=exercise(true);
        assertEquals(value(forward,0,"lengthAngstrom"),value(reverse,0,"lengthAngstrom"));
        for(int i=0;i<3;i++)assertEquals(-Double.parseDouble(value(forward,0,"unitVector").get(i).asText()),Double.parseDouble(value(reverse,0,"unitVector").get(i).asText()),1e-12);
        assertEquals(value(forward,1,"pairs").get(0).get("distanceAngstrom"),value(reverse,1,"pairs").get(0).get("distanceAngstrom"));
    }
    public static void main(String[] args)throws Exception {
        Files.write(Path.of(args[0]),SystemStateView.bytes(Map.of("forward",exercise(false),"reverse",exercise(true))));
    }
}
