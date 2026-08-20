package totah.lab.prometheus.neural.ferminet.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
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
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

final class FermiNetDerivativeEngineParityTest {

    @Test
    void batchedForwardMatchesReferenceSpatialNuclearAndDirections() {
        Molecule molecule = hydrogen();
        FermiNetV1Configuration configuration =
                FermiNetV1Configuration.testFixture();
        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(configuration, molecule);
        FermiNetV1State state = new FermiNetV1State(molecule, configuration,
                FermiNetParameters.initialize(layout, 44017L));
        QuantumCoordinates coordinates = coordinates();
        FermiNetDerivativeEngine reference = FermiNetStateAccess.derivatives(
                FermiNetDerivativeConfiguration.referenceJet());
        FermiNetDerivativeEngine batched = FermiNetStateAccess.derivatives(
                FermiNetDerivativeConfiguration.batchedForward());

        var expectedSpatial = reference.spatial(state, coordinates);
        var actualSpatial = batched.spatial(state, coordinates);
        assertEquals(expectedSpatial.sign(), actualSpatial.sign());
        assertEquals(expectedSpatial.logAbsoluteWavefunction(),
                actualSpatial.logAbsoluteWavefunction(), 1.0e-12);
        assertArrayEquals(expectedSpatial.logCoordinateGradient(),
                actualSpatial.logCoordinateGradient(), 1.0e-12);
        assertEquals(expectedSpatial.laplacianOverWavefunction(),
                actualSpatial.laplacianOverWavefunction(), 1.0e-10);

        var expectedNuclear = reference.nuclear(state, coordinates);
        var actualNuclear = batched.nuclear(state, coordinates);
        assertEquals(expectedNuclear.sign(), actualNuclear.sign());
        assertEquals(expectedNuclear.logAbsoluteWavefunction(),
                actualNuclear.logAbsoluteWavefunction(), 1.0e-12);
        assertArrayEquals(expectedNuclear.logNuclearGradient(),
                actualNuclear.logNuclearGradient(), 1.0e-11);

        List<FermiNetStateAccess.NuclearDirection> nuclear = new ArrayList<>();
        List<FermiNetStateAccess.ElectronDirection> electron = new ArrayList<>();
        for (int direction = 0; direction < 6; direction++) {
            double[] n = new double[6];
            double[] e = new double[6];
            n[direction] = 1.0;
            e[direction] = 0.17 - 0.03 * direction;
            nuclear.add(new FermiNetStateAccess.NuclearDirection(n));
            electron.add(new FermiNetStateAccess.ElectronDirection(e));
        }
        var actualBatch = batched.directionalBatch(
                state, coordinates, nuclear, electron);
        var actualDirections = actualBatch.directions();
        for (int direction = 0; direction < nuclear.size(); direction++) {
            var expected = reference.directional(state, coordinates,
                    nuclear.get(direction), electron.get(direction));
            var actual = actualDirections.get(direction);
            assertEquals(expected.sign(), actual.sign());
            assertEquals(expected.logAbsoluteWavefunction(),
                    actual.logAbsoluteWavefunction(), 1.0e-12);
            assertEquals(expected.laplacianOverWavefunction(),
                    actual.laplacianOverWavefunction(), 1.0e-10);
            assertEquals(expected.directionalLogAbsoluteWavefunction(),
                    actual.directionalLogAbsoluteWavefunction(), 1.0e-10);
            assertEquals(expected.directionalLaplacianOverWavefunction(),
                    actual.directionalLaplacianOverWavefunction(), 1.0e-8);
        }
    }

    private static Molecule hydrogen() {
        return new Molecule("derivative-engine-h2", List.of(
                new NuclearCenter(0, "H", new NuclearCharge(1),
                        new CartesianPosition(-0.7, 0.0, 0.0, LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(0.7, 0.0, 0.0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(2),
                new SpinSector(1, 1, 1));
    }

    private static QuantumCoordinates coordinates() {
        return new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(
                        0, -0.42, 0.18, -0.11, SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(
                        1, 0.37, -0.16, 0.09, SpinProjection.BETA)));
    }
}
