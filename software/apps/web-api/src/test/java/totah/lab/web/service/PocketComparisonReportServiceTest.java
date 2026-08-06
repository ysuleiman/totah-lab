package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.ResidueChemistry;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.evidence.LigandContact;
import totah.lab.athena.pocket.evidence.LigandContactStatus;
import totah.lab.athena.pocket.evidence.LigandContactType;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.athena.sequence.SequenceResidue;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.biohub.model.BiohubPocketEvidence;
import totah.lab.hermes.biohub.model.BiohubPocketEvidence.ResidueContact;
import totah.lab.web.persistence.PocketRepository;
import totah.lab.web.persistence.PocketSummaryEntity;
import totah.lab.web.persistence.PocketSummaryRepository;
import totah.lab.web.persistence.StructureRepository;
import totah.lab.web.pocketmatch.PocketMatchCandidateProvider;
import totah.lab.web.pocketmatch.PocketMatchProperties;
import totah.lab.web.pocketmatch.PocketMatchSignatureLoader;
import totah.lab.web.service.PocketComparisonReportView.LigandContactSection;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Service-level assembly of the structured comparison report through
 * the live comparison path, with the same fake loaders as
 * {@code PocketSimilarityServiceTest}.
 */
class PocketComparisonReportServiceTest {

    private static final long QUERY_POCKET_ID = 42L;
    private static final long CANDIDATE_POCKET_ID = 1L;

    private static final PocketGeometryBasis BASIS =
            PocketGeometryBasis.RESIDUE_ATOMS;

    // Irregular 8-point cloud: non-degenerate for principal-axis
    // alignment (the fixture of PocketSimilarityServiceTest).
    private static final double[][] IRREGULAR_CLOUD = {
            {0.0, 0.0, 0.0},
            {10.0, 0.0, 0.0},
            {0.0, 6.0, 0.0},
            {0.0, 0.0, 3.0},
            {8.0, 5.0, 2.0},
            {2.0, 4.0, 6.0},
            {7.0, 1.0, 5.0},
            {3.0, 8.0, 1.0}
    };

    private static final String[] RESIDUE_NAMES = {
            "ALA", "PHE", "SER", "LYS", "ASP", "CYS", "GLY", "LEU"
    };

    private static final ResidueChemistry[] RESIDUE_CHEMISTRIES = {
            ResidueChemistry.HYDROPHOBIC,
            ResidueChemistry.AROMATIC,
            ResidueChemistry.POLAR,
            ResidueChemistry.POSITIVE,
            ResidueChemistry.NEGATIVE,
            ResidueChemistry.CYSTEINE,
            ResidueChemistry.GLYCINE,
            ResidueChemistry.HYDROPHOBIC
    };

    private final PocketSummaryRepository repository =
            mock(PocketSummaryRepository.class);
    private final StructureRepository structureRepository =
            mock(StructureRepository.class);
    private final PocketRepository pocketRepository =
            mock(PocketRepository.class);
    private final FakeGeometryLoader geometryLoader =
            new FakeGeometryLoader();
    private final FakeResidueLoader residueLoader =
            new FakeResidueLoader();
    private final FakeSequenceAlignmentService sequenceAlignmentService =
            new FakeSequenceAlignmentService();
    private final PocketSimilarityService similarityService =
            new PocketSimilarityService(
                    repository,
                    structureRepository,
                    geometryLoader,
                    residueLoader,
                    new KeyResidueConfiguration(),
                    sequenceAlignmentService,
                    new PocketMatchCandidateProvider(
                            new PocketMatchProperties(),
                            new PocketMatchSignatureLoader(
                                    unusedDataSource(),
                                    System.getProperty("java.io.tmpdir"),
                                    ""
                            )
                    )
            );
    private final StubBiohubReportService reportService =
            new StubBiohubReportService(
                    similarityService,
                    structureRepository,
                    pocketRepository
            );

