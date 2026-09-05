package totah.lab.athena.interaction;

import org.junit.jupiter.api.Test;
import totah.lab.athena.ligand.interaction.DefaultLigandInteractionAnalyzer;
import totah.lab.athena.ligand.interaction.LigandInteraction;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.interaction.InteractionFixtures.atom;
import static totah.lab.athena.interaction.InteractionFixtures.chain;
import static totah.lab.athena.interaction.InteractionFixtures.residue;
import static totah.lab.athena.interaction.InteractionFixtures.structure;

class HydrogenBondDetectorTest {

    private static final InteractionThresholds ATHENA =
            InteractionThresholds.athenaDefaults();
    private static final InteractionThresholds PLIP =
            InteractionThresholds.plipReference();

    private final HydrogenBondDetector detector = new HydrogenBondDetector();

    /**
     * Protein donor N-H at the origin / (1, 0, 0); ligand acceptor placed
     * on the +x axis at the given D...A distance (angle at H = 180).
     */
    private static Structure receptor(Atom... atoms) {
        return structure(chain("A",
                residue("SER", 20, List.of(atoms))));
    }

    private static Atom[] proteinDonor() {
        return new Atom[] {
                atom(1, "N", Element.N, "N", 0, 0, 0),
                atom(2, "H", Element.H, "HD", 1, 0, 0)};
    }

    private static Atom ligandAcceptor(double x) {
        return atom(101, "O1", Element.O, "OA", x, 0, 0);
    }

    private static Structure ligand(Atom... atoms) {
        return structure(chain("L",
                residue("LIG", 501, List.of(atoms))));
    }

    @Test
    void detectsProteinDonorHydrogenBond() {
        Atom[] donor = proteinDonor();
        Atom acceptor = ligandAcceptor(3.05);

        List<Interaction> bonds = detector.detect(
                receptor(donor), ligand(acceptor), ATHENA);

        assertThat(bonds).singleElement().satisfies(bond -> {
            assertThat(bond.type()).isEqualTo(InteractionType.HYDROGEN_BOND);
            assertThat(bond.residue())
                    .isEqualTo(new ResidueId("A", 20, null));
            // Donor side convention: [donor heavy atom, donor hydrogen].
            assertThat(bond.proteinAtoms())
                    .containsExactly(donor[0], donor[1]);
            assertThat(bond.ligandAtoms()).containsExactly(acceptor);
            assertThat(bond.distanceAngstroms()).isEqualTo(3.05);
            assertThat(bond.primaryAngleDegrees()).isCloseTo(180.0,
                    org.assertj.core.data.Offset.offset(1e-9));
            assertThat(bond.thresholdsProvenance()).isEqualTo(
                    InteractionThresholds.ATHENA_DEFAULTS_PROVENANCE);
        });
    }

    @Test
    void detectsLigandDonorHydrogenBond() {
        Atom proteinAcceptor = atom(1, "OG", Element.O, "OA", 0, 0, 0);
        Atom ligandDonorHeavy = atom(101, "N1", Element.N, "N", 0, 3.0, 0);
        Atom ligandHydrogen = atom(102, "H1", Element.H, "HD", 0, 2.0, 0);

        List<Interaction> bonds = detector.detect(
                receptor(proteinAcceptor),
                ligand(ligandDonorHeavy, ligandHydrogen), ATHENA);

        assertThat(bonds).singleElement().satisfies(bond -> {
            assertThat(bond.proteinAtoms())
                    .containsExactly(proteinAcceptor);
            assertThat(bond.ligandAtoms())
                    .containsExactly(ligandDonorHeavy, ligandHydrogen);
            assertThat(bond.distanceAngstroms()).isEqualTo(3.0);
        });
    }

    @Test
    void thresholdInjectionFlipsBoundaryVerdict() {
        // D...A = 3.8, H...A = 2.8, angle 180: outside the athena legacy
        // cutoffs (3.5 / 2.5), inside the PLIP reference (4.1, A...H
        // ungated).
        Atom[] donor = proteinDonor();
        Atom acceptor = ligandAcceptor(3.8);

        assertThat(detector.detect(receptor(donor), ligand(acceptor),
                ATHENA)).isEmpty();
        assertThat(detector.detect(receptor(donor), ligand(acceptor),
                PLIP)).hasSize(1);
    }

