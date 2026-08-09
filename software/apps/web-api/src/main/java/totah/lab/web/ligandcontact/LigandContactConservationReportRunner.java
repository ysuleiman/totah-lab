package totah.lab.web.ligandcontact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.hermes.biohub.artifact.BiohubPocketEvidenceReader;
import totah.lab.hermes.biohub.model.BiohubPocketEvidence;
import totah.lab.web.ligandcontact.ComplexLigandContactExtractor
        .ResidueMoietyContact;
import totah.lab.web.ligandcontact.LigandContactConservationAnalyzer
        .LigandContactConservationReport;
import totah.lab.web.persistence.ReceptorEntity;
import totah.lab.web.persistence.ReceptorRepository;
import totah.lab.web.service.ProteinSequenceAlignmentService;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Writes the residue-level ligand-contact conservation report for a
 * receptor pair (default METTL7A vs METTL7B) from the existing BioHub
 * evidence artifacts and the cached protein sequence alignment.
 *
 * <p>Enabled with {@code totah.ligand-contact-report.enabled=true}
 * (intended with {@code --spring.main.web-application-type=none}).
 * When the SAH evidence of both targets is equivalent to the SAM
 * evidence (same residue set, distances, and atom-pair counts), a
 * single SAM report is written with an equivalence note instead of
 * duplicating the report.</p>
 */
