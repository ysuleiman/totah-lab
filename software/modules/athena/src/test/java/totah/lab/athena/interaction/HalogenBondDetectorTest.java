package totah.lab.athena.interaction;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.interaction.InteractionFixtures.atom;
import static totah.lab.athena.interaction.InteractionFixtures.bond;
import static totah.lab.athena.interaction.InteractionFixtures.chain;
import static totah.lab.athena.interaction.InteractionFixtures.residue;
import static totah.lab.athena.interaction.InteractionFixtures.structure;

/**
 * Halogen-bond geometry: the halogen sits at the origin with its donor
 * carbon at (-1.7, 0, 0), so the X-&gt;C ray points at 180 degrees. The
 * acceptor oxygen is placed at (180 - donorAngle) degrees at a chosen
 * distance, and its neighbor Y is placed so the Y-O...X acceptor angle
 * is exact.
 */
class HalogenBondDetectorTest {

    private static final InteractionThresholds THRESHOLDS =
            InteractionThresholds.athenaDefaults();

    private final HalogenBondDetector detector = new HalogenBondDetector();

    private static Atom halogen(Element element, int serial) {
        return atom(serial, "X1", element, 0, 0, 0);
    }

    private static Atom donorCarbon() {
        return atom(102, "C1", Element.C, -1.7, 0, 0);
    }

    /** Returns {acceptor oxygen, its carbon neighbor} with exact angles. */
    private static Atom[] acceptorPair(
            int serialBase,
            double distance,
            double donorAngleDegrees,
            double acceptorAngleDegrees) {

        double oxygenDirection = Math.toRadians(180.0 - donorAngleDegrees);
        double ox = distance * Math.cos(oxygenDirection);
        double oy = distance * Math.sin(oxygenDirection);
        double neighborDirection = oxygenDirection + Math.PI
                - Math.toRadians(acceptorAngleDegrees);
        Atom oxygen = atom(serialBase, "OG", Element.O, ox, oy, 0);
        Atom neighbor = atom(serialBase + 1, "CB", Element.C,
                ox + 1.43 * Math.cos(neighborDirection),
                oy + 1.43 * Math.sin(neighborDirection), 0);
        return new Atom[] {oxygen, neighbor};
    }

    private static Structure protein(Atom... atoms) {
        List<Bond> bonds = new ArrayList<>();
        bonds.add(bond("A", 20, "OG", "CB"));
        return structure(List.of(chain("A",
                residue("SER", 20, List.of(atoms)))), bonds);
    }

    private static Structure ligand(Atom halogen, Atom carbon,
            List<Bond> extraBonds) {
        List<Bond> bonds = new ArrayList<>();
        bonds.add(bond("L", 501, halogen.getName(), carbon.getName()));
        bonds.addAll(extraBonds);
        return structure(List.of(chain("L",
                residue("LIG", 501, List.of(halogen, carbon)))), bonds);
    }

    @Test
    void detectsExactAngleHalogenBond() {
        Atom chlorine = halogen(Element.CL, 101);
        Atom carbon = donorCarbon();
        Atom[] acceptor = acceptorPair(1, 3.2, 175.0, 120.0);

        List<Interaction> bonds = detector.detect(
                protein(acceptor), ligand(chlorine, carbon, List.of()),
                THRESHOLDS);

        assertThat(bonds).singleElement().satisfies(halogenBond -> {
            assertThat(halogenBond.type())
                    .isEqualTo(InteractionType.HALOGEN_BOND);
            assertThat(halogenBond.residue().residueNumber()).isEqualTo(20);
            assertThat(halogenBond.proteinAtoms())
                    .containsExactly(acceptor[0]);
            assertThat(halogenBond.ligandAtoms())
                    .containsExactly(chlorine, carbon);
            assertThat(halogenBond.distanceAngstroms()).isCloseTo(3.2,
                    org.assertj.core.data.Offset.offset(1e-9));
            assertThat(halogenBond.primaryAngleDegrees()).isCloseTo(120.0,
                    org.assertj.core.data.Offset.offset(1e-6));
            assertThat(halogenBond.secondaryAngleDegrees()).isCloseTo(175.0,
                    org.assertj.core.data.Offset.offset(1e-6));
        });
    }

