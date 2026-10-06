package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/** Literal proposed identities only; no activation or chemical-reactivity classifier. */
class IsocyanateCandidateTest {
    static final Path REVIEW = Path.of("software/qualification/f14-isocyanate-review-20261006");

    static RuleManifest candidate(boolean sulfur) throws Exception {
        var path = REVIEW.resolve((sulfur ? "ISOTHIOCYANATE" : "ISOCYANATE") + ".manifest.json");
        var json = (ObjectNode) JSON.readTree(Files.readAllBytes(path));
        assertEquals("NOT_EVALUATED", json.get("qualification").asText());
        json.put("qualification", "QUALIFIED");
        return RuleRegistry.decode(SystemStateView.bytes(json));
    }

    static Fixture fixture(boolean sulfur, boolean isomer) {
        var atoms = List.of(atom("r", "C", 0, false, -1, 0, 0),
                atom("n", "N", 0, false, 0, 0, 0), atom("c", "C", 0, false, 1, 0, 0),
                atom("x", sulfur ? "S" : "O", 0, false, 2, 0, 0));
        var single = MolecularGraph.BondOrder.SINGLE;
        var dual = MolecularGraph.BondOrder.DOUBLE;
        var bonds = isomer
                ? List.of(bond("r", "x", single), bond("x", "c", single), bond("c", "n", MolecularGraph.BondOrder.TRIPLE))
                : List.of(bond("r", "n", single), bond("n", "c", dual), bond("c", "x", dual));
        return new Fixture(new MolecularGraph(atoms, bonds, Map.of()), Map.of("r", 3, "n", 0, "c", 0, "x", 0));
    }

    static JsonNode evaluate(boolean sulfur, Fixture fixture) throws Exception {
        var source = system(List.of(fixture.graph()), true, false);
        byte[] before = SystemStateView.bytes(source.snapshot());
        var result = report(candidate(sulfur), source, coverage(source, fixture));
        assertArrayEquals(before, SystemStateView.bytes(source.snapshot()));
        return result;
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exactCoreAndAttachmentAreAttributed(boolean sulfur) throws Exception {
        var report = evaluate(sulfur, fixture(sulfur, false));
        assertEquals("SUPPORTED_PRESENT", report.get("assessment").asText());
        var occurrence = report.get("occurrences").get(0);
        assertEquals(List.of("c", "n", "x"), JSON.convertValue(occurrence.get("memberAtomIds"), List.class));
        assertEquals("r", occurrence.get("roleCorrespondenceAlternatives").get(0).get("carbonAttachment").get(0).asText());
        assertEquals("n", occurrence.get("roleCorrespondenceAlternatives").get(0).get("nitrogen").get(0).asText());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void constitutionalIsomerAndDifferentTerminalElementRemainDifferent(boolean sulfur) throws Exception {
        assertEquals("ABSENT_FALSE", evaluate(sulfur, fixture(sulfur, true)).get("assessment").asText());
        assertEquals("ABSENT_FALSE", evaluate(sulfur, fixture(!sulfur, false)).get("assessment").asText());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void occurrencesExplicitHydrogenAndPermutation(boolean sulfur) throws Exception {
        var base = fixture(sulfur, false);
        assertEquals("SUPPORTED_PRESENT", evaluate(sulfur, explicit(base)).get("assessment").asText());
        assertEquals(2, evaluate(sulfur, doubled(base)).get("occurrences").size());
        var atoms = new ArrayList<>(base.graph().atoms()); Collections.reverse(atoms);
        var bonds = new ArrayList<>(base.graph().bonds()); Collections.reverse(bonds);
        var permuted = new Fixture(new MolecularGraph(atoms, bonds, Map.of()), base.hydrogens());
        assertEquals(normalized(evaluate(sulfur, base)), normalized(evaluate(sulfur, permuted)));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void missingHydrogenChargeOrGraphCoverageIsNotAbsence(boolean sulfur) throws Exception {
        var base = fixture(sulfur, false);
        var source = system(List.of(base.graph()), true, false);
        for (String mode : List.of("H", "charge", "graph")) {
            var coverage = coverage(source, base);
            var nitrogen = (ObjectNode) coverage.get("atomState").get("n");
            if (mode.equals("H")) { nitrogen.put("hydrogenMode", "UNKNOWN"); nitrogen.putNull("implicitHydrogenCount"); }
            else if (mode.equals("charge")) nitrogen.put("chargeStatus", "UNKNOWN_INCONCLUSIVE");
            else coverage.put("completeGraph", "UNKNOWN_INCONCLUSIVE");
            assertEquals("UNKNOWN_INCONCLUSIVE", report(candidate(sulfur), source, coverage).get("assessment").asText());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void chargeSeparatedRepresentationRemainsUnsupported(boolean sulfur) throws Exception {
        var base = fixture(sulfur, false);
        var atoms = base.graph().atoms().stream().map(a -> atom(a.id(), a.element(),
                a.id().equals("n") ? 1 : a.id().equals("x") ? -1 : 0, false, 0, 0, 0)).toList();
        var bonds = List.of(bond("r", "n", MolecularGraph.BondOrder.SINGLE),
                bond("n", "c", MolecularGraph.BondOrder.TRIPLE), bond("c", "x", MolecularGraph.BondOrder.SINGLE));
        assertEquals("UNSUPPORTED", evaluate(sulfur,
                new Fixture(new MolecularGraph(atoms, bonds, Map.of()), base.hydrogens())).get("assessment").asText());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aromaticAttachmentDoesNotBecomeCoreMembership(boolean sulfur) throws Exception {
        var base = fixture(sulfur, false);
        var ring = FoundationVocabularyAcceptanceTest.molecule("benzene");
        var atoms = new ArrayList<>(ring.graph().atoms());
        base.graph().atoms().stream().filter(a -> !a.id().equals("r")).forEach(atoms::add);
        var bonds = new ArrayList<>(ring.graph().bonds());
        base.graph().bonds().stream().filter(b -> !b.firstAtomId().equals("r")).forEach(bonds::add);
        bonds.add(bond("a0", "n", MolecularGraph.BondOrder.SINGLE));
        var hs = new TreeMap<>(base.hydrogens()); hs.remove("r"); hs.putAll(ring.hydrogens()); hs.put("a0", 0);
        var result = evaluate(sulfur, new Fixture(new MolecularGraph(atoms, bonds, Map.of()), hs));
        assertEquals("SUPPORTED_PRESENT", result.get("assessment").asText());
        assertEquals(3, result.get("occurrences").get(0).get("memberAtomIds").size());
    }

    public static void main(String[] args) throws Exception {
        Files.write(Path.of(args[0]), SystemStateView.bytes(List.of(
                evaluate(false, fixture(false, false)), evaluate(true, fixture(true, false)))));
    }
}
