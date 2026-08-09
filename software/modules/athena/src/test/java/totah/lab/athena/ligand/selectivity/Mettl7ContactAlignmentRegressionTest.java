package totah.lab.athena.ligand.selectivity;

import org.junit.jupiter.api.Test;
import totah.lab.athena.ligand.selectivity.MutationCandidate.MutationDirection;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.atom;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.contact;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.pose;
import totah.lab.gaia.geometry.Point3D;

/**
 * Regression against the verified METTL7A/METTL7B contact region:
 * 7A 36-43 F P Y F L V R F versus 7B 36-43 F P Y L M A V L, with
 * conserved 36-38 and divergent 39 F/L, 40 L/M, 41 V/A, 42 R/V,
 * 43 F/L.
 */
class Mettl7ContactAlignmentRegressionTest {

    private final DefaultLigandContactAlignmentAnalyzer analyzer =
            new DefaultLigandContactAlignmentAnalyzer();

    @Test
    void reproducesTheVerified36To43MappingAndCandidates() {
        Structure receptorA =
                receptorFromSequence("/mettl7/query_sequence.csv");
        Structure receptorB =
                receptorFromSequence("/mettl7/candidate_sequence.csv");

        List<totah.lab.athena.ligand.contact.LigandContact> contacts =
                new ArrayList<>();
        for (int residueNumber = 36; residueNumber <= 43;
                residueNumber++) {
            contacts.add(contact(residueNumber));
        }

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(), contacts,
                receptorB, pose(), contacts,
                null, null
        );

        Map<Integer, AlignedLigandContact> rowsByResidueA =
                alignment.contacts().stream()
                        .filter(row -> row.residueAId() != null)
                        .collect(Collectors.toMap(
                                row -> row.residueAId().residueNumber(),
                                Function.identity(),
                                (first, second) -> first
                        ));

        for (int residueNumber = 36; residueNumber <= 38;
                residueNumber++) {
            AlignedLigandContact row =
                    rowsByResidueA.get(residueNumber);
            assertThat(row.differentialType())
                    .isEqualTo(DifferentialContactType.CONSERVED_CONTACT);
            assertThat(row.residueA()).isEqualTo(row.residueB());
            assertThat(row.residueBId().residueNumber())
                    .isEqualTo(residueNumber);
        }

        assertDivergent(rowsByResidueA.get(39), "PHE", "LEU");
        assertDivergent(rowsByResidueA.get(40), "LEU", "MET");
        assertDivergent(rowsByResidueA.get(41), "VAL", "ALA");
        assertDivergent(rowsByResidueA.get(42), "ARG", "VAL");
        assertDivergent(rowsByResidueA.get(43), "PHE", "LEU");

        // The contact strings reproduce the verified 7A/7B sequences.
        String rendered = new ContactStringRenderer().render(alignment);
        assertThat(rendered).contains("A: FPYFLVRF");
        assertThat(rendered).contains("B: FPYLMAVL");
        assertThat(rendered).contains("diff: |||.....");

        // Exactly the five divergent 7A->7B singles.
        List<MutationCandidate> aToB = new MutationCandidateRanker()
                .rank(alignment).stream()
                .filter(candidate -> candidate.direction()
                        == MutationDirection.A_TO_B)
                .toList();

        assertThat(aToB).hasSize(5);
        assertThat(aToB.stream().map(MutationCandidate::label))
                .containsExactly("F39L", "R42V", "F43L", "L40M", "V41A");
    }

    private static void assertDivergent(
            AlignedLigandContact row,
            String residueA,
            String residueB
    ) {
        assertThat(row).isNotNull();
        assertThat(row.differentialType()).isEqualTo(
                DifferentialContactType.CONTACT_BOTH_DIFFERENT_RESIDUE);
        assertThat(row.residueA()).isEqualTo(residueA);
        assertThat(row.residueB()).isEqualTo(residueB);
        assertThat(row.residueBId().residueNumber())
                .isEqualTo(row.residueAId().residueNumber());
    }

    private static Structure receptorFromSequence(String resource) {
        List<Residue> residues = new ArrayList<>();
        int serial = 1;

        for (String line : readLines(resource)) {
            String[] columns = line.split(",");
            int number = Integer.parseInt(columns[0]);
            residues.add(new Residue(
                    columns[1],
                    number,
                    List.of(atom(serial++, "CA",
                            new Point3D(number * 3.8, 0, 0)))
            ));
        }

        return new Structure(List.of(new Chain("A", residues)));
    }

    private static List<String> readLines(String resource) {
        InputStream input = Mettl7ContactAlignmentRegressionTest.class
                .getResourceAsStream(resource);

        if (input == null) {
            throw new IllegalStateException(
                    "Missing test resource: " + resource
            );
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)
        )) {
            return reader.lines().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
