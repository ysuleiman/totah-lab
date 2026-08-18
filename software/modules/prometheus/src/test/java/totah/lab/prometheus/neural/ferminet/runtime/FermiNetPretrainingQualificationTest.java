package totah.lab.prometheus.neural.ferminet.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.ferminet.pretraining.FermiNetPretrainingQualification;
import totah.lab.prometheus.neural.ferminet.pretraining.HartreeFockOrbitalTarget;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

final class FermiNetPretrainingQualificationTest {
    @Test
    void correspondenceQualificationIsReadOnly() {
        Molecule molecule = molecule();
        var configuration = FermiNetV1Configuration.testFixture();
        var state = new FermiNetV1State(molecule, configuration,
                FermiNetParameters.initialize(new FermiNetParameterLayout(configuration, molecule), 17L));
        double[] before = state.parameterArray();
        HartreeFockOrbitalTarget target = coordinates ->
                new HartreeFockOrbitalTarget.OrbitalMatrices(
                        new double[][]{{Math.exp(-Math.abs(coordinates.particles().get(0).xBohr()))}},
                        new double[][]{{Math.exp(-Math.abs(coordinates.particles().get(1).xBohr()))}});
        var result = new FermiNetPretrainingQualification().evaluate(
                state, target, List.of(coordinates(0.1), coordinates(0.2)));
        assertArrayEquals(before, state.parameterArray());
        assertEquals(2, result.configurations());
        assertEquals(configuration.determinants(), result.alphaSubspaces().size());
        assertEquals(configuration.determinants(), result.betaSubspaces().size());
    }

    @Test
    void energyQualificationRejectsWrongGeometryIdentityBeforeSampling() {
        Molecule molecule = molecule();
        var configuration = FermiNetV1Configuration.testFixture();
        var state = new FermiNetV1State(molecule, configuration,
                FermiNetParameters.initialize(new FermiNetParameterLayout(configuration, molecule), 17L));
        var request = new FermiNetRuntimeSampling.Request(1, 0, 1, 1, 0.02, 9L);
        assertThrows(IllegalArgumentException.class, () ->
                new FermiNetPretrainingQualification().evaluateEnergy(
                        state, List.of(coordinates(0.1)), request, 1,
                        "not-the-canonical-geometry", -1.0));
    }

    private static Molecule molecule() {
        return new Molecule("qualification-fixture", List.of(
                new NuclearCenter(0,"H",new NuclearCharge(1),new CartesianPosition(0,0,-0.7,LengthUnit.BOHR)),
                new NuclearCenter(1,"H",new NuclearCharge(1),new CartesianPosition(0,0,0.7,LengthUnit.BOHR))),
                new MolecularCharge(0),new ElectronCount(2),new SpinSector(1,1,1));
    }
    private static QuantumCoordinates coordinates(double x) {
        return new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0,x,0,-0.4,SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1,-x,0,0.4,SpinProjection.BETA)));
    }
}
