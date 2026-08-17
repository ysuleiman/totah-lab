package totah.lab.athena.fragment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;

class FragmentRegionFitTest {
    @Test
    void preservesIndependentSpatialEvidenceDimensions() {
        var fit = new FragmentRegionFit("F001", "C1", "REGION_1", .35, .65, .2, .4,
                .7, 4.0, .8, 3.2, true, Set.of(FragmentPocketChemistry.HYDROPHOBIC),
                List.of(), List.of(new SpatialAttachmentVector(0, new Point3D(0, 0, 0), new Vector3D(1, 0, 0))));

        assertEquals(.35, fit.fragmentVolumeFractionInRequestedRegion());
        assertEquals(.65, fit.fragmentVolumeFractionInCommonCavity());
        assertTrue(fit.functionalInterference());
    }

    @Test
    void rejectsInvalidOccupancyFractions() {
        assertThrows(IllegalArgumentException.class, () -> new FragmentRegionFit("F", "C", "R", 1.1,
                0, 0, 0, 0, 0, 0, 0, false, Set.of(), List.of(), List.of()));
    }
}
