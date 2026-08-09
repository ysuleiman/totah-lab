package totah.lab.athena.ligand.selectivity;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.contact;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.pose;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.receptor;

class ContactStringRendererTest {

    private final DefaultLigandContactAlignmentAnalyzer analyzer =
            new DefaultLigandContactAlignmentAnalyzer();
    private final ContactStringRenderer renderer =
            new ContactStringRenderer();

    @Test
    void rendersContactStringsNumbersAndDiffExactly() {
        Structure receptorA = receptor(10, "ALA", "PHE", "GLY");
        Structure receptorB = receptor(7, "ALA", "PHE", "SER");

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(),
                List.of(contact(10), contact(11), contact(12)),
                receptorB, pose(),
                List.of(contact(7), contact(8), contact(9)),
                null, null
        );

        assertThat(renderer.render(alignment)).isEqualTo(
                "A: AFG\n"
                        + "A residues: 10 11 12\n"
                        + "B: AFS\n"
                        + "B residues: 7 8 9\n"
                        + "diff: ||."
        );
    }

    @Test
    void oneLetterCodesCoverTheStandardResidues() {
        assertThat(ContactStringRenderer.oneLetter("PHE")).isEqualTo("F");
        assertThat(ContactStringRenderer.oneLetter("arg")).isEqualTo("R");
        assertThat(ContactStringRenderer.oneLetter("MSE")).isEqualTo("X");
    }
}
