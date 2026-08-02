package totah.lab.athena.pocket.selection;

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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PocketResidueSelectionTest {
    private final PocketResidueSelection selection =
            new PocketResidueSelection();

    @Test
    void reportsMissingIdsWithoutHidingResolvedResidues() {
        Residue present = residue("GLY", 1, 0.0);
        Structure structure = structure(present);
        ResidueId presentId = new ResidueId("A", 1, null);
        ResidueId missingId = new ResidueId("A", 99, null);
        Pocket pocket = pocket(List.of(presentId, missingId));

        assertThat(selection.resolvedResidues(structure, pocket))
                .containsExactly(present);
        assertThat(selection.unresolvedResidues(structure, pocket))
                .containsExactly(missingId);
    }

    @Test
    void liningCutoffUsesResolvedPocketResidueHeavyAtoms() {
        Residue pocketResidue = residue("GLY", 1, 100.0);
        Residue insideCutoff = residue("SER", 2, 103.9);
        Residue outsideCutoff = residue("LEU", 3, 104.1);
        Structure structure = structure(
                pocketResidue, insideCutoff, outsideCutoff);
        Pocket pocket = pocket(List.of(new ResidueId("A", 1, null)));

        assertThat(selection.liningResidues(structure, pocket, 4.0))
                .containsExactly(pocketResidue, insideCutoff);
    }

    private static Structure structure(Residue... residues) {
        return new Structure(List.of(new Chain("A", List.of(residues))));
    }

    private static Residue residue(String name, int number, double x) {
        Atom atom = Atom.builder()
                .pdbSerial(number)
                .name("CA")
                .position(new Point3D(x, 0, 0))
                .element(Element.C)
                .build();
        return new Residue(name, number, List.of(atom));
    }

    private static Pocket pocket(List<ResidueId> residues) {
        return new Pocket(
                new PocketId("1"),
                "Pocket 1",
                PocketSource.MANUAL,
                new Point3D(0, 0, 0),
                residues,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Map.of());
    }
}
