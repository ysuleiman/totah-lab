package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/** Verifies that SR can reuse a derivative-complete evaluation for local energy. */
final class FermiNetVmcEvaluationReuseTest {

    @Test
    void reusedEvaluationMatchesCanonicalLocalEnergyBitForBit() {
        Molecule molecule = water();
        FermiNetV1Configuration configuration = FermiNetV1Configuration.testFixture();
        FermiNetV1State state = new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.initialize(
                        new FermiNetParameterLayout(configuration, molecule),
                        44017L));

        double[] shifts = {0.0, 0.015, -0.021, 0.033};

        for (double shift : shifts) {
            QuantumCoordinates coordinates = coordinates(shift);

            LocalEnergyComponents canonical =
                    FermiNetVmc.localEnergy(state, coordinates);

            FermiNetV1State.Evaluation evaluation =
                    state.evaluate(coordinates);

            LocalEnergyComponents reused =
                    FermiNetVmc.localEnergy(state, coordinates, evaluation);

            assertSameBits(canonical.kineticHartree(), reused.kineticHartree());
            assertSameBits(canonical.electronNuclearHartree(), reused.electronNuclearHartree());
            assertSameBits(canonical.electronElectronHartree(), reused.electronElectronHartree());
            assertSameBits(canonical.nuclearNuclearHartree(), reused.nuclearNuclearHartree());
            assertSameBits(canonical.totalHartree(), reused.totalHartree());
        }
    }

    private static void assertSameBits(double expected, double actual) {
        assertEquals(
                Double.doubleToLongBits(expected),
                Double.doubleToLongBits(actual));
    }

    private static QuantumCoordinates coordinates(double shift) {
        double[][] xyz = {
                {.18, .11, .27},
                {-.31, .42, -.16},
                {.57, -.28, .33},
                {-.63, -.37, .21},
                {.24, .71, -.45},
                {-.22, -.15, -.38},
                {.36, -.54, .19},
                {-.48, .26, .51},
                {.69, .18, -.24},
                {-.12, .61, .37}
        };

        List<QuantumCoordinates.ParticleCoordinate> result = new ArrayList<>();

        for (int i = 0; i < xyz.length; i++) {
            double signed = i % 2 == 0 ? shift : -shift;

            result.add(
                    new QuantumCoordinates.ParticleCoordinate(
                            i,
                            xyz[i][0] + signed,
                            xyz[i][1] - .5 * signed,
                            xyz[i][2] + .25 * signed,
                            i < 5 ? SpinProjection.ALPHA : SpinProjection.BETA));
        }

        return new QuantumCoordinates(result);
    }

    private static Molecule water() {
        return new Molecule(
                "ferminet-vmc-evaluation-reuse-water",
                List.of(
                        new NuclearCenter(
                                0,
                                "O",
                                new NuclearCharge(8),
                                new CartesianPosition(0, 0, 0, LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        1.7952398191849366,
                                        0,
                                        0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                2,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        -.46464225035067114,
                                        1.7340684963325879,
                                        0,
                                        LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(10),
                new SpinSector(5, 5, 1));
    }
}