    @Test
    void identicalPocketsProduceAStrongMatchReport() {
        stubIdenticalPair(null, null);

        PocketComparisonReportView report =
                reportService.report(QUERY_POCKET_ID, CANDIDATE_POCKET_ID);

        assertEquals(QUERY_POCKET_ID, report.queryPocketId());
        assertEquals(CANDIDATE_POCKET_ID, report.candidatePocketId());

        // Retrieval: a direct pairwise report bypasses retrieval.
        assertFalse(report.retrieval().globalShapeEvaluated());
        assertFalse(report.retrieval().pocketMatchEvaluated());
        assertFalse(report.retrieval().chosenReference());
        assertTrue(report.retrieval().candidateSources().isEmpty());
        assertNull(report.retrieval().globalShapeRank());

        // Alignment: PCA+ICP is the only hypothesis without a seed.
        assertEquals(
                "PCA_ICP",
                report.alignment().selectedInitialization()
        );
        assertFalse(report.alignment().selectionReason().isBlank());
        assertTrue(report.alignment().pcaIcp().available());
        assertTrue(report.alignment().pcaIcp().accepted());
        assertFalse(report.alignment().sequenceSeeded().available());
        assertFalse(report.alignment().sequenceSeedAvailable());
        assertEquals(
                1.0,
                report.alignment().pcaIcp().geometrySimilarity(),
                1.0e-9
        );

        // Residue comparison: all eight pairs matched and identical.
        assertEquals(8, report.residueComparison().matchedResidueCount());
        assertEquals(8, report.residueComparison().identicalCount());
        assertEquals(
                1.0,
                report.residueComparison().chemistrySimilarity(),
                1.0e-9
        );
        assertEquals(
                1.0,
                report.residueComparison().identityFraction(),
                1.0e-9
        );
        assertEquals(
                8,
                report.residueComparison().correspondences().size()
        );
        assertTrue(report.residueComparison().correspondences().stream()
                .allMatch(pair -> pair.matchType().equals("IDENTICAL")));

        // Chemistry comparison mirrors the compare endpoint's view.
        assertEquals(
                "STRONG_SIMILARITY",
                report.chemistryComparison().classification()
        );
        assertEquals(
                8,
                report.chemistryComparison().matchedResidueCount()
        );

        // Key residues: nothing configured.
        assertEquals(
                0,
                report.keyResidueComparison().totalKeyResidueCount()
        );
        assertTrue(report.keyResidueComparison()
                .configuredKeyResidues()
                .isEmpty());

        // Interpretation: geometry 1.0, chemistry 1.0, substitution
        // 76/120 >= 0.60, no sequence or ligand evidence.
        assertEquals(
                "STRONG_FUNCTIONAL_MATCH",
                report.interpretation().verdict()
        );
        assertTrue(
                report.interpretation().reason().contains("geometry"),
                "reason was: " + report.interpretation().reason()
        );
        assertTrue(
                report.interpretation().reason().contains("chemistry"),
                "reason was: " + report.interpretation().reason()
        );
        assertTrue(
                report.interpretation().reason()
                        .contains("no ligand-contact evidence"),
                "reason was: " + report.interpretation().reason()
        );
    }

    @Test
    void ligandSectionRendersNotAvailableWithoutBiohubEvidence() {
        stubIdenticalPair(null, null);

        LigandContactSection section = reportService
                .report(QUERY_POCKET_ID, CANDIDATE_POCKET_ID)
                .ligandContactConservation();

        assertEquals(
                LigandContactStatus.NOT_AVAILABLE.name(),
                section.status()
        );
        assertNull(section.ligandCcd());
        assertNull(section.evidenceSource());
        // Absent evidence is never rendered as zeroed counts.
        assertNull(section.queryContactResidueCount());
        assertNull(section.matchedQueryContactResidueCount());
        assertNull(section.identicalContactCount());
        assertNull(section.contactCoverage());
        assertNull(section.contactIdentityFraction());
        assertTrue(section.contacts().isEmpty());
    }

