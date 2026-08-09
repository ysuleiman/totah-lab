package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared synthetic fixtures for the architecture tests: one irregular
 * six-residue receptor and one connected, anisotropic alpha-sphere
 * pocket, plus transform helpers for building rigidly moved copies.
 */
final class ArchitectureTestFixtures {

    private ArchitectureTestFixtures() {
    }

    static final String[] RESIDUE_NAMES = {
            "ALA", "LEU", "SER", "LYS", "VAL", "GLY"
    };

    static final double[][] CA_POSITIONS = {
            {0, 0, 0},
            {9, 1, 2},
            {2, 8, 1},
            {5, 3, 9},
            {11, 7, 4},
            {3, 12, 6}
    };

    /**
     * Connected (surface gaps below 1 A at radius 1.5), anisotropic
     * sphere cluster spanning x = 0..6.
     */
    static final double[][] BASE_SPHERES = {
            {0, 0, 0},
            {3, 0, 0},
            {6, 0, 0},
            {1, 2, 0},
            {4, 2, 0},
            {1, -2, 0},
            {4, -2, 0},
            {3, 1, 2}
    };

    static final double SPHERE_RADIUS = 1.5;

    /** 90 degrees about z, then translation. */
    static final RigidTransform TRANSFORM = new RigidTransform(
            0, -1, 0,
            1, 0, 0,
            0, 0, 1,
            new Point3D(10, -4, 6)
    );

