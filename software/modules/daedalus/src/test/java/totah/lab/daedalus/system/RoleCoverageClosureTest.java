package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import totah.lab.athena.system.SystemStateView;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.system;

/** P03 reconciliation across separately qualified role reports, not a new perception engine. */
class RoleCoverageClosureTest {
    static Map<String,JsonNode> roles(Fixture f,boolean unknownH)throws Exception {
        var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);
        if(unknownH)c.get("atomState").forEach(a->{((ObjectNode)a).put("hydrogenMode","UNKNOWN");((ObjectNode)a).putNull("implicitHydrogenCount");});
        var out=new TreeMap<String,JsonNode>();byte[] before=SystemStateView.bytes(s.snapshot());
        for(var row:ChemicalRoleAcceptanceTest.cases())out.put(row[0],report(ChemicalRoleAcceptanceTest.candidate(row[0]),s,c));
        assertEquals(17,out.size());assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));return out;
    }
    static Set<String> present(Map<String,JsonNode> reports) {
        var out=new TreeSet<String>();reports.forEach((k,v)->{if(v.get("assessment").asText().equals("SUPPORTED_PRESENT"))out.add(k);});return out;
    }
    @Test void neutralAndChargedAmineAreSeparateRoleStates()throws Exception {
        assertEquals(Set.of("DONOR.AMINE_PRIMARY","ACCEPTOR.AMINE_PRIMARY"),present(roles(ChemicalRoleAcceptanceTest.chemical("methylamine"),false)));
        assertEquals(Set.of("DONOR.AMMONIUM_PRIMARY"),present(roles(ChemicalRoleAcceptanceTest.chemical("methylammonium"),false)));
    }
    @Test void overlappingAmideRolesRetainDifferentExactAtoms()throws Exception {
        var r=roles(ChemicalRoleAcceptanceTest.chemical("acetamide"),false);
        assertEquals(Set.of("DONOR.AMIDE_NH2","ACCEPTOR.CARBONYL_O"),present(r));
        assertEquals("a3",r.get("DONOR.AMIDE_NH2").get("occurrences").get(0).get("memberAtomIds").get(0).asText());
        assertEquals("a2",r.get("ACCEPTOR.CARBONYL_O").get("occurrences").get(0).get("memberAtomIds").get(0).asText());
    }
    @Test void explicitHydrogensDoNotChangeRoleIdentity()throws Exception {
        var f=ChemicalRoleAcceptanceTest.chemical("methylamine");var implicit=roles(f,false);var explicit=roles(explicit(f),false);
        assertEquals(present(implicit),present(explicit));
        assertTrue(implicit.get("DONOR.AMINE_PRIMARY").get("occurrences").get(0).get("explicitHydrogenAtomIds").isEmpty());
        assertFalse(explicit.get("DONOR.AMINE_PRIMARY").get("occurrences").get(0).get("explicitHydrogenAtomIds").isEmpty());
    }
    @Test void unknownRequiredHydrogensDoNotBecomeGlobalAbsence()throws Exception {
        var r=roles(ChemicalRoleAcceptanceTest.chemical("methylamine"),true);
        assertEquals("UNKNOWN_INCONCLUSIVE",r.get("DONOR.AMINE_PRIMARY").get("assessment").asText());
        assertEquals("UNKNOWN_INCONCLUSIVE",r.get("ACCEPTOR.AMINE_PRIMARY").get("assessment").asText());
        assertTrue(present(r).isEmpty()); // No aggregate ABSENT_FALSE is manufactured from empty positives.
    }
    @Test void aromaticRolesDoNotSelectAnImplicitTautomer()throws Exception {
        assertEquals(Set.of("DONOR.PYRROLIC_NH"),present(roles(ChemicalRoleAcceptanceTest.chemical("pyrrole"),false)));
        assertEquals(Set.of("ACCEPTOR.PYRIDINE_LIKE_N"),present(roles(ChemicalRoleAcceptanceTest.chemical("pyridine"),false)));
        assertEquals(Set.of("DONOR.PYRIDINIUM_NH"),present(roles(ChemicalRoleAcceptanceTest.chemical("pyridinium"),false)));
    }
    public static void main(String[] args)throws Exception {
        var reports=new TreeMap<String,Object>();for(var name:List.of("methylamine","methylammonium","acetamide","pyrrole","pyridine","pyridinium"))reports.put(name,roles(ChemicalRoleAcceptanceTest.chemical(name),false));
        Files.write(Path.of(args[0]),SystemStateView.bytes(reports));
    }
}
