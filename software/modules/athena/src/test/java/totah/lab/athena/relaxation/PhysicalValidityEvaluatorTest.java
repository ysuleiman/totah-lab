package totah.lab.athena.relaxation;

import org.junit.jupiter.api.Test;
import totah.lab.athena.energy.EnergyComponent;
import totah.lab.athena.energy.EnergyEvaluation;
import totah.lab.athena.energy.ScientificMethod;
import totah.lab.athena.thermo.EnergyUnit;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PhysicalValidityEvaluatorTest {
    @Test
    void reportsAllIndependentFailuresWithoutDiscardingEvidence() {
        Structure receptor = oneAtom("A", "ALA", "CA", 0.0);
        Structure ligand = oneAtom("L", "LIG", "C1", 0.1);
        Structure changedLigand = new Structure(List.of(new Chain("L", List.of(
                new Residue("LIG", 1, List.of(atom(2, "C2", 0.1)))))));
        Structure combined = new Structure(List.of(receptor.getChains().getFirst(),
                ligand.getChains().getFirst()));
        var displacements = new DisplacementCalculator.DetailedDisplacements(
                2, 3, 4, 5, 6, 6, 6, "FIXED_RECEPTOR_FRAME_NO_SUPERPOSITION");
        var input = new PhysicalValidityEvaluator.Input(receptor, receptor,
                ligand, changedLigand, null, null, combined, energy(), displacements,
                false, false, false);
        var result = PhysicalValidityEvaluator.evaluate(input,
                new PhysicalValidityEvaluator.Policy(0.7, 0, 1, 1, 1, 1, 1));
        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).extracting(PhysicalValidityEvaluator.Failure::code)
                .contains(PhysicalValidityEvaluator.Code.LIGAND_TOPOLOGY_INTEGRITY,
                        PhysicalValidityEvaluator.Code.SEVERE_CLASH,
                        PhysicalValidityEvaluator.Code.RESTRAINT_ESCAPE,
                        PhysicalValidityEvaluator.Code.EXCESSIVE_LIGAND_DISTORTION,
                        PhysicalValidityEvaluator.Code.EXCESSIVE_RECEPTOR_MOVEMENT,
                        PhysicalValidityEvaluator.Code.MINIMIZATION_FAILURE,
                        PhysicalValidityEvaluator.Code.MISSING_PARAMETERS,
                        PhysicalValidityEvaluator.Code.INVALID_ATOM_MAPPING);
    }

    @Test
    void exactBoundaryIsAcceptedBecausePoliciesUseStrictExceedance() {
        Structure receptor = oneAtom("A", "ALA", "CA", 0);
        Structure ligand = oneAtom("L", "LIG", "C1", 10);
        Structure combined = new Structure(List.of(receptor.getChains().getFirst(),
                ligand.getChains().getFirst()));
        var d = new DisplacementCalculator.DetailedDisplacements(1, 1, 1, 1, 1, 1, 1,
                "FIXED_RECEPTOR_FRAME_NO_SUPERPOSITION");
        var result = PhysicalValidityEvaluator.evaluate(new PhysicalValidityEvaluator.Input(
                receptor, receptor, ligand, ligand, null, null, combined, energy(), d,
                true, true, true), new PhysicalValidityEvaluator.Policy(0.7, 0, 1, 1, 1, 1, 1));
        assertThat(result.valid()).isTrue();
    }

    private static EnergyEvaluation energy() {
        return new EnergyEvaluation("q", Map.of(EnergyComponent.TOTAL_POTENTIAL, 1.0),
                EnergyUnit.KILOJOULES_PER_MOLE, "fixture", "fixture", "fixture",
                ScientificMethod.STATE_ENERGY, Map.of());
    }

    private static Structure oneAtom(String chain, String residue, String atom, double x) {
        return new Structure(List.of(new Chain(chain, List.of(new Residue(residue, 1,
                List.of(atom(1, atom, x)))))));
    }

    private static Atom atom(int serial, String name, double x) {
        return Atom.builder().pdbSerial(serial).name(name).position(new Point3D(x, 0, 0))
                .element(Element.C).build();
    }
}
