package totah.lab.analysis.disulfide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentalVicinalCohortBuilderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void retainsOnlyExperimentalVicinalAndSeparatelyBondedControls() throws Exception {
        Path scan = temporaryDirectory.resolve("scan.csv");
        Files.writeString(scan, """
                uniprot_accession,chain,cys1,cys2,sequence_context,motif_offset,sg_distance_angstrom,distance_class,chi3_degrees,cys1_plddt,cys2_plddt,mean_plddt,filename
                POS,A,2,3,ACCG,1,7.000,OPEN_GT_6A,90.0,90.0,92.0,91.0,pos.pdb
                NEG,A,2,3,ACCG,1,7.100,OPEN_GT_6A,91.0,91.0,91.0,91.0,neg.pdb
                UNKNOWN,A,2,3,ACCG,1,7.200,OPEN_GT_6A,92.0,92.0,92.0,92.0,unknown.pdb
                """);
        Path uniProt = temporaryDirectory.resolve("uniprot.tsv");
        Files.writeString(uniProt, """
                Entry\tGene Names\tProtein names\tSequence\tDisulfide bond
                POS\tPOSG\tPositive\tACCGA\tDISULFID 2..3; /evidence="ECO:0000269|PubMed:1"
                NEG\tNEGG\tNegative\tACCGA\tDISULFID 1..2; /evidence="ECO:0007744|PDB:1ABC"; DISULFID 3..5; /evidence="ECO:0000269|PubMed:2"
                UNKNOWN\tUNKG\tUnknown\tACCGA\tDISULFID 2..3; /evidence="ECO:0000250"
                """);
        Path output = temporaryDirectory.resolve("cohorts");

        ExperimentalVicinalCohortBuilder.CohortSummary summary =
                ExperimentalVicinalCohortBuilder.build(scan, uniProt, output);

        assertThat(summary.positiveCount()).isEqualTo(1);
        assertThat(summary.controlPoolCount()).isEqualTo(1);
        assertThat(summary.matchedPairCount()).isEqualTo(1);
        assertThat(Files.readString(
                output.resolve("experimentally-confirmed-vicinal-cc.csv")))
                .contains("VICINAL,POS,POSG");
        assertThat(Files.readString(
                output.resolve("experimental-non-vicinal-cc-control-pool.csv")))
                .contains("NON_VICINAL,NEG,NEGG");
        assertThat(Files.readString(
                output.resolve("matched-vicinal-vs-non-vicinal-cc.csv")))
                .contains("CASE,VICINAL,POS")
                .contains("CONTROL,NON_VICINAL,NEG")
                .doesNotContain("UNKNOWN");
    }

    @Test
    void acceptsScanRowsWithMissingSgDistance() throws Exception {
        Path scan = temporaryDirectory.resolve("scan.csv");
        Files.writeString(scan, """
                uniprot_accession,chain,cys1,cys2,sequence_context,motif_offset,sg_distance_angstrom,distance_class,chi3_degrees,cys1_plddt,cys2_plddt,mean_plddt,filename
                POS,A,2,3,ACCG,1,,MISSING_SG,,90.0,92.0,91.0,pos.pdb
                NEG,A,2,3,ACCG,1,7.100,OPEN_GT_6A,91.0,91.0,91.0,91.0,neg.pdb
                """);
        Path uniProt = temporaryDirectory.resolve("uniprot.tsv");
        Files.writeString(uniProt, """
                Entry\tGene Names\tProtein names\tSequence\tDisulfide bond
                POS\tPOSG\tPositive\tACCGA\tDISULFID 2..3; /evidence="ECO:0000269|PubMed:1"
                NEG\tNEGG\tNegative\tACCGA\tDISULFID 1..2; /evidence="ECO:0007744|PDB:1ABC"; DISULFID 3..5; /evidence="ECO:0000269|PubMed:2"
                """);
        Path output = temporaryDirectory.resolve("cohorts");

        ExperimentalVicinalCohortBuilder.CohortSummary summary =
                ExperimentalVicinalCohortBuilder.build(scan, uniProt, output);

        assertThat(summary.positiveCount()).isEqualTo(1);
        assertThat(summary.controlPoolCount()).isEqualTo(1);
        assertThat(summary.matchedPairCount()).isEqualTo(1);
        String positives = Files.readString(
                output.resolve("experimentally-confirmed-vicinal-cc.csv"));
        assertThat(positives)
                .contains(",MISSING_SG,")
                .doesNotContain("NaN");
    }

    @Test
    void parsesQuotedScanFieldsContainingCommas() throws Exception {
        Path scan = temporaryDirectory.resolve("scan.csv");
        Files.writeString(scan, """
                uniprot_accession,chain,cys1,cys2,sequence_context,motif_offset,sg_distance_angstrom,distance_class,chi3_degrees,cys1_plddt,cys2_plddt,mean_plddt,filename
                "PO,S",A,2,3,ACCG,1,7.000,OPEN_GT_6A,90.0,90.0,92.0,91.0,"AF-POS,1-model.pdb"
                """);
        Path uniProt = temporaryDirectory.resolve("uniprot.tsv");
        Files.writeString(uniProt, """
                Entry\tGene Names\tProtein names\tSequence\tDisulfide bond
                PO,S\tPOSG\tPositive\tACCGA\tDISULFID 2..3; /evidence="ECO:0000269|PubMed:1"
                """);
        Path output = temporaryDirectory.resolve("cohorts");

        ExperimentalVicinalCohortBuilder.CohortSummary summary =
                ExperimentalVicinalCohortBuilder.build(scan, uniProt, output);

        assertThat(summary.positiveCount()).isEqualTo(1);
        assertThat(Files.readString(
                output.resolve("experimentally-confirmed-vicinal-cc.csv")))
                .contains("PO,S")
                .contains("AF-POS,1-model.pdb");
    }

    @Test
    void rejectsScanRowsWithWrongColumnCount() throws Exception {
        Path scan = temporaryDirectory.resolve("scan.csv");
        Files.writeString(scan, """
                uniprot_accession,chain,cys1,cys2,sequence_context,motif_offset,sg_distance_angstrom,distance_class,chi3_degrees,cys1_plddt,cys2_plddt,mean_plddt,filename
                POS,A,2,3,ACCG,1,7.000,OPEN_GT_6A,90.0,90.0,92.0,91.0
                """);
        Path uniProt = temporaryDirectory.resolve("uniprot.tsv");
        Files.writeString(uniProt, """
                Entry\tGene Names\tProtein names\tSequence\tDisulfide bond
                POS\tPOSG\tPositive\tACCGA\tDISULFID 2..3; /evidence="ECO:0000269|PubMed:1"
                """);

        assertThatThrownBy(() -> ExperimentalVicinalCohortBuilder.build(
                scan, uniProt, temporaryDirectory.resolve("cohorts")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Malformed scan CSV row");
    }
}
