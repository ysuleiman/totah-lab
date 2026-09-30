package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.aether.basis.Sto3gHydrogen;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

/** Package-level fault injection checks guards beneath the immutable public evidence producers. */
class AetherOccupiedDensityGuardsTest {
    private static OneShotRhfResult source() throws IOException {
        var h=Sto3gHydrogen.load();
        var centers=List.of(new Point3D(0,0,0),new Point3D(1.4,0,0));
        var system=new QuantumSystem(centers.stream().map(p->new NuclearCenter(p,1)).toList(),0,1);
        var basis=centers.stream().map(h::atBohr).toList();
        var density=DensityMatrix.fromRowMajor(system,basis,List.of(0.,0.,0.,0.));
        return OneShotRhfCalculator.solve(density,OverlapMatrix.compute(basis),
                new CoreHamiltonianCalculator(system,basis).calculate(),
                JkCalculator.calculate(density,new ElectronRepulsionCalculator(system,basis).calculate()));
    }
    private static MolecularOrbitalCoefficients scaled(OneShotRhfResult source,double scale) {
        var data=IntegralMatrixData.capture(source.density().functions(),(i,j)->scale*source.coefficients().get(i,j),
                "test-fault","test-fault","test-fault","test-fault","");
        return new MolecularOrbitalCoefficients(data,source.receipt());
    }
    @ParameterizedTest @ValueSource(doubles={0,2,1e200})
    void unnormalizedAndOverflowingCoefficientsFailClosed(double scale) throws IOException {
        var r=source(); var s=OverlapMatrix.compute(r.density().functions());
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.build(r.density().system(),s,scaled(r,scale),r.energies()));
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void nonfiniteCoefficientCaptureAndOrbitalEnergiesFailClosed(double value) throws IOException {
        var r=source(); var s=OverlapMatrix.compute(r.density().functions());
        assertThrows(IllegalArgumentException.class,()->scaled(r,value));
        var bad=new OrbitalEnergies(new SpectralValues(List.of(value,1.),"fault"),r.receipt());
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.build(r.density().system(),s,r.coefficients(),bad));
    }
    @Test void descendingOrbitalEnergiesAreNotSilentlyReoccupied() throws IOException {
        var r=source(); var s=OverlapMatrix.compute(r.density().functions());
        var descending=new OrbitalEnergies(SpectralValues.capture(new double[]{1,-1},"test"),r.receipt());
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.build(r.density().system(),s,r.coefficients(),descending));
    }
    @Test void nonorthogonalColumnsFailEvenWhenEachColumnIsNormalized() throws IOException {
        var r=source(); var s=OverlapMatrix.compute(r.density().functions());
        var data=IntegralMatrixData.capture(r.density().functions(),(i,j)->r.coefficients().get(i,0),
                "fault","fault","fault","fault","");
        var c=new MolecularOrbitalCoefficients(data,r.receipt());
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.build(r.density().system(),s,c,r.energies()));
    }
}
