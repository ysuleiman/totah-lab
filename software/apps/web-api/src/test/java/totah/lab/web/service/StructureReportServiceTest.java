package totah.lab.web.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructureReportServiceTest {

    @Test
    void generatesOrderedResidueLevelReport() {
        StructureReportService reportService = new StructureReportService(
                new ReportStructureService(),
                new ReportPocketService()
        );

        StructureReportService.StructureReport report =
                reportService.generate(2);

        assertThat(report.title())
                .isEqualTo("Thiol S-methyltransferase TMT1B structure report");
        assertThat(report.chosenPocketResidues())
                .extracting(StructureReportService.ReportResidue::residueNumber)
                .containsExactly(76, 100);
        assertThat(report.chosenPocketResidues())
                .extracting(StructureReportService.ReportResidue::oneLetterCode)
                .containsExactly("E", "N");

        StructureReportService.LigandEvidence sam =
                report.ligandEvidence().getFirst();
        assertThat(sam.strongContactCount()).isEqualTo(1);
        assertThat(sam.nearContactCount()).isEqualTo(1);
        assertThat(sam.outsideDirectContactCount()).isEqualTo(1);
        assertThat(sam.residues())
                .extracting(StructureReportService.ContactResidue::classification)
                .containsExactly("STRONG", "NEAR", "CONTEXT");
        assertThat(sam.residues().get(1).residueName()).isEqualTo("ALA");
        assertThat(sam.residues().get(1).oneLetterCode()).isEqualTo("A");
        assertThat(report.narrative())
                .contains("1 direct contacts lie outside the chosen pocket")
                .endsWith("The original pocket membership is unchanged.");
    }

    private static final class ReportStructureService
            extends StructureService {

        private ReportStructureService() {
            super(null, null);
        }

        @Override
        public StructureDetails getStructure(long structureId) {
            return new StructureDetails(
                    structureId,
                    "ALPHAFOLD",
                    "AF-Q6UX53-F1-model_v6",
                    "A",
                    1,
                    "RAW",
                    null,
                    new ReceptorSummary(
                            1,
                            "METTL7B",
                            "Q6UX53",
                            "Thiol S-methyltransferase TMT1B",
                            "METTL7B",
                            "Homo sapiens"
                    ),
                    new ArtifactSummary(
                            6,
                            "Q6UX53.pdb",
                            "RAW_PDB_FILE",
                            "/structures/Q6UX53.pdb"
                    ),
                    new ChosenPocketSummary(1, 2, "FPOCKET"),
                    List.of(),
                    "/api/structures/2/pockets"
            );
        }
    }

    private static final class ReportPocketService extends PocketService {

        private ReportPocketService() {
            super(null, null);
        }

        @Override
        public PocketDetails getPocket(long pocketId) {
            return new PocketDetails(
                    pocketId,
                    2,
                    "FPOCKET",
                    1690.538,
                    0.003,
                    0.832,
                    null,
                    new StructureSummary(
                            2,
                            "ALPHAFOLD",
                            "AF-Q6UX53-F1-model_v6",
                            "A",
                            1
                    ),
                    new ReceptorSummary(1, "METTL7B"),
                    new ArtifactSummary(
                            9,
                            "pocket2_atm.pdb",
                            "FPOCKET_POCKET",
                            "/pockets/pocket2_atm.pdb"
                    ),
                    List.of(
                            residue(100, "ASN"),
                            residue(76, "GLU")
                    ),
                    null
            );
        }

        @Override
        public List<PocketSummary> getPocketsForStructure(long structureId) {
            List<PocketResidueEvidence> residues = List.of(
                    contact(100, "ASN", 3.89, true, true),
                    contact(125, "ALA", 4.47, true, false),
                    contact(128, "GLU", 5.1, false, true)
            );
            PocketEvidence evidence = new PocketEvidence(
                    "SAM",
                    "esmfold2-fast",
                    6.0,
                    4.5,
                    0.94,
                    0.98,
                    3,
                    2,
                    2,
                    1,
                    List.of(100L, 125L, 128L),
                    List.of(100L, 125L),
                    List.of(100L, 128L),
                    List.of(100L),
                    residues
            );
            return List.of(new PocketSummary(
                    107,
                    1,
                    "BIOHUB",
                    null,
                    null,
                    null,
                    null,
                    47,
                    evidence
            ));
        }

        private static ResidueDetails residue(
                int residueNumber,
                String residueName
        ) {
            return new ResidueDetails(
                    residueNumber,
                    "A",
                    residueNumber,
                    "",
                    residueName
            );
        }

        private static PocketResidueEvidence contact(
                int residueNumber,
                String residueName,
                double distance,
                boolean direct,
                boolean chosen
        ) {
            return new PocketResidueEvidence(
                    residueNumber,
                    "A",
                    residueNumber,
                    residueName,
                    distance,
                    4,
                    direct,
                    chosen
            );
        }
    }
}
