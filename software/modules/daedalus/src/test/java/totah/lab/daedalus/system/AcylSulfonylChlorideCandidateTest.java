package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/** Review-only definitions executed through the existing group interpreter. */
class AcylSulfonylChlorideCandidateTest {
    static final Path REVIEW = Path.of("software/qualification/f05-acyl-sulfonyl-chloride-review-20261006");

    static RuleManifest candidate(boolean sulfur) throws Exception {
        String name = sulfur ? "SULFONYL_CHLORIDE" : "ACYL_CHLORIDE";
        var json = (ObjectNode) JSON.readTree(Files.readAllBytes(REVIEW.resolve(name + ".CARBON_BOUND.manifest.json")));
        assertEquals("NOT_EVALUATED", json.get("qualification").asText());
        json.put("qualification", "QUALIFIED"); // Engineering fixture, never persisted as human review.
        return RuleRegistry.decode(SystemStateView.bytes(json));
    }

    static Fixture fixture(boolean sulfur, String terminal) {
        var atoms = new ArrayList<MolecularGraph.Atom>();
        atoms.add(atom("r", "C", 0, false, -1, 0, 0));
        atoms.add(atom("center", sulfur ? "S" : "C", 0, false, 0, 0, 0));
        atoms.add(atom("o1", "O", 0, false, 1, 0, 0));
        atoms.add(atom("end", terminal, 0, false, 0, 1, 0));
        var bonds = new ArrayList<MolecularGraph.Bond>();
        bonds.add(bond("r", "center", MolecularGraph.BondOrder.SINGLE));
        bonds.add(bond("center", "o1", MolecularGraph.BondOrder.DOUBLE));
        bonds.add(bond("center", "end", MolecularGraph.BondOrder.SINGLE));
        var hs = new TreeMap<>(Map.of("r", 3, "center", 0, "o1", 0, "end", terminal.equals("O") ? 1 : 0));
        if (sulfur) {
            atoms.add(atom("o2", "O", 0, false, 0, 0, 1));
            bonds.add(bond("center", "o2", MolecularGraph.BondOrder.DOUBLE));
            hs.put("o2", 0);
        }
        return new Fixture(new MolecularGraph(atoms, bonds, Map.of()), hs);
    }

