package totah.lab.pocket.visualization;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PocketSliceRendererTest {
    @Test
    void rendersResidueDerivedPocketWithoutFabricatingAlphaSpheres() {
        Residue residue = new Residue(
                "GLY",
                1,
                List.of(atom(1, new Point3D(0, 0, 0)),
                        atom(2, new Point3D(2, 2, 1))));
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(residue))));
        Pocket pocket = new Pocket(
                new PocketId("1"),
                "Pocket 1",
                PocketSource.P2RANK,
                new Point3D(1, 1, 0.5),
                List.of(new ResidueId("A", 1, null)),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Map.of());
        PocketProjection.SlicePlane plane =
                new PocketProjection.SlicePlane(
                        pocket.center(),
                        new PocketPca.Vector3D(1, 0, 0),
                        new PocketPca.Vector3D(0, 1, 0),
                        new PocketPca.Vector3D(0, 0, 1));

        BufferedImage image = new PocketSliceRenderer().renderProjection(
                structure,
                pocket,
                plane,
                PocketSliceRenderer.RenderOptions.defaults());

        assertThat(image.getWidth()).isPositive();
        assertThat(image.getHeight()).isPositive();
        assertThat(pocket.alphaSphereSet()).isEmpty();
    }

    private static Atom atom(int serial, Point3D position) {
        return Atom.builder()
                .pdbSerial(serial)
                .name("C" + serial)
                .position(position)
                .element(Element.C)
                .build();
    }
}
