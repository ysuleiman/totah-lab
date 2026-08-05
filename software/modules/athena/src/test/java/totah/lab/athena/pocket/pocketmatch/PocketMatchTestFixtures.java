package totah.lab.athena.pocket.pocketmatch;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Synthetic structure builders for PocketMatch tests. Coordinates are
 * arbitrary but physically spaced (a few angstroms apart).
 */
final class PocketMatchTestFixtures {

    private static final AtomicInteger SERIAL = new AtomicInteger(1);

    private PocketMatchTestFixtures() {
    }

    static Atom atom(
            String name,
            Element element,
            double x,
            double y,
            double z
    ) {
        return new Atom(
                SERIAL.getAndIncrement(),
                name,
                null,
                null,
                new Point3D(x, y, z),
                0.0,
                1.0,
                0.0,
                element,
                null
        );
    }

    /**
     * A residue with a complete backbone plus the given side-chain
     * heavy atoms, rooted near the given origin.
     */
    static Residue residue(
            String name,
            int number,
            double originX,
            double originY,
            double originZ,
            List<Atom> sideChain
    ) {
        List<Atom> atoms = new ArrayList<>(List.of(
                atom("N", Element.N, originX - 1.2, originY, originZ),
                atom("CA", Element.C, originX, originY, originZ),
                atom("C", Element.C, originX + 0.4, originY + 1.4, originZ),
                atom("O", Element.O, originX + 0.4, originY + 2.6, originZ)
        ));
        atoms.addAll(sideChain);
        return new Residue(name, number, null, atoms);
    }

    static Residue residueWithoutBetaCarbon(
            String name,
            int number,
            double originX,
            double originY,
            double originZ
    ) {
        return residue(name, number, originX, originY, originZ, List.of());
    }

    static Residue alanine(
            int number,
            double originX,
            double originY,
            double originZ
    ) {
        return residue(
                "ALA",
                number,
                originX,
                originY,
                originZ,
                List.of(atom(
                        "CB",
                        Element.C,
                        originX - 0.5,
                        originY - 0.9,
                        originZ + 0.8
                ))
        );
    }

    static Structure structureOf(Residue... residues) {
        return new Structure(List.of(new Chain("A", List.of(residues))));
    }

    static Pocket pocketOfResidueNumbers(
            Structure structure,
            int... residueNumbers
    ) {
        List<ResidueId> ids = new ArrayList<>();
        for (int number : residueNumbers) {
            ids.add(new ResidueId("A", number, null));
        }
        return new Pocket(
                PocketId.of(1),
                "test-pocket",
                PocketSource.FPOCKET,
                new Point3D(0.0, 0.0, 0.0),
                ids,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Map.of()
        );
    }
}
