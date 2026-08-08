package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class PoseComparisonTestFixtures {

    private PoseComparisonTestFixtures() {
    }

    static Ligand ligand(String id, String[] names, double[][] positions) {
        List<Atom> atoms = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            atoms.add(Atom.builder()
                    .pdbSerial(index + 1)
                    .name(names[index])
                    .position(point(positions[index]))
                    .charge(0.0)
                    .occupancy(1.0)
                    .bFactor(0.0)
                    .element(Element.C)
                    .build());
        }
        Structure structure = new Structure(List.of(new Chain("L", List.of(
                new Residue("LIG", 1, atoms)))));
        return new Ligand(id, id, null, null, null, null, structure);
    }

    static Pocket pocket(String id, double[][] positions) {
        List<AlphaSphere> spheres = new ArrayList<>();
        for (int index = 0; index < positions.length; index++) {
            spheres.add(new AlphaSphere(index + 1L, point(positions[index]), 1.5));
        }
        return new Pocket(new PocketId(id), "pocket-" + id,
                PocketSource.FPOCKET, point(positions[0]), List.of(), List.of(),
                Optional.empty(), Optional.of(new AlphaSphereSet(spheres)), Map.of());
    }

    private static Point3D point(double[] coordinates) {
        return new Point3D(coordinates[0], coordinates[1], coordinates[2]);
    }
}
