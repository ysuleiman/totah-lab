package totah.lab.mettl7.landscape;

import org.junit.jupiter.api.Test;
import totah.lab.athena.energy.EnergyComponent;
import totah.lab.athena.energy.EnergyEvaluation;
import totah.lab.athena.energy.ScientificMethod;
import totah.lab.athena.thermo.EnergyUnit;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mettl7DifferentialEnergyTest {
    @Test
    void computesOnlyDeclaredBMinusAComponents() {
        var a = energy("A", 2.0, 10.0);
        var b = energy("B", 5.0, 99.0);
        var policy = new Mettl7EnergyComparability(
                Set.of(EnergyComponent.PROTEIN_LIGAND_VDW),
                "matched coordinates and interaction term", "same SAM microstate",
                "fixture-force-field", "ligand-params", "implicit-water", "NoCutoff",
                "none", "not applicable", "restraints", "minimization", "frame",
                "perturbation", "fixture preparation");
        var result = Mettl7DifferentialEnergy.compare("q0", a, b, policy);
        assertThat(result.bMinusA())
                .containsOnly(Map.entry(EnergyComponent.PROTEIN_LIGAND_VDW, 3.0));
    }

    @Test
    void forbidsRawWholeSystemTotalAndMismatchedForceFields() {
        assertThatThrownBy(() -> new Mettl7EnergyComparability(
                Set.of(EnergyComponent.TOTAL_POTENTIAL), "none", "SAM", "ff",
                "params", "solvent", "method", "cutoff", "temperature",
                "restraint", "minimization", "frame", "perturbation", "prep"))
                .isInstanceOf(IllegalArgumentException.class);
        var policy = new Mettl7EnergyComparability(
                Set.of(EnergyComponent.PROTEIN_LIGAND_VDW), "matched", "SAM", "ff",
                "ligand-params", "implicit-water", "NoCutoff", "none", "not applicable",
                "restraints", "minimization", "frame", "perturbation", "prep");
        assertThatThrownBy(() -> Mettl7DifferentialEnergy.compare("q0",
                energy("A", 1, 1), energyWithForceField("B", "other"), policy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEvaluationReceiptMismatch() {
        var policy = new Mettl7EnergyComparability(
                Set.of(EnergyComponent.PROTEIN_LIGAND_VDW), "matched", "same SAM microstate",
                "fixture-force-field", "ligand-params", "implicit-water", "NoCutoff",
                "none", "not applicable", "restraints", "minimization", "frame",
                "perturbation", "prep");
        Map<String, String> alteredProvenance = new java.util.LinkedHashMap<>(provenance());
        alteredProvenance.remove("solvent.model");
        EnergyEvaluation altered = new EnergyEvaluation("B", Map.of(
                EnergyComponent.TOTAL_POTENTIAL, 1.0,
                EnergyComponent.PROTEIN_LIGAND_VDW, 1.0),
                EnergyUnit.KILOCALORIES_PER_MOLE, "fixture-engine",
                "fixture-force-field", "fixture", ScientificMethod.STATE_ENERGY,
                alteredProvenance);
        assertThatThrownBy(() -> Mettl7DifferentialEnergy.compare(
                "q0", energy("A", 1, 1), altered, policy))
                .hasMessageContaining("ENERGY_COMPARABILITY=FAIL: solvent.model");
    }

    private static EnergyEvaluation energy(String id, double vdw, double total) {
        return new EnergyEvaluation(id, Map.of(
                EnergyComponent.TOTAL_POTENTIAL, total,
                EnergyComponent.PROTEIN_LIGAND_VDW, vdw),
                EnergyUnit.KILOCALORIES_PER_MOLE, "fixture-engine",
                "fixture-force-field", "fixture", ScientificMethod.STATE_ENERGY,
                provenance());
    }

    private static EnergyEvaluation energyWithForceField(String id, String ff) {
        return new EnergyEvaluation(id, Map.of(
                EnergyComponent.TOTAL_POTENTIAL, 1.0,
                EnergyComponent.PROTEIN_LIGAND_VDW, 1.0),
                EnergyUnit.KILOCALORIES_PER_MOLE, "fixture-engine", ff,
                "fixture", ScientificMethod.STATE_ENERGY, provenance());
    }

    private static Map<String, String> provenance() {
        return Map.ofEntries(
                Map.entry("ligand.parameters.sha256", "ligand-params"),
                Map.entry("sam.state", "same SAM microstate"),
                Map.entry("solvent.model", "implicit-water"),
                Map.entry("nonbonded.method", "NoCutoff"),
                Map.entry("cutoff.switching", "none"),
                Map.entry("temperature", "not applicable"),
                Map.entry("restraint.protocol.sha256", "restraints"),
                Map.entry("minimization.protocol.sha256", "minimization"),
                Map.entry("common.frame", "frame"),
                Map.entry("perturbation.sha256", "perturbation"));
    }
}