    static Atom atom(int serial, String name, Point3D position) {
        return Atom.builder()
                .pdbSerial(serial)
                .name(name)
                .position(position)
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.C)
                .build();
    }

    /** Six-residue single-chain receptor with CA-only residues. */
    static Structure receptor() {
        List<Residue> residues = new ArrayList<>();

        for (int index = 0; index < RESIDUE_NAMES.length; index++) {
            residues.add(new Residue(
                    RESIDUE_NAMES[index],
                    index + 1,
                    List.of(atom(100 + index, "CA",
                            point(CA_POSITIONS[index])))
            ));
        }

        return new Structure(List.of(new Chain("A", residues)));
    }

    /** Receptor whose residue {@code residueNumber} CA is shifted. */
    static Structure receptorWithShiftedCa(
            int residueNumber,
            double dx,
            double dy,
            double dz
    ) {
        List<Residue> residues = new ArrayList<>();

        for (int index = 0; index < RESIDUE_NAMES.length; index++) {
            double[] position = CA_POSITIONS[index];
            double x = position[0];
            double y = position[1];
            double z = position[2];

            if (index + 1 == residueNumber) {
                x += dx;
                y += dy;
                z += dz;
            }

            residues.add(new Residue(
                    RESIDUE_NAMES[index],
                    index + 1,
                    List.of(atom(100 + index, "CA",
                            new Point3D(x, y, z)))
            ));
        }

        return new Structure(List.of(new Chain("A", residues)));
    }

    /** Six-residue receptor with CA + one CB side-chain atom each. */
    static Structure receptorWithSideChains(
            int shiftedResidueNumber,
            double cbShiftX
    ) {
        List<Residue> residues = new ArrayList<>();

        for (int index = 0; index < RESIDUE_NAMES.length; index++) {
            double[] ca = CA_POSITIONS[index];
            double cbX = ca[0];
            double cbZ = ca[2] + 1.5;

            if (index + 1 == shiftedResidueNumber) {
                cbX += cbShiftX;
            }

            residues.add(new Residue(
                    RESIDUE_NAMES[index],
                    index + 1,
                    List.of(
                            atom(100 + index, "CA", point(ca)),
                            atom(200 + index, "CB",
                                    new Point3D(cbX, ca[1], cbZ))
                    )
            ));
        }

        return new Structure(List.of(new Chain("A", residues)));
    }

    /**
     * Six-residue receptor whose CA/CB atoms hug the base sphere
     * cluster (CA one unit above sphere i, CB two units above), so
     * every sphere has a near wall atom; residue
     * {@code shiftedResidueNumber}'s CB is shifted in x.
     */
    static Structure receptorWithSideChainsNearSpheres(
            int shiftedResidueNumber,
            double cbShiftX
    ) {
        List<Residue> residues = new ArrayList<>();

        for (int index = 0; index < RESIDUE_NAMES.length; index++) {
            double[] sphere = BASE_SPHERES[index];
            double cbX = sphere[0];

            if (index + 1 == shiftedResidueNumber) {
                cbX += cbShiftX;
            }

            residues.add(new Residue(
                    RESIDUE_NAMES[index],
                    index + 1,
                    List.of(
                            atom(100 + index, "CA", new Point3D(
                                    sphere[0], sphere[1] + 1,
                                    sphere[2])),
                            atom(200 + index, "CB", new Point3D(
                                    cbX, sphere[1] + 2, sphere[2]))
                    )
            ));
        }

        return new Structure(List.of(new Chain("A", residues)));
    }

    /** FPOCKET pocket over all six residues with the given spheres. */
    static Pocket pocket(String id, double[][] spherePositions) {
        List<ResidueId> residues = new ArrayList<>();
        for (int index = 0; index < RESIDUE_NAMES.length; index++) {
            residues.add(new ResidueId("A", index + 1, null));
        }

        List<AlphaSphere> spheres = new ArrayList<>();
        for (int index = 0; index < spherePositions.length; index++) {
            spheres.add(new AlphaSphere(
                    index + 1L,
                    point(spherePositions[index]),
                    SPHERE_RADIUS
            ));
        }

        return new Pocket(
                new PocketId(id),
                "pocket-" + id,
                PocketSource.FPOCKET,
                point(spherePositions[0]),
                residues,
                List.of(),
                Optional.empty(),
                Optional.of(new AlphaSphereSet(spheres)),
                Map.of()
        );
    }

    static Structure transformed(
            Structure structure,
            RigidTransform transform
    ) {
        List<Chain> chains = new ArrayList<>();

        for (Chain chain : structure.getChains()) {
            List<Residue> residues = new ArrayList<>();

            for (Residue residue : chain.residues()) {
                List<Atom> atoms = new ArrayList<>();

                for (Atom atom : residue.getAtoms()) {
                    atoms.add(atom.toBuilder()
                            .position(transform.apply(
                                    atom.getPosition()))
                            .build());
                }

                residues.add(new Residue(
                        residue.getName(),
                        residue.getNumber(),
                        residue.getInsertionCode(),
                        atoms
                ));
            }

            chains.add(new Chain(chain.id(), residues));
        }

        return new Structure(chains);
    }

    static Pocket transformed(Pocket pocket, RigidTransform transform) {
        List<AlphaSphere> spheres = pocket.alphaSphereSet()
                .orElseThrow()
                .spheres()
                .stream()
                .map(sphere -> new AlphaSphere(
                        sphere.id(),
                        transform.apply(sphere.center()),
                        sphere.radius()
                ))
                .toList();

        return new Pocket(
                pocket.id(),
                pocket.name(),
                pocket.source(),
                transform.apply(pocket.center()),
                pocket.residues(),
                pocket.metrics(),
                pocket.bounds(),
                Optional.of(new AlphaSphereSet(spheres)),
                pocket.metadata()
        );
    }

    static Ligand pose(String id, double[][] positions) {
        List<Atom> atoms = new ArrayList<>();

        for (int index = 0; index < positions.length; index++) {
            atoms.add(atom(1 + index, "C" + (index + 1),
                    point(positions[index])));
        }

        Residue residue = new Residue("LIG", 1, atoms);
        Structure structure = new Structure(
                List.of(new Chain("L", List.of(residue))));

        return new Ligand(id, id, null, null, null, null, structure);
    }

    /** Ligand with explicit atom names (all carbon), in given order. */
    static Ligand ligand(String id, String[] names, double[][] positions) {
        List<Atom> atoms = new ArrayList<>();

        for (int index = 0; index < names.length; index++) {
            atoms.add(atom(1 + index, names[index],
                    point(positions[index])));
        }

        Residue residue = new Residue("LIG", 1, atoms);
        Structure structure = new Structure(
                List.of(new Chain("L", List.of(residue))));

        return new Ligand(id, id, null, null, null, null, structure);
    }

    static Point3D point(double[] coordinates) {
        return new Point3D(
                coordinates[0],
                coordinates[1],
                coordinates[2]
        );
    }
}