@Component
@ConditionalOnProperty(
        name = "totah.ligand-contact-report.enabled",
        havingValue = "true"
)
public class LigandContactConservationReportRunner
        implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            LigandContactConservationReportRunner.class
    );

    private static final double EQUIVALENCE_EPSILON_ANGSTROMS = 0.01;

    private static final double DIRECT_CONTACT_CUTOFF_ANGSTROMS = 4.5;

    private static final String BIOHUB_POCKETS_SQL = """
            SELECT a.storage_location
            FROM docking.pocket p
            JOIN docking.artifacts a ON a.id = p.artifact_id
            WHERE p.receptor_id = ? AND p.source = 'BIOHUB'
            ORDER BY p.pocket_number
            """;

    private final DataSource dataSource;
    private final ReceptorRepository receptorRepository;
    private final ProteinSequenceAlignmentService alignmentService;
    private final BiohubPocketEvidenceReader evidenceReader =
            new BiohubPocketEvidenceReader();
    private final LigandContactConservationAnalyzer analyzer =
            new LigandContactConservationAnalyzer();
    private final ComplexLigandContactExtractor contactExtractor =
            new ComplexLigandContactExtractor();
    private final LigandMoietyConservationAnalyzer moietyAnalyzer =
            new LigandMoietyConservationAnalyzer();

    private final String queryUniProtId;
    private final String candidateUniProtId;
    private final String queryLabel;
    private final String candidateLabel;
    private final String outputPath;

    public LigandContactConservationReportRunner(
            DataSource dataSource,
            ReceptorRepository receptorRepository,
            ProteinSequenceAlignmentService alignmentService,
            @Value("${totah.ligand-contact-report.query-uniprot:Q9H8H3}")
            String queryUniProtId,
            @Value("${totah.ligand-contact-report.candidate-uniprot:Q6UX53}")
            String candidateUniProtId,
            @Value("${totah.ligand-contact-report.query-label:7A}")
            String queryLabel,
            @Value("${totah.ligand-contact-report.candidate-label:7B}")
            String candidateLabel,
            @Value("${totah.ligand-contact-report.output:"
                    + "analysis/mettl7-sam-contact/"
                    + "SAM_CONTACT_CONSERVATION.md}")
            String outputPath
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.receptorRepository = Objects.requireNonNull(
                receptorRepository,
                "receptorRepository"
        );
        this.alignmentService = Objects.requireNonNull(
                alignmentService,
                "alignmentService"
        );
        this.queryUniProtId = queryUniProtId;
        this.candidateUniProtId = candidateUniProtId;
        this.queryLabel = queryLabel;
        this.candidateLabel = candidateLabel;
        this.outputPath = outputPath;
    }

    @Override
    public void run(String... arguments) throws Exception {
        ReceptorEntity queryReceptor = receptorRepository
                .findByUniProtId(queryUniProtId)
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown query receptor: " + queryUniProtId
                ));
        ReceptorEntity candidateReceptor = receptorRepository
                .findByUniProtId(candidateUniProtId)
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown candidate receptor: " + candidateUniProtId
                ));

        BiohubPocketEvidence querySam =
                evidenceFor(queryReceptor.getId(), "SAM");
        BiohubPocketEvidence candidateSam =
                evidenceFor(candidateReceptor.getId(), "SAM");
        BiohubPocketEvidence querySah =
                evidenceFor(queryReceptor.getId(), "SAH");
        BiohubPocketEvidence candidateSah =
                evidenceFor(candidateReceptor.getId(), "SAH");

        Path querySamComplex =
                complexPdbFor(queryReceptor.getId(), "SAM");
        Path candidateSamComplex =
                complexPdbFor(candidateReceptor.getId(), "SAM");

        SequenceAlignment alignment = alignmentService.alignmentFor(
                queryReceptor.getId(),
                candidateReceptor.getId()
        );
        LOGGER.info(
                "Sequence alignment {} -> {}: {} pairs, identity {}",
                queryUniProtId,
                candidateUniProtId,
                alignment.pairs().size(),
                alignment.identity()
        );

        LigandContactConservationReport samReport = analyzer.analyze(
                queryLabel,
                candidateLabel,
                querySam,
                candidateSam,
                alignment
        );

        boolean sahEquivalent =
                LigandContactConservationAnalyzer.equivalent(
                        querySam,
                        querySah,
                        EQUIVALENCE_EPSILON_ANGSTROMS
                )
                        && LigandContactConservationAnalyzer.equivalent(
                        candidateSam,
                        candidateSah,
                        EQUIVALENCE_EPSILON_ANGSTROMS
                );

        StringBuilder markdown = new StringBuilder(
                LigandContactConservationMarkdown.render(samReport)
        );

        markdown.append('\n').append(moietySection(
                queryLabel,
                candidateLabel,
                "SAM",
                querySamComplex,
                candidateSamComplex,
                alignment
        ));

        if (sahEquivalent) {
            markdown.append("""
                    
                    ## SAH evidence
                    
                    SAH evidence is equivalent to SAM for both targets \
                    (identical residue sets, minimum distances, and \
                    atom-pair counts); a separate SAH report is omitted.
                    """);
        } else {
            LigandContactConservationReport sahReport = analyzer.analyze(
                    queryLabel,
                    candidateLabel,
                    querySah,
                    candidateSah,
                    alignment
            );
            markdown.append('\n').append(
                    LigandContactConservationMarkdown.render(sahReport)
            );
            markdown.append('\n').append(moietySection(
                    queryLabel,
                    candidateLabel,
                    "SAH",
                    complexPdbFor(queryReceptor.getId(), "SAH"),
                    complexPdbFor(candidateReceptor.getId(), "SAH"),
                    alignment
            ));
        }

        Path output = Path.of(outputPath);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, markdown.toString());
        LOGGER.info(
                "Ligand contact conservation report written to {}",
                output.toAbsolutePath()
        );
    }

    private BiohubPocketEvidence evidenceFor(
            long receptorId,
            String ligandCcd
    ) throws SQLException, IOException {
        for (String storageLocation : biohubArtifacts(receptorId)) {
            BiohubPocketEvidence evidence =
                    evidenceReader.read(Path.of(storageLocation));
            if (ligandCcd.equalsIgnoreCase(evidence.ligandCcd())) {
                return evidence;
            }
        }
        throw new IllegalStateException(
                "No " + ligandCcd + " BioHub evidence for receptor "
                        + receptorId
        );
    }

    private String moietySection(
            String queryLabel,
            String candidateLabel,
            String ligandCcd,
            Path queryComplex,
            Path candidateComplex,
            SequenceAlignment alignment
    ) throws IOException {
        List<ResidueMoietyContact> queryContacts = contactExtractor
                .extract(
                        queryComplex,
                        ligandCcd,
                        DIRECT_CONTACT_CUTOFF_ANGSTROMS
                );
        List<ResidueMoietyContact> candidateContacts = contactExtractor
                .extract(
                        candidateComplex,
                        ligandCcd,
                        DIRECT_CONTACT_CUTOFF_ANGSTROMS
                );
        return LigandContactConservationMarkdown.renderMoietySection(
                moietyAnalyzer.analyze(
                        queryLabel,
                        candidateLabel,
                        ligandCcd,
                        queryContacts,
                        candidateContacts,
                        alignment
                )
        );
    }

    /**
     * Derives the complex PDB path from the evidence artifact path
     * (the artifacts of one complex share a directory and name stem).
     */
    private Path complexPdbFor(
            long receptorId,
            String ligandCcd
    ) throws SQLException, IOException {
        for (String storageLocation : biohubArtifacts(receptorId)) {
            BiohubPocketEvidence evidence =
                    evidenceReader.read(Path.of(storageLocation));
            if (ligandCcd.equalsIgnoreCase(evidence.ligandCcd())) {
                Path pdb = Path.of(storageLocation.replace(
                        "_pocket_6A.json",
                        ".pdb"
                ));
                if (!Files.exists(pdb)) {
                    throw new IOException(
                            "Complex PDB not found next to evidence "
                                    + "artifact: " + pdb
                    );
                }
                return pdb;
            }
        }
        throw new IllegalStateException(
                "No " + ligandCcd + " BioHub evidence for receptor "
                        + receptorId
        );
    }

    private List<String> biohubArtifacts(long receptorId)
            throws SQLException {
        List<String> locations = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     BIOHUB_POCKETS_SQL)
        ) {
            statement.setLong(1, receptorId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    locations.add(rows.getString(1));
                }
            }
        }
        return locations;
    }
}
