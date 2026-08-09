package totah.lab.athena.ligand.interaction;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLigandInteractionAnalyzerTest {

    private final DefaultLigandInteractionAnalyzer analyzer =
            new DefaultLigandInteractionAnalyzer();

    @Test
    void detectsHydrogenBondFromPreparedAd4TypesAndGeometry() {
        Structure receptor = structure("SER", 42, List.of(
                atom("OG", Element.O, "O", 0.0, 0.0, 0.0, 0.0),
                atom("HG", Element.H, "HD", 1.0, 0.0, 0.0, 0.1)
        ));
        Ligand ligand = ligand(List.of(
                atom("O1", Element.O, "OA", 2.5, 0.0, 0.0, -0.4)
        ));

        assertThat(analyzer.analyze(receptor, ligand))
                .singleElement()
                .satisfies(interaction -> {
                    assertThat(interaction.type())
                            .isEqualTo(InteractionType.HYDROGEN_BOND);
                    assertThat(interaction.residue().residueNumber())
                            .isEqualTo(42);
                    assertThat(interaction.distance()).isEqualTo(2.5);
                    assertThat(interaction.angleDegrees()).isEqualTo(180.0);
                });
    }

    @Test
    void rejectsHydrogenBondWithBadDonorHydrogenAcceptorAngle() {
        Structure receptor = structure("SER", 42, List.of(
                atom("OG", Element.O, "O", 0.0, 0.0, 0.0, 0.0),
                atom("HG", Element.H, "HD", 1.0, 0.0, 0.0, 0.1)
        ));
        Ligand ligand = ligand(List.of(
                atom("O1", Element.O, "OA", 0.0, 1.5, 0.0, -0.4)
        ));

        assertThat(analyzer.analyze(receptor, ligand)).isEmpty();
    }

    @Test
    void detectsSaltBridgeFromOppositePreparedChargeSums() {
        Structure receptor = structure("LYS", 10, List.of(
                atom("NZ", Element.N, "N", 0.0, 0.0, 0.0, 1.0)
        ));
        Ligand ligand = ligand(List.of(
                atom("O1", Element.O, "OA", 3.0, 0.0, 0.0, -1.0)
        ));

        assertThat(analyzer.analyze(receptor, ligand))
                .singleElement()
                .satisfies(interaction -> {
                    assertThat(interaction.type())
                            .isEqualTo(InteractionType.SALT_BRIDGE);
                    assertThat(interaction.distance()).isEqualTo(3.0);
                });
    }

    private static Structure structure(
            String residueName,
            int residueNumber,
            List<Atom> atoms
    ) {
        return new Structure(List.of(new Chain("A", List.of(
                new Residue(residueName, residueNumber, atoms)))));
    }

    private static Ligand ligand(List<Atom> atoms) {
        return new Ligand("L", "Ligand", null, null, null, null,
                structure("LIG", 1, atoms));
    }

    private static Atom atom(
            String name,
            Element element,
            String autoDockType,
            double x,
            double y,
            double z,
            double charge
    ) {
        return Atom.builder()
                .pdbSerial(1)
                .name(name)
                .element(element)
                .autoDockType(autoDockType)
                .position(new Point3D(x, y, z))
                .charge(charge)
                .occupancy(1.0)
                .build();
    }
}
