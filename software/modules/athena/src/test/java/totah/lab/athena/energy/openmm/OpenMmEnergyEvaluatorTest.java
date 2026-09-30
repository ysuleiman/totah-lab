package totah.lab.athena.energy.openmm;

import org.junit.jupiter.api.Test;
import totah.lab.athena.energy.EnergyComponent;
import totah.lab.athena.energy.EnergyEvaluationException;
import totah.lab.athena.energy.MolecularState;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenMmEnergyEvaluatorTest {
    @Test
    void preservesComponentsAndCompleteDeterministicProvenance() throws Exception {
        var evaluator = new OpenMmEnergyEvaluator((state, groups) ->
                new OpenMmBackend.BackendResult(Map.of(1, 4.0, 2, -3.0), 8.0,
                        "8.5.2", "CPU", Map.of("Threads", "8",
                        "DeterministicForces", "true"), "amber-system-builder-v1",
                        hashes(), false, true), groups());
        var result = evaluator.evaluate(state());
        assertThat(result.components()).containsEntry(EnergyComponent.BONDED, 4.0)
                .containsEntry(EnergyComponent.PROTEIN_LIGAND_ELECTROSTATIC, -3.0)
                .containsEntry(EnergyComponent.TOTAL_POTENTIAL, 8.0);
        assertThat(result.provenance()).containsEntry("openmm.platform", "CPU")
                .containsEntry("platform.Threads", "8")
                .containsEntry("platform.DeterministicForces", "true")
                .containsEntry("receptor.sha256", "r");
    }

    @Test
    void rejectsNonfiniteEnergyAndMissingHashes() {
        var invalid = new OpenMmEnergyEvaluator((state, groups) ->
                new OpenMmBackend.BackendResult(Map.of(1, Double.NaN), 1,
                        "8.5.2", "CPU", Map.of(), "builder", hashes(), false, false),
                groups());
        assertThatThrownBy(() -> invalid.evaluate(state()))
                .isInstanceOf(EnergyEvaluationException.class);
        var missing = new OpenMmEnergyEvaluator((state, groups) ->
                new OpenMmBackend.BackendResult(Map.of(1, 1.0), 1,
                        "8.5.2", "CPU", Map.of(), "builder", Map.of(), false, true),
                groups());
        assertThatThrownBy(() -> missing.evaluate(state()))
                .isInstanceOf(EnergyEvaluationException.class);
    }

    @Test
    void rejectsMissingGroupsAndNoncanonicalCpuSettings() {
        var missingGroup = new OpenMmEnergyEvaluator((state, groups) ->
                new OpenMmBackend.BackendResult(Map.of(1, 1.0), 1,
                        "8.5.2", "CPU", Map.of("Threads", "8",
                        "DeterministicForces", "true"), "builder", hashes(), false, true),
                groups());
        assertThatThrownBy(() -> missingGroup.evaluate(state()))
                .isInstanceOf(EnergyEvaluationException.class)
                .hasMessageContaining("force-group");
        var nondeterministic = new OpenMmEnergyEvaluator((state, groups) ->
                new OpenMmBackend.BackendResult(Map.of(1, 1.0, 2, 1.0), 1,
                        "8.5.2", "CPU", Map.of("Threads", "4",
                        "DeterministicForces", "false"), "builder", hashes(), false, true),
                groups());
        assertThatThrownBy(() -> nondeterministic.evaluate(state()))
                .isInstanceOf(EnergyEvaluationException.class)
                .hasMessageContaining("CPU/8/true");
    }

    @Test
    void requiresSamHashExactlyWhenCofactorIsPresent() {
        var evaluator = new OpenMmEnergyEvaluator((state, groups) ->
                new OpenMmBackend.BackendResult(Map.of(1, 1.0, 2, 1.0), 2,
                        "8.5.2", "CPU", Map.of("Threads", "8",
                        "DeterministicForces", "true"), "builder",
                        Map.of("receptor.sha256", "r", "ligand.sha256", "l",
                                "parameters.sha256", "p"), false, true), groups());
        assertThatThrownBy(() -> evaluator.evaluate(stateWithCofactor()))
                .isInstanceOf(EnergyEvaluationException.class)
                .hasMessageContaining("sam.sha256");
    }

    private static OpenMmForceGroupMap groups() {
        return new OpenMmForceGroupMap(Map.of(
                1, new OpenMmForceGroupMap.ForceGroup("HarmonicBondForce",
                        EnergyComponent.BONDED, "bond term only"),
                2, new OpenMmForceGroupMap.ForceGroup("CustomNonbondedForce",
                        EnergyComponent.PROTEIN_LIGAND_ELECTROSTATIC,
                        "direct-space declared pair interaction; no PME reciprocal")));
    }

    private static Map<String, String> hashes() {
        return Map.of("receptor.sha256", "r", "ligand.sha256", "l",
                "parameters.sha256", "p", "sam.sha256", "s");
    }

    private static MolecularState state() {
        Structure empty = new Structure(List.of());
        return new MolecularState("q0", "A", "ligand", empty, empty,
                Optional.empty(), "neutral", "fixture-force-field", "fixture-parameters", "vacuum",
                "bounded", Map.of());
    }

    private static MolecularState stateWithCofactor() {
        Structure empty = new Structure(List.of());
        return new MolecularState("q0", "A", "ligand", empty, empty,
                Optional.of(empty), "neutral", "fixture-force-field",
                "fixture-parameters", "vacuum", "bounded", Map.of());
    }

}