    @Test
    void biohubEvidencePopulatesTheLigandContactSection() {
        stubIdenticalPair(100L, 200L);

        reportService.register(
                1000L + QUERY_POCKET_ID,
                biohubEvidence(List.of(
                        contact(1, "ALA", 2.5, 30, true),
                        contact(2, "PHE", 3.1, 20, true),
                        contact(3, "SER", 5.5, 2, false)
                ))
        );
        reportService.register(
                1000L + CANDIDATE_POCKET_ID,
                biohubEvidence(List.of(
                        contact("B", 101, "ALA", 2.6, 28, true),
                        contact("B", 102, "PHE", 3.0, 22, true)
                ))
        );

        LigandContactSection section = reportService
                .report(QUERY_POCKET_ID, CANDIDATE_POCKET_ID)
                .ligandContactConservation();

        assertEquals(
                LigandContactStatus.AVAILABLE.name(),
                section.status()
        );
        assertEquals("SAM", section.ligandCcd());
        assertEquals("BIOHUB", section.evidenceSource());

        // Two direct query contacts (the shell member is not a
        // contact in the conservation sense), both matched.
        assertEquals(2, section.queryContactResidueCount());
        assertEquals(2, section.matchedQueryContactResidueCount());
        assertEquals(2, section.identicalContactCount());
        assertEquals(2, section.sharedContactAnnotationCount());
        assertEquals(1.0, section.contactCoverage(), 1.0e-9);

        // Canonical per-residue contacts of both pockets, including
        // the shell member.
        assertEquals(5, section.contacts().size());

        LigandContact queryDirect = section.contacts().stream()
                .filter(contact ->
                        contact.residue().residueNumber() == 1)
                .findFirst()
                .orElseThrow();
        assertEquals(String.valueOf(QUERY_POCKET_ID),
                queryDirect.pocketReference());
        assertEquals("SAM", queryDirect.ligandCcd());
        assertEquals(LigandContactType.DIRECT,
                queryDirect.contactType());
        assertEquals(2.5, queryDirect.minimumDistance());
        assertEquals("BIOHUB", queryDirect.evidenceSource());

        LigandContact shell = section.contacts().stream()
                .filter(contact ->
                        contact.residue().residueNumber() == 3)
                .findFirst()
                .orElseThrow();
        assertEquals(LigandContactType.SHELL, shell.contactType());

        assertTrue(section.contacts().stream()
                .anyMatch(contact -> contact.pocketReference()
                        .equals(String.valueOf(CANDIDATE_POCKET_ID))));
    }

    /**
     * The METTL7A/METTL7B regression fixture assembled through the
     * service-level path (fake loaders serve the fixture clouds,
     * residues and the Needleman-Wunsch sequence alignment): the
     * sequence-seeded hypothesis must win with 31/31
     * sequence-consistent pairs, and the interpretation must reflect
     * the functional agreement.
     */
    @Test
    void mettl7FixtureAssemblesThroughTheService() {
        when(repository.findById(QUERY_POCKET_ID))
                .thenReturn(Optional.of(new TestPocketSummaryEntity(
                        QUERY_POCKET_ID, 100L
                )));
        when(repository.findById(CANDIDATE_POCKET_ID))
                .thenReturn(Optional.of(new TestPocketSummaryEntity(
                        CANDIDATE_POCKET_ID, 200L
                )));

        geometryLoader.register(
                QUERY_POCKET_ID,
                new PocketPointCloud(
                        points("/mettl7/query_alpha_spheres.csv"),
                        PocketGeometryBasis.ALPHA_SPHERES
                )
        );
        geometryLoader.register(
                CANDIDATE_POCKET_ID,
                new PocketPointCloud(
                        points("/mettl7/candidate_alpha_spheres.csv"),
                        PocketGeometryBasis.ALPHA_SPHERES
                )
        );
        residueLoader.register(
                QUERY_POCKET_ID,
                residues("/mettl7/query_residues.csv")
        );
        residueLoader.register(
                CANDIDATE_POCKET_ID,
                residues("/mettl7/candidate_residues.csv")
        );
        sequenceAlignmentService.register(
                100L,
                200L,
                new NeedlemanWunschSequenceAligner().align(
                        sequence("/mettl7/query_sequence.csv"),
                        sequence("/mettl7/candidate_sequence.csv")
                )
        );

        PocketComparisonReportView report =
                reportService.report(QUERY_POCKET_ID, CANDIDATE_POCKET_ID);

        assertNotEquals(
                "PCA_ICP",
                report.alignment().selectedInitialization()
        );
        assertTrue(report.alignment().sequenceSeeded().available());
        assertTrue(report.alignment().sequenceSeeded().accepted());
        assertFalse(report.alignment().selectionReason().isBlank());

        // The discarded PCA+ICP hypothesis stays inspectable.
        assertTrue(report.alignment().pcaIcp().available());
        assertFalse(report.alignment().pcaIcp().accepted());
        assertEquals(
                0,
                report.alignment().pcaIcp().sequenceConsistentPairCount()
        );

        assertEquals(
                31,
                report.residueComparison().matchedResidueCount()
        );
        assertEquals(
                31,
                report.residueComparison().sequenceConsistentPairCount()
        );
        assertEquals(
                1.0,
                report.residueComparison().sequenceConsistentFraction(),
                1.0e-9
        );
        assertEquals(
                0.82,
                report.residueComparison().chemistrySimilarity(),
                0.02
        );

        String verdict = report.interpretation().verdict();
        assertTrue(
                verdict.equals("STRONG_FUNCTIONAL_MATCH")
                        || verdict.equals("PROBABLE_FUNCTIONAL_MATCH"),
                "verdict was " + verdict
        );
        assertFalse(report.interpretation().reason().isBlank());
        assertTrue(
                report.interpretation().reason()
                        .startsWith(verdict + ":"),
                "reason was: " + report.interpretation().reason()
        );

        // No BioHub evidence for the fixture pair: NOT_AVAILABLE.
        assertEquals(
                LigandContactStatus.NOT_AVAILABLE.name(),
                report.ligandContactConservation().status()
        );
    }

