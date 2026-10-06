package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.RuleManifest;
import totah.lab.athena.system.rules.RuleRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;

/** Prospective F18 graph predicate; fixture qualification does not authorize scientific adoption. */
class TerminalAlkyneCandidateTest {
    private static RuleManifest candidate() throws Exception {
        var path = Path.of("software/qualification/f18-terminal-alkyne-contract-20261006/CANDIDATE_MANIFEST.json");
        var copy = (ObjectNode) JSON.readTree(Files.readAllBytes(path));
        assertEquals("NOT_EVALUATED", copy.get("qualification").asText());
        copy.put("qualification", "QUALIFIED"); // Synthetic execution fixture only.
        return RuleRegistry.decode(SystemStateView.bytes(copy));
    }

    private static Fixture alkyne(boolean leftMethyl, boolean rightMethyl) {
        var atoms = new ArrayList<MolecularGraph.Atom>();
        var bonds = new ArrayList<MolecularGraph.Bond>();
        var hydrogen = new TreeMap<String, Integer>();
        atoms.add(atom("a", "C", 0, false, 0, 0, 0));
        atoms.add(atom("b", "C", 0, false, 1.2, 0, 0));
        bonds.add(bond("a", "b", MolecularGraph.BondOrder.TRIPLE));
        hydrogen.put("a", leftMethyl ? 0 : 1);
        hydrogen.put("b", rightMethyl ? 0 : 1);
        for (var end : List.of("a", "b")) {
            if (end.equals("a") ? leftMethyl : rightMethyl) {
                String id = end + "m";
                atoms.add(atom(id, "C", 0, false, end.equals("a") ? -1.5 : 2.7, 0, 0));
                bonds.add(bond(end, id, MolecularGraph.BondOrder.SINGLE));
                hydrogen.put(id, 3);
            }
        }
        return new Fixture(new MolecularGraph(atoms, bonds, Map.of()), hydrogen);
    }

    private static JsonNode evaluate(Fixture fixture) throws Exception {
        var state = system(List.of(fixture.graph()), true, false);
        byte[] before = SystemStateView.bytes(state.snapshot());
        var result = report(candidate(), state, coverage(state, fixture));
        assertArrayEquals(before, SystemStateView.bytes(state.snapshot()));
        return result;
    }

    @Test void acetyleneHasOneOccurrenceAndTwoTerminalRoleAlternatives() throws Exception {
        var result = evaluate(alkyne(false, false));
        assertEquals("SUPPORTED_PRESENT", result.get("assessment").asText());
        assertEquals(1, result.get("occurrences").size());
        assertEquals(2, result.get("occurrences").get(0).get("roleCorrespondenceAlternatives").size());
    }

    @Test void propyneIsTerminalButTwoButyneIsNot() throws Exception {
        assertEquals("SUPPORTED_PRESENT", evaluate(alkyne(true, false)).get("assessment").asText());
        assertEquals("ABSENT_FALSE", evaluate(alkyne(true, true)).get("assessment").asText());
    }

    @Test void explicitHydrogenAndDistinctOccurrencesSurvive() throws Exception {
        assertEquals("SUPPORTED_PRESENT", evaluate(explicit(alkyne(true, false))).get("assessment").asText());
        assertEquals(2, evaluate(doubled(alkyne(true, false))).get("occurrences").size());
    }

    @Test void unknownAndContradictoryHydrogenAreNotNegatives() throws Exception {
        var fixture = alkyne(true, false);
        var state = system(List.of(fixture.graph()), true, false);
        for (boolean unknown : List.of(true, false)) {
            var source = coverage(state, fixture);
            var atom = (ObjectNode) source.get("atomState").get("b");
            if (unknown) { atom.put("hydrogenMode", "UNKNOWN"); atom.putNull("implicitHydrogenCount"); }
            else atom.put("implicitHydrogenCount", 2);
            assertEquals("UNKNOWN_INCONCLUSIVE", report(candidate(), state, source).get("assessment").asText());
        }
    }

    @Test void stableOrderingAndGeneralAlkyneIdentityRemain() throws Exception {
        var fixture = alkyne(false, false);
        var atoms = new ArrayList<>(fixture.graph().atoms());
        Collections.reverse(atoms);
        var permuted = new Fixture(new MolecularGraph(atoms, fixture.graph().bonds(), Map.of()), fixture.hydrogens());
        assertEquals(normalized(evaluate(fixture)), normalized(evaluate(permuted)));
        assertEquals("SUPPORTED_PRESENT", FoundationVocabularyAcceptanceTest.run("ALKYNE", fixture).get("assessment").asText());
        assertEquals("SUPPORTED_PRESENT", FoundationVocabularyAcceptanceTest.run("ALKYNE", alkyne(true, true)).get("assessment").asText());
    }

    @Test void constitutionalNearMissesAreNotTerminalAlkynes() throws Exception {
        for (var name : List.of("ethene", "ethane", "acetonitrile")) {
            assertEquals("ABSENT_FALSE", evaluate(FoundationVocabularyAcceptanceTest.molecule(name)).get("assessment").asText());
        }
    }

    @Test void incompleteGraphAndUnknownChargeCannotEstablishAbsence() throws Exception {
        var fixture = alkyne(true, true);
        var state = system(List.of(fixture.graph()), true, false);
        var incomplete = coverage(state, fixture);
        incomplete.put("completeGraph", "UNKNOWN_INCONCLUSIVE");
        assertEquals("UNKNOWN_INCONCLUSIVE", report(candidate(), state, incomplete).get("assessment").asText());
        var unknownCharge = coverage(state, fixture);
        unknownCharge.get("atomState").forEach(a -> ((ObjectNode) a).put("chargeStatus", "UNKNOWN_INCONCLUSIVE"));
        assertEquals("UNKNOWN_INCONCLUSIVE", report(candidate(), state, unknownCharge).get("assessment").asText());
    }

    @Test void terminalRoleIdentifiesTheHydrogenBearingEnd() throws Exception {
        var result = evaluate(alkyne(true, false));
        var occurrence = result.get("occurrences").get(0);
        assertEquals(List.of("a", "b"), JSON.convertValue(occurrence.get("memberAtomIds"), List.class));
        var alternatives = occurrence.get("roleCorrespondenceAlternatives");
        assertEquals(1, alternatives.size());
        assertEquals("b", alternatives.get(0).get("terminalCarbon").get(0).asText());
        assertEquals("a", alternatives.get(0).get("partnerCarbon").get(0).asText());
    }

    public static void main(String[] args) throws Exception {
        Files.write(Path.of(args[0]), SystemStateView.bytes(List.of(
                evaluate(alkyne(false, false)), evaluate(alkyne(true, false)), evaluate(alkyne(true, true)))));
    }
}