    @Test
    void fluorineQualifiesAsDonor() {
        Atom fluorine = halogen(Element.F, 101);
        Atom carbon = donorCarbon();
        Atom[] acceptor = acceptorPair(1, 3.2, 175.0, 120.0);

        assertThat(detector.detect(protein(acceptor),
                ligand(fluorine, carbon, List.of()), THRESHOLDS))
                .hasSize(1);
    }

    @Test
    void donorAngleBoundsAreInclusive() {
        Atom[] at135 = acceptorPair(1, 3.2, 135.0, 120.0);
        Atom[] at134 = acceptorPair(1, 3.2, 134.0, 120.0);

        assertThat(detector.detect(protein(at135),
                ligand(halogen(Element.CL, 101), donorCarbon(), List.of()),
                THRESHOLDS)).hasSize(1);
        assertThat(detector.detect(protein(at134),
                ligand(halogen(Element.CL, 101), donorCarbon(), List.of()),
                THRESHOLDS)).isEmpty();
    }

    @Test
    void acceptorAngleBoundsAreInclusive() {
        Atom[] at150 = acceptorPair(1, 3.2, 175.0, 150.0);
        Atom[] at151 = acceptorPair(1, 3.2, 175.0, 151.0);

        assertThat(detector.detect(protein(at150),
                ligand(halogen(Element.CL, 101), donorCarbon(), List.of()),
                THRESHOLDS)).hasSize(1);
        assertThat(detector.detect(protein(at151),
                ligand(halogen(Element.CL, 101), donorCarbon(), List.of()),
                THRESHOLDS)).isEmpty();
    }

    @Test
    void distanceMaxBoundIsInclusive() {
        Atom[] atMax = acceptorPair(1, 4.0, 175.0, 120.0);
        Atom[] beyondMax = acceptorPair(1, 4.1, 175.0, 120.0);

        assertThat(detector.detect(protein(atMax),
                ligand(halogen(Element.CL, 101), donorCarbon(), List.of()),
                THRESHOLDS)).hasSize(1);
        assertThat(detector.detect(protein(beyondMax),
                ligand(halogen(Element.CL, 101), donorCarbon(), List.of()),
                THRESHOLDS)).isEmpty();
    }

    @Test
    void halogenWithTwoBondsIsNotADonor() {
        Atom chlorine = halogen(Element.CL, 101);
        Atom carbon = donorCarbon();
        Atom extraCarbon = atom(103, "C2", Element.C, 1.7, 0, 0);
        Atom[] acceptor = acceptorPair(1, 3.2, 175.0, 120.0);
        Structure ligand = structure(List.of(chain("L",
                        residue("LIG", 501,
                                List.of(chlorine, carbon, extraCarbon)))),
                List.of(bond("L", 501, "X1", "C1"),
                        bond("L", 501, "X1", "C2")));

        assertThat(detector.detect(protein(acceptor), ligand, THRESHOLDS))
                .isEmpty();
    }

    @Test
    void etherOxygenWithTwoHeavyNeighborsIsNotAnAcceptor() {
        Atom chlorine = halogen(Element.CL, 101);
        Atom carbon = donorCarbon();
        Atom[] acceptor = acceptorPair(1, 3.2, 175.0, 120.0);
        Atom secondNeighbor = atom(3, "CG", Element.C,
                acceptor[0].getPosition().x(),
                acceptor[0].getPosition().y() - 1.43, 0);
        Structure protein = structure(List.of(chain("A",
                        residue("SER", 20, List.of(acceptor[0],
                                acceptor[1], secondNeighbor)))),
                List.of(bond("A", 20, "OG", "CB"),
                        bond("A", 20, "OG", "CG")));

        assertThat(detector.detect(protein,
                ligand(chlorine, carbon, List.of()), THRESHOLDS)).isEmpty();
    }

    @Test
    void degradedConnectivityYieldsNoHalogenBonds() {
        Atom chlorine = halogen(Element.CL, 101);
        Atom carbon = donorCarbon();
        Atom[] acceptor = acceptorPair(1, 3.2, 175.0, 120.0);
        Chain proteinChain = chain("A",
                residue("SER", 20, List.of(acceptor)));
        Chain ligandChain = chain("L",
                residue("LIG", 501, List.of(chlorine, carbon)));

        assertThat(detector.detect(structure(proteinChain),
                structure(ligandChain), THRESHOLDS)).isEmpty();
    }
}