    private void stubIdenticalPair(Long queryReceptor, Long candidateReceptor) {
        when(repository.findById(QUERY_POCKET_ID))
                .thenReturn(Optional.of(new TestPocketSummaryEntity(
                        QUERY_POCKET_ID, queryReceptor
                )));
        when(repository.findById(CANDIDATE_POCKET_ID))
                .thenReturn(Optional.of(new TestPocketSummaryEntity(
                        CANDIDATE_POCKET_ID, candidateReceptor
                )));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(
                CANDIDATE_POCKET_ID,
                cloud(IRREGULAR_CLOUD)
        );
        residueLoader.register(
                QUERY_POCKET_ID,
                residuePoints("A", 1)
        );
        residueLoader.register(
                CANDIDATE_POCKET_ID,
                residuePoints("B", 101)
        );
    }

    private static BiohubPocketEvidence biohubEvidence(
            List<ResidueContact> contacts
    ) {
        return new BiohubPocketEvidence(
                "SAM",
                "esmfold2",
                6.0,
                4.5,
                null,
                null,
                contacts
        );
    }

    private static ResidueContact contact(
            int residueNumber,
            String residueName,
            double minimumDistance,
            int atomPairs,
            boolean directContact
    ) {
        return contact(
                "A",
                residueNumber,
                residueName,
                minimumDistance,
                atomPairs,
                directContact
        );
    }

    private static ResidueContact contact(
            String chain,
            int residueNumber,
            String residueName,
            double minimumDistance,
            int atomPairs,
            boolean directContact
    ) {
        return new ResidueContact(
                chain,
                residueNumber,
                residueName,
                minimumDistance,
                atomPairs,
                directContact
        );
    }

    private static PocketPointCloud cloud(double[][] coordinates) {
        List<Point3D> points = Arrays.stream(coordinates)
                .map(coordinate -> new Point3D(
                        coordinate[0],
                        coordinate[1],
                        coordinate[2]
                ))
                .toList();
        return new PocketPointCloud(points, BASIS);
    }

    private static List<PocketResiduePoint> residuePoints(
            String chainId,
            int firstResidueNumber
    ) {
        List<Point3D> positions = cloud(IRREGULAR_CLOUD).points();
        List<PocketResiduePoint> residues = new ArrayList<>();

        for (int index = 0; index < positions.size(); index++) {
            residues.add(new PocketResiduePoint(
                    new ResidueReference(
                            chainId,
                            firstResidueNumber + index,
                            ' ',
                            RESIDUE_NAMES[index]
                    ),
                    positions.get(index),
                    RESIDUE_CHEMISTRIES[index]
            ));
        }

        return residues;
    }

