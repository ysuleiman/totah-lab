package totah.lab.mettl7.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.MolecularSanitizer;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.design.grammar.ExecutableScaffoldGrammar;
import totah.lab.athena.design.grammar.DesignGrammarJsonCodec;
import totah.lab.athena.design.grammar.PredockGrammarEngine;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7bRepresentationRepairTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir")).resolve("../../../").normalize();
    private static final Path CAMPAIGN = ROOT.resolve("analysis/mettl7/mettl7b_design_campaign_v1");

    @Test
    void executableGraphExactlyMatchesRegisteredHermesSdfAndSurvivesOcl() throws Exception {
        var executable = new ObjectMapper().readValue(CAMPAIGN.resolve(
                "METTL7B_BRICS0040_EXECUTABLE_SCAFFOLD_GRAMMAR.json").toFile(),
                ExecutableScaffoldGrammar.class);
        var sdf = new SdfLigandReader().readModel(CAMPAIGN.resolve("ligands/BRICS0040_NEUTRAL.sdf"));
        var atoms = sdf.ligand().structure().getChains().getFirst().residues().getFirst().getAtoms();
        assertThat(executable.parentGraph().atoms()).hasSameSizeAs(atoms);
        for (int index = 0; index < atoms.size(); index++) {
            var actual = executable.parentGraph().atoms().get(index);
            assertThat(actual.id()).isEqualTo(atoms.get(index).getName());
            assertThat(actual.element()).isEqualTo(atoms.get(index).getElement().name());
            assertThat(actual.formalCharge()).isEqualTo(sdf.formalCharges().get(index));
        }
        assertThat(executable.parentGraph().bonds()).hasSameSizeAs(sdf.bonds());
        for (var bond : sdf.bonds()) {
            String first = atoms.get(bond.atomIndexA()).getName();
            String second = atoms.get(bond.atomIndexB()).getName();
            var actual = executable.parentGraph().bonds().stream().filter(candidate ->
                    Set.of(candidate.firstAtomId(), candidate.secondAtomId()).equals(Set.of(first, second)))
                    .findFirst().orElseThrow();
            assertThat(actual.order().toString()).isEqualTo(bond.order().name());
            assertThat(actual.aromatic()).isEqualTo(bond.aromatic());
        }
        var graph = new MolecularGraph(executable.parentGraph().atoms().stream().map(atom ->
                new MolecularGraph.Atom(atom.id(), atom.element(), null,
                        atom.formalCharge() == null ? 0 : atom.formalCharge(), 0, atom.aromatic(),
                        atom.stereochemistry(), null, Map.of())).toList(),
                executable.parentGraph().bonds().stream().map(bond -> new MolecularGraph.Bond(
                        bond.firstAtomId() + "|" + bond.secondAtomId(), bond.firstAtomId(), bond.secondAtomId(),
                        MolecularGraph.BondOrder.valueOf(bond.order()), bond.aromatic(),
                        bond.stereochemistry(), Map.of())).toList(), Map.of());
        var sanitized = new OclMolecularBackend().sanitize(graph,
                new MolecularSanitizer.SanitizationPolicy(
                        Set.of("KEKULE_TO_AROMATIC", "UNSPECIFIED_TO_UNKNOWN_STEREO"), true));
        assertThat(sanitized.evidence().graphChanges()).allMatch(change ->
                change.disposition() == totah.lab.athena.design.backend.BackendEvidence.Disposition.BENIGN_NORMALIZATION);
        assertThat(sanitized.valid()).isTrue();
        assertThat(sanitized.graph().atoms()).hasSameSizeAs(graph.atoms());
        assertThat(sanitized.graph().bonds()).hasSameSizeAs(graph.bonds());
    }

    @Test
    void registeredParentPassesReconciledHardGates() throws Exception {
        var grammar = new Mettl7bGrammarConfigurationLoader().load(
                CAMPAIGN.resolve("METTL7B_TARGET_LEVEL_B_RECOGNITION_GRAMMAR.json"),
                Optional.of(CAMPAIGN.resolve("METTL7B_BRICS0040_SCAFFOLD_GRAMMAR.json")),
                CAMPAIGN.resolve("METTL7B_A_COUNTER_RECOGNITION_GRAMMAR.json"),
                CAMPAIGN.resolve("METTL7B_GRAMMAR_HARD_SOFT_RULES.csv"));
        var parent = java.util.Arrays.stream(new DesignGrammarJsonCodec().readCandidates(
                CAMPAIGN.resolve("METTL7B_PREDOCK_RETROSPECTIVE_INPUT.json")))
                .filter(candidate -> candidate.candidateId().equals("BRICS0040_NEUTRAL"))
                .findFirst().orElseThrow();
        var result = new PredockGrammarEngine().evaluate(grammar, parent);
        assertThat(result.hardRulesPass()).as(result.hardRules().toString()).isTrue();
    }

    @Test
    void allFortyTwoRootEditSlotsPassTheParentRepresentationPrecheck() throws Exception {
        var executable = new ObjectMapper().readValue(CAMPAIGN.resolve(
                "METTL7B_BRICS0040_EXECUTABLE_SCAFFOLD_GRAMMAR.json").toFile(),
                ExecutableScaffoldGrammar.class);
        var graph = new MolecularGraph(executable.parentGraph().atoms().stream().map(atom ->
                new MolecularGraph.Atom(atom.id(), atom.element(), null,
                        atom.formalCharge() == null ? 0 : atom.formalCharge(), 0, atom.aromatic(),
                        atom.stereochemistry(), null, Map.of())).toList(),
                executable.parentGraph().bonds().stream().map(bond -> new MolecularGraph.Bond(
                        bond.firstAtomId() + "|" + bond.secondAtomId(), bond.firstAtomId(), bond.secondAtomId(),
                        MolecularGraph.BondOrder.valueOf(bond.order()), bond.aromatic(),
                        bond.stereochemistry(), Map.of())).toList(), Map.of());
        int rootEditSlots = executable.editableVectors().stream().mapToInt(vector ->
                vector.allowedTransformationClasses().stream().mapToInt(transformation ->
                        transformation.equals("RING_SUBSTITUTION")
                                ? (vector.currentSubgraphAtomIds().stream().anyMatch(id ->
                                graph.atom(id).map(atom -> atom.element().equals("H")).orElse(false)) ? 3 : 0)
                                : 1
                ).sum()).sum();
        assertThat(rootEditSlots).isEqualTo(42);
        var backend = new OclMolecularBackend();
        for (int slot = 0; slot < rootEditSlots; slot++) {
            assertThat(backend.sanitize(graph, new MolecularSanitizer.SanitizationPolicy(
                    Set.of("KEKULE_TO_AROMATIC", "UNSPECIFIED_TO_UNKNOWN_STEREO"), true)).valid()).isTrue();
        }
    }
}