    static JsonNode evaluate(boolean sulfur, Fixture fixture) throws Exception {
        var state = system(List.of(fixture.graph()), true, false);
        var before = SystemStateView.bytes(state.snapshot());
        var result = report(candidate(sulfur), state, coverage(state, fixture));
        assertArrayEquals(before, SystemStateView.bytes(state.snapshot()));
        return result;
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exactMembershipAndContextRemainSeparate(boolean sulfur) throws Exception {
        var result = evaluate(sulfur, fixture(sulfur, "Cl"));
        assertEquals("SUPPORTED_PRESENT", result.get("assessment").asText());
        assertEquals(1, result.get("occurrences").size());
        var occurrence = result.get("occurrences").get(0);
        var members = JSON.convertValue(occurrence.get("memberAtomIds"), List.class);
        assertEquals(sulfur ? 4 : 3, members.size());
        assertFalse(members.contains("r"));
        assertEquals("r", occurrence.get("roleCorrespondenceAlternatives").get(0).get("carbonAttachment").get(0).asText());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void bromideAcidAndOtherFamilyAreNotTheRequestedIdentity(boolean sulfur) throws Exception {
        for (var miss : List.of(fixture(sulfur, "Br"), fixture(sulfur, "O"), fixture(!sulfur, "Cl"))) {
            assertEquals("ABSENT_FALSE", evaluate(sulfur, miss).get("assessment").asText());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void explicitHydrogenDistinctOccurrencesAndPermutations(boolean sulfur) throws Exception {
        var fixture = fixture(sulfur, "Cl");
        assertEquals("SUPPORTED_PRESENT", evaluate(sulfur, explicit(fixture)).get("assessment").asText());
        assertEquals(2, evaluate(sulfur, doubled(fixture)).get("occurrences").size());
        var atoms = new ArrayList<>(fixture.graph().atoms());
        var bonds = new ArrayList<>(fixture.graph().bonds());
        Collections.reverse(atoms); Collections.reverse(bonds);
        var permuted = new Fixture(new MolecularGraph(atoms, bonds, Map.of()), fixture.hydrogens());
        assertEquals(normalized(evaluate(sulfur, fixture)), normalized(evaluate(sulfur, permuted)));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void incompleteStateCannotBecomeNegative(boolean sulfur) throws Exception {
        var fixture = fixture(sulfur, "Cl");
        var state = system(List.of(fixture.graph()), true, false);
        for (String mode : List.of("hydrogen", "charge", "graph")) {
            var source = coverage(state, fixture);
            if (mode.equals("graph")) source.put("completeGraph", "UNKNOWN_INCONCLUSIVE");
            else source.get("atomState").forEach(a -> {
                var atom = (ObjectNode) a;
                if (mode.equals("charge")) atom.put("chargeStatus", "UNKNOWN_INCONCLUSIVE");
                else { atom.put("hydrogenMode", "UNKNOWN"); atom.putNull("implicitHydrogenCount"); }
            });
            assertEquals("UNKNOWN_INCONCLUSIVE", report(candidate(sulfur), state, source).get("assessment").asText());
        }
    }

    @Test void legacyCarbonylDomainAndSulfurOxygenSymmetrySurvive() throws Exception {
        // Original B01 CARBONYL domain excludes Cl. Do not widen it to make this candidate pass.
        assertEquals("UNSUPPORTED", report("CARBONYL", fixture(false, "Cl")).get("assessment").asText());
        var result = evaluate(true, fixture(true, "Cl"));
        assertEquals(1, result.get("occurrences").size());
        assertEquals(2, result.get("occurrences").get(0).get("roleCorrespondenceAlternatives").size());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aromaticCarbonAttachmentIsContextNotAnExtraMember(boolean sulfur) throws Exception {
        var base = fixture(sulfur, "Cl");
        var ring = FoundationVocabularyAcceptanceTest.molecule("benzene");
        var atoms = new ArrayList<>(ring.graph().atoms());
        base.graph().atoms().stream().filter(a -> !a.id().equals("r")).forEach(atoms::add);
        var bonds = new ArrayList<>(ring.graph().bonds());
        base.graph().bonds().stream().filter(b -> !b.firstAtomId().equals("r")).forEach(bonds::add);
        bonds.add(bond("a0", "center", MolecularGraph.BondOrder.SINGLE));
        var hs = new TreeMap<>(base.hydrogens()); hs.remove("r");
        hs.putAll(ring.hydrogens()); hs.put("a0", 0);
        var result = evaluate(sulfur, new Fixture(new MolecularGraph(atoms, bonds, Map.of()), hs));
        assertEquals("SUPPORTED_PRESENT", result.get("assessment").asText());
        assertEquals(sulfur ? 4 : 3, result.get("occurrences").get(0).get("memberAtomIds").size());
    }

    @Test void chargeSeparatedSulfurIsNotNormalizedIntoTheNeutralIdentity() throws Exception {
        var base = fixture(true, "Cl");
        var atoms = new ArrayList<MolecularGraph.Atom>();
        for (var a : base.graph().atoms()) {
            int charge = a.id().equals("center") ? 2 : a.id().startsWith("o") ? -1 : 0;
            atoms.add(atom(a.id(), a.element(), charge, false, 0, 0, 0));
        }
        var bonds = base.graph().bonds().stream()
                .map(b -> bond(b.firstAtomId(), b.secondAtomId(), MolecularGraph.BondOrder.SINGLE)).toList();
        var result = evaluate(true, new Fixture(new MolecularGraph(atoms, bonds, Map.of()), base.hydrogens()));
        assertEquals("UNSUPPORTED", result.get("assessment").asText());
    }

    public static void main(String[] args) throws Exception {
        Files.write(Path.of(args[0]), SystemStateView.bytes(List.of(
                evaluate(false, fixture(false, "Cl")), evaluate(true, fixture(true, "Cl")))));
    }
}