    private static DataSource unusedDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                PocketComparisonReportServiceTest.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException(
                            "DataSource must not be used in this test"
                    );
                }
        );
    }

    private static List<Point3D> points(String resource) {
        List<Point3D> points = new ArrayList<>();

        for (String line : readLines(resource)) {
            String[] columns = line.split(",");
            points.add(new Point3D(
                    Double.parseDouble(columns[0]),
                    Double.parseDouble(columns[1]),
                    Double.parseDouble(columns[2])
            ));
        }

        return points;
    }

    private static List<PocketResiduePoint> residues(String resource) {
        List<PocketResiduePoint> residues = new ArrayList<>();

        for (String line : readLines(resource)) {
            String[] columns = line.split(",");
            residues.add(new PocketResiduePoint(
                    new ResidueReference(
                            "A",
                            Integer.parseInt(columns[0]),
                            ' ',
                            columns[1]
                    ),
                    new Point3D(
                            Double.parseDouble(columns[3]),
                            Double.parseDouble(columns[4]),
                            Double.parseDouble(columns[5])
                    ),
                    ResidueChemistry.valueOf(columns[2])
            ));
        }

        return residues;
    }

    private static List<SequenceResidue> sequence(String resource) {
        List<SequenceResidue> sequence = new ArrayList<>();

        for (String line : readLines(resource)) {
            String[] columns = line.split(",");
            sequence.add(new SequenceResidue(
                    Integer.parseInt(columns[0]),
                    columns[1]
            ));
        }

        return sequence;
    }

    private static List<String> readLines(String resource) {
        InputStream input =
                PocketComparisonReportServiceTest.class
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

    /**
     * Serves registered BioHub evidence per structure id, in place of
     * the artifact store.
     */
    private static final class StubBiohubReportService
            extends PocketComparisonReportService {

        private final Map<Long, List<BiohubPocketEvidence>> evidence =
                new HashMap<>();

        private StubBiohubReportService(
                PocketSimilarityService similarityService,
                StructureRepository structureRepository,
                PocketRepository pocketRepository
        ) {
            super(similarityService, structureRepository, pocketRepository);
        }

        void register(long structureId, BiohubPocketEvidence pocket) {
            evidence.computeIfAbsent(
                    structureId,
                    key -> new ArrayList<>()
            ).add(pocket);
        }

        @Override
        List<BiohubPocketEvidence> biohubEvidence(long structureId) {
            return evidence.getOrDefault(structureId, List.of());
        }
    }

    private static class TestPocketSummaryEntity
            extends PocketSummaryEntity {

        private final long pocketId;
        private final Long receptorId;

        TestPocketSummaryEntity(long pocketId, Long receptorId) {
            this.pocketId = pocketId;
            this.receptorId = receptorId;
        }

        @Override
        public Long getPocketId() {
            return pocketId;
        }

        @Override
        public Long getReceptorId() {
            return receptorId;
        }

        @Override
        public Long getStructureId() {
            return 1000L + pocketId;
        }

        @Override
        public String getSourceAccession() {
            return "ACC-" + pocketId;
        }

        @Override
        public Integer getPocketNumber() {
            return 1;
        }

        @Override
        public String getUniProtId() {
            return "UP" + pocketId;
        }
    }

    private static final class FakeGeometryLoader
            extends PocketPointCloudLoader {

        private final Map<Long, PocketPointCloud> clouds = new HashMap<>();

        private FakeGeometryLoader() {
            super(null, null);
        }

        void register(long pocketId, PocketPointCloud cloud) {
            clouds.put(pocketId, cloud);
        }

        @Override
        public LoadedPointClouds loadAllWithSpheres(
                Collection<Long> pocketIds
        ) {
            Map<Long, PocketPointCloud> resultClouds =
                    new LinkedHashMap<>();
            Map<Long, List<AlphaSphereView>> resultSpheres =
                    new LinkedHashMap<>();
            for (Long pocketId : pocketIds) {
                PocketPointCloud cloud = clouds.get(pocketId);
                if (cloud != null) {
                    resultClouds.put(pocketId, cloud);
                }
            }
            return new LoadedPointClouds(resultClouds, resultSpheres);
        }
    }

    /**
     * Serves registered protein sequence alignments per ordered
     * receptor pair. Unregistered pairs return {@code null} (no
     * sequence seed).
     */
    private static final class FakeSequenceAlignmentService
            extends ProteinSequenceAlignmentService {

        private final Map<String, SequenceAlignment> alignments =
                new HashMap<>();

        private FakeSequenceAlignmentService() {
            super(null, null, null, null, null);
        }

        void register(
                long queryReceptorId,
                long candidateReceptorId,
                SequenceAlignment alignment
        ) {
            alignments.put(
                    queryReceptorId + "->" + candidateReceptorId,
                    alignment
            );
        }

        @Override
        public SequenceAlignment alignmentFor(
                long queryReceptorId,
                long candidateReceptorId
        ) {
            return alignments.get(
                    queryReceptorId + "->" + candidateReceptorId
            );
        }
    }

    private static final class FakeResidueLoader
            extends PocketResidueLoader {

        private final Map<Long, List<PocketResiduePoint>> residues =
                new HashMap<>();

        private FakeResidueLoader() {
            super(null, null, null);
        }

        void register(long pocketId, List<PocketResiduePoint> points) {
            residues.put(pocketId, points);
        }

        @Override
        public List<PocketResiduePoint> load(long pocketId) {
            if (!residues.containsKey(pocketId)) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus
                                .UNPROCESSABLE_ENTITY,
                        "Pocket " + pocketId
                                + " structure artifact cannot be loaded"
                );
            }
            return residues.get(pocketId);
        }
    }
}
