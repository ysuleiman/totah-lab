package totah.lab.prometheus.neural.ferminet.force;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.molecular.*;

final class FermiNetNuclearForceValidationTest {
    @Test void reportsFiniteTranslationAndTorqueSeparatelyForAsymmetricCharges() {
        Molecule molecule = molecule(2.0e-12);
        NuclearForceResult result = result(new double[][]{{1, 2, 3}, {-4, 5, -6}});
        var finite = FermiNetNuclearForceValidation.validate(molecule, result);
        var physical = FermiNetNuclearForceValidation.physicalDiagnostics(molecule, result);
        assertTrue(finite.completeFiniteVector());
        assertTrue(finite.planarGeometry());
        assertEquals(-3.0, physical.netForceHartreePerBohr().x(), 0.0);
        assertEquals(7.0, physical.netForceHartreePerBohr().y(), 0.0);
        assertEquals(-3.0, physical.netForceHartreePerBohr().z(), 0.0);
        assertTrue(physical.torqueNorm() > 0.0);
    }

    @Test void geometryOutsidePrecisionDerivedPlanarityToleranceIsNotPlanar() {
        assertFalse(FermiNetNuclearForceValidation.validate(
                molecule(2.0 * FermiNetNuclearForceValidation
                        .PLANAR_GEOMETRY_TOLERANCE_BOHR),
                result(new double[][]{{1, 2, 3}, {-4, 5, -6}}))
                .planarGeometry());
    }

    private static Molecule molecule(double secondZ) {
        return new Molecule("asymmetric", List.of(
                new NuclearCenter(0,"O",new NuclearCharge(8),
                        new CartesianPosition(0.2,-0.3,0,LengthUnit.BOHR)),
                new NuclearCenter(1,"H",new NuclearCharge(1),
                        new CartesianPosition(1.7,0.4,secondZ,LengthUnit.BOHR))),
                new MolecularCharge(0),new ElectronCount(9),new SpinSector(5,4,2));
    }

    private static NuclearForceResult result(double[][] force) {
        List<NuclearForceResult.Component> components = new ArrayList<>();
        var tails = new NuclearForceResult.TailDiagnostics(0,0,0,0,0,0,0,0,0);
        for (int atom=0; atom<force.length; atom++) for (int axis=0; axis<3; axis++)
            components.add(new NuclearForceResult.Component(atom,axis,"xyz".substring(axis,axis+1),
                    force[atom][axis],0,0,2,0,tails,"0".repeat(64),new double[]{force[atom][axis],force[atom][axis]}));
        return new NuclearForceResult(NuclearForceEstimatorType.SWCT,"test",
                "0".repeat(64),"1".repeat(64),"2".repeat(64),"3".repeat(64),"4".repeat(64),
                2,2,1,components,new NuclearForceResult.SwctDiagnostics(0,List.of()));
    }
}