    @Test
    void angleGateDiffersBetweenThresholdSets() {
        // Angle at H = 110: below athena's 120, above PLIP's 100.
        // H->D direction is at 180 degrees; the acceptor sits at 70
        // degrees from +x, H...A = 2.2, so the D-H...A angle is 110.
        Atom[] donor = proteinDonor();
        Atom acceptor = atom(101, "O1", Element.O, "OA",
                1 + 2.2 * Math.cos(Math.toRadians(70.0)),
                2.2 * Math.sin(Math.toRadians(70.0)), 0);

        assertThat(detector.detect(receptor(donor), ligand(acceptor),
                ATHENA)).isEmpty();
        assertThat(detector.detect(receptor(donor), ligand(acceptor),
                PLIP)).hasSize(1);
    }

    @Test
    void athenaDefaultsMatchLegacyAnalyzerVerdicts() {
        Atom[] donor = proteinDonor();
        Atom ligandAcceptor = ligandAcceptor(3.05);
        Atom proteinAcceptor = atom(3, "OG", Element.O, "OA", 0, 8.0, 0);
        Atom ligandDonorHeavy = atom(102, "N1", Element.N, "N", 0, 5.0, 0);
        Atom ligandHydrogen = atom(103, "H1", Element.H, "HD", 0, 6.0, 0);
        Atom farAcceptor = atom(104, "O2", Element.O, "OA", 30, 30, 0);

        Structure receptor = structure(chain("A",
                residue("SER", 20, List.of(donor[0], donor[1])),
                residue("THR", 21, List.of(proteinAcceptor))));
        Structure ligand = ligand(ligandAcceptor, ligandDonorHeavy,
                ligandHydrogen, farAcceptor);

        List<Interaction> detected = detector.detect(
                receptor, ligand, ATHENA);
        List<LigandInteraction> legacy =
                new DefaultLigandInteractionAnalyzer().analyze(receptor,
                        new Ligand("L1", "LIG", null, null, null, null,
                                ligand))
                        .stream()
                        .filter(interaction -> interaction.type()
                                == totah.lab.athena.ligand.interaction
                                .InteractionType.HYDROGEN_BOND)
                        .toList();

        assertThat(detected).hasSameSizeAs(legacy);
        assertThat(detected).allSatisfy(bond -> assertThat(legacy)
                .anySatisfy(old -> {
                    // The heavy participants are the first atom of each
                    // side on the new records, matching the legacy
                    // receptor/ligand atom pair.
                    assertThat(bond.proteinAtoms().get(0))
                            .isSameAs(old.receptorAtom());
                    assertThat(bond.ligandAtoms().get(0))
                            .isSameAs(old.ligandAtom());
                    assertThat(bond.distanceAngstroms())
                            .isEqualTo(old.distance());
                    assertThat(bond.primaryAngleDegrees())
                            .isEqualTo(old.angleDegrees());
                    assertThat(bond.residue()).isEqualTo(old.residue());
                }));
    }

    @Test
    void donorHydrogenBeyondDonorBondCutoffYieldsNoDonorSite() {
        Atom heavy = atom(1, "N", Element.N, "N", 0, 0, 0);
        Atom looseHydrogen = atom(2, "H", Element.H, "HD", 2.0, 0, 0);
        Atom acceptor = atom(101, "O1", Element.O, "OA", 3.0, 1.0, 0);

        assertThat(detector.detect(receptor(heavy, looseHydrogen),
                ligand(acceptor), ATHENA)).isEmpty();
    }

    @Test
    void nonAcceptorAd4TypesAreIgnored() {
        Atom[] donor = proteinDonor();
        Atom plainOxygen = atom(101, "O1", Element.O, "O", 3.0, 0, 0);

        assertThat(detector.detect(receptor(donor),
                ligand(plainOxygen), ATHENA)).isEmpty();
    }
}
