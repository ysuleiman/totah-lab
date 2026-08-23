package totah.lab.prometheus.neural.ferminet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FermiNetDeterminantSemanticsTest {

    @Test
    void smallNonzeroHeadRemainsAValidContribution() {
        FermiNetSpatialJet determinant = FermiNetV1State.determinant(matrix(
                constant(1.0e-15), constant(0.0),
                constant(0.0), constant(1.0)));
        FermiNetSpatialJet wavefunction = determinant.add(constant(2.0));

        assertEquals(1.0e-15, determinant.value(), 0.0);
        assertTrue(Double.isFinite(Math.log(Math.abs(wavefunction.value()))));
        assertEquals(2.000000000000001, wavefunction.value(), 0.0);
    }

    @Test
    void exactlySingularHeadRetainsNonzeroCoordinateDerivatives() {
        FermiNetSpatialJet coordinate = FermiNetSpatialJet.variable(1.0, 1, 0);
        FermiNetSpatialJet squared = coordinate.multiply(coordinate);
        FermiNetSpatialJet determinant = FermiNetV1State.determinant(matrix(
                squared, constant(1.0), constant(1.0), constant(1.0)));
        FermiNetSpatialJet wavefunction = determinant.add(constant(2.0));

        assertEquals(0.0, determinant.value(), 0.0);
        assertEquals(2.0, determinant.gradient()[0], 0.0);
        assertEquals(2.0, determinant.laplacian(), 0.0);
        assertEquals(1.0, wavefunction.gradient()[0] / wavefunction.value(), 0.0);
        assertEquals(1.0, wavefunction.laplacian() / wavefunction.value(), 0.0);
    }

    @Test
    void singularHeadDirectionalAndBatchedTangentsRemainDefined() {
        FermiNetSpatialJet directional = FermiNetSpatialJet.directionalVariable(
                1.0, 1, 0, 3.0);
        FermiNetSpatialJet reference = FermiNetV1State.determinant(matrix(
                directional, constant(1.0), constant(1.0), constant(1.0)));
        assertEquals(0.0, reference.value(), 0.0);
        assertEquals(3.0, reference.directionalValue(), 0.0);

        FermiNetBatchedJetWorkspace workspace = new FermiNetBatchedJetWorkspace();
        FermiNetSpatialJet batched = FermiNetBatchedSpatialJet.variable(
                workspace, 1.0, 1, 0, new double[] {2.0, -1.0});
        FermiNetBatchedSpatialJet result = (FermiNetBatchedSpatialJet)
                FermiNetV1State.determinant(matrix(
                        batched,
                        FermiNetBatchedSpatialJet.constant(
                                workspace, 1.0, 1, new double[2]),
                        FermiNetBatchedSpatialJet.constant(
                                workspace, 1.0, 1, new double[2]),
                        FermiNetBatchedSpatialJet.constant(
                                workspace, 1.0, 1, new double[2])));
        assertEquals(0.0, result.value(), 0.0);
        assertEquals(2.0, result.directionalValue(0), 0.0);
        assertEquals(-1.0, result.directionalValue(1), 0.0);
    }

    @Test
    void rankNMinusOneCofactorMatchesFiniteDifference() {
        double[][] singular = {{1.0, 1.0}, {1.0, 1.0}};
        double epsilon = 1.0e-6;
        double[][] plus = copy(singular);
        double[][] minus = copy(singular);
        plus[0][0] += epsilon;
        minus[0][0] -= epsilon;
        double finiteDifference = (FermiNetV1State.divisionFreeDeterminant(plus)
                - FermiNetV1State.divisionFreeDeterminant(minus))
                / (2.0 * epsilon);

        assertEquals(0.0,
                FermiNetV1State.divisionFreeDeterminant(singular), 0.0);
        assertEquals(1.0, FermiNetV1State.cofactor(singular, 0, 0), 0.0);
        assertEquals(1.0, finiteDifference, 1.0e-10);
    }

    @Test
    void multipleSingularHeadsCanHaveFiniteTotalWavefunction() {
        FermiNetSpatialJet first = FermiNetV1State.determinant(matrix(
                constant(1.0), constant(1.0), constant(1.0), constant(1.0)));
        FermiNetSpatialJet second = FermiNetV1State.determinant(matrix(
                constant(2.0), constant(2.0), constant(3.0), constant(3.0)));
        FermiNetSpatialJet ordinary = FermiNetV1State.determinant(matrix(
                constant(2.0), constant(0.0), constant(0.0), constant(3.0)));

        assertEquals(0.0, first.value(), 0.0);
        assertEquals(0.0, second.value(), 0.0);
        assertEquals(6.0, first.add(second).add(ordinary).value(), 0.0);
    }

    @Test
    void genuineTotalNodeLeavesLogAndRatiosUndefined() {
        FermiNetSpatialJet node = FermiNetV1State.determinant(matrix(
                constant(1.0), constant(1.0), constant(1.0), constant(1.0)));
        assertEquals(0.0, node.value(), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, Math.log(Math.abs(node.value())));
        assertTrue(Double.isNaN(node.gradient()[0] / node.value()));
    }

    @Test
    void ordinaryFastAndDivisionFreeDeterminantsAgree() {
        double[][] values = {
                {2.0, -1.0, 0.5},
                {0.25, 3.0, -2.0},
                {1.5, 0.75, 4.0}
        };
        FermiNetSpatialJet[][] jets = new FermiNetSpatialJet[3][3];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                jets[row][column] = constant(values[row][column]);
            }
        }
        assertEquals(FermiNetV1State.divisionFreeDeterminant(values),
                FermiNetV1State.determinant(jets).value(), 1.0e-15);
    }

    private static FermiNetSpatialJet constant(double value) {
        return FermiNetSpatialJet.constant(value, 1);
    }

    private static FermiNetSpatialJet[][] matrix(
            FermiNetSpatialJet a00, FermiNetSpatialJet a01,
            FermiNetSpatialJet a10, FermiNetSpatialJet a11) {
        return new FermiNetSpatialJet[][] {{a00, a01}, {a10, a11}};
    }

    private static double[][] copy(double[][] values) {
        return new double[][] {values[0].clone(), values[1].clone()};
    }
}
