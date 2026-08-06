package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.ResidueChemistry;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.evidence.PocketCandidateSource;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.web.persistence.PocketSummaryEntity;
import totah.lab.web.persistence.PocketSummaryRepository;
import totah.lab.web.persistence.StructureRepository;
import totah.lab.web.pocketmatch.PocketMatchCandidateProvider;
import totah.lab.web.pocketmatch.PocketMatchCandidateProvider
        .PocketMatchCandidate;
import totah.lab.web.pocketmatch.PocketMatchProperties;
import totah.lab.web.pocketmatch.PocketMatchSignatureLoader;
import totah.lab.web.pocketmatch.PocketSearchProperties;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The retrieval-channel configuration of the Stage 1 union
 * ({@code pocket.search.global-shape.*},
 * {@code pocket.search.include-chosen-references}) and the provenance
 * carried on candidates: unioned source flags, per-channel ranks and
 * the evidence-pipeline assessment on diagnostic rows.
 */
class PocketSearchUnionConfigurationTest {

    private static final long QUERY_POCKET_ID = 42L;

    private static final PocketGeometryBasis BASIS =
            PocketGeometryBasis.RESIDUE_ATOMS;

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
    private final FakeGeometryLoader geometryLoader =
            new FakeGeometryLoader();
    private final FakeResidueLoader residueLoader =
            new FakeResidueLoader();
    private final FakeSequenceAlignmentService sequenceAlignmentService =
            new FakeSequenceAlignmentService();
    private final KeyResidueConfiguration keyResidues =
            new KeyResidueConfiguration();
    private final FakePocketMatchProvider pocketMatchProvider =
            new FakePocketMatchProvider();

    @Test
    void disabledChannelsReproduceProductionCandidateList() {
        stubQuerySummary();
        stubSqlCandidates(1L, 2L, 3L);
        registerPair(1L);
        registerPair(2L);
        registerPair(3L);
        // Would be injected if the chosen channel were on.
        when(structureRepository.findAllChosenPocketIds())
                .thenReturn(List.of(9L));

        // Reference: legacy construction (pre-configuration defaults,
        // chosen channel on but nothing chosen).
        PocketSimilarityService legacy = new PocketSimilarityService(
                repository,
                structureRepository,
                geometryLoader,
                residueLoader,
                keyResidues,
                sequenceAlignmentService,
                pocketMatchProvider
        );
        List<Long> reference = diagnose(legacy);

        // Configured: global shape enabled and uncapped, chosen
        // channel explicitly off, PocketMatch disabled by default.
        PocketSearchProperties properties = new PocketSearchProperties();
        properties.getGlobalShape().setEnabled(true);
        properties.getGlobalShape().setLimit(Integer.MAX_VALUE);
        properties.setIncludeChosenReferences(false);

        List<Long> configured = diagnose(service(properties));

        assertThat(configured).isEqualTo(reference);
        assertThat(configured).containsExactly(1L, 2L, 3L);
    }

    @Test
    void chosenChannelInjectsGuaranteedCandidateWithSourceFlag() {
        stubQuerySummary();
        stubSqlCandidates(1L);
        registerPair(1L);
        registerPair(9L);
        when(structureRepository.findAllChosenPocketIds())
                .thenReturn(List.of(9L));
        when(repository.findAllById(List.of(9L)))
                .thenReturn(List.of(candidateSummary(9L)));

        List<PocketSimilarityDiagnostic> rows =
                service(new PocketSearchProperties())
                        .diagnoseSimilar(QUERY_POCKET_ID, 10);

        assertThat(rows).extracting(PocketSimilarityDiagnostic::pocketId)
                .contains(9L);
        PocketSimilarityDiagnostic chosen = rows.stream()
                .filter(row -> row.pocketId() == 9L)
                .findFirst()
                .orElseThrow();
        // Chosen is a guaranteed evaluation only: no Stage 1 rank,
        // no score bonus — the flag records provenance, not rank.
        assertThat(chosen.stageOneRank()).isZero();
        assertThat(chosen.candidateSources())
                .containsExactly("CHOSEN_REFERENCE");
        assertThat(chosen.provenance()).isEqualTo("CHOSEN_REFERENCE");
        assertThat(chosen.assessment()).isNotBlank();

        PocketSimilarityDiagnostic sql = rows.stream()
                .filter(row -> row.pocketId() == 1L)
                .findFirst()
                .orElseThrow();
        assertThat(sql.candidateSources())
                .containsExactly("GLOBAL_SHAPE");
        assertThat(sql.stageOneRank()).isEqualTo(1);
    }

    @Test
    void includeChosenReferencesFalseSkipsTheChosenChannel() {
        stubQuerySummary();
        stubSqlCandidates(1L);
        registerPair(1L);
        registerPair(9L);
        when(structureRepository.findAllChosenPocketIds())
                .thenReturn(List.of(9L));

        PocketSearchProperties properties = new PocketSearchProperties();
        properties.setIncludeChosenReferences(false);

        assertThat(diagnose(service(properties)))
                .doesNotContain(9L);
    }

    @Test
    void disabledGlobalShapeLeavesOnlyTheUnionChannels() {
        stubQuerySummary();
        when(structureRepository.findAllChosenPocketIds())
                .thenReturn(List.of(9L));
        when(repository.findAllById(List.of(9L)))
                .thenReturn(List.of(candidateSummary(9L)));
        registerPair(9L);

        PocketSearchProperties properties = new PocketSearchProperties();
        properties.getGlobalShape().setEnabled(false);

        assertThat(diagnose(service(properties)))
                .containsExactly(9L);
    }

    @Test
    void globalShapeLimitCapsTheStageOneQuery() {
        stubQuerySummary();
        stubSqlCandidates();

        PocketSearchProperties properties = new PocketSearchProperties();
        properties.getGlobalShape().setLimit(2);

        service(properties).findSimilar(QUERY_POCKET_ID, 100);

        verify(repository).findDescriptorCandidates(
                eq(QUERY_POCKET_ID),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                eq(PageRequest.of(0, 2))
        );
    }

    @Test
    void pocketMatchRanksAndDualSourcesStayChannelLocal() {
        stubQuerySummary();
        stubSqlCandidates(7L);
        registerPair(7L);
        pocketMatchProvider.enabled = true;
        // Candidate 7 arrives through both channels: the union keeps
        // one row with both source flags and both natural ranks.
        pocketMatchProvider.hits = List.of(new PocketMatchCandidate(
                7L,
                0.9,
                5,
                0.8,
                0.7,
                3,
                2,
                0.5
        ));

        List<PocketSimilarityDiagnostic> rows =
                service(new PocketSearchProperties())
                        .diagnoseSimilar(QUERY_POCKET_ID, 10);

        PocketSimilarityDiagnostic row = rows.stream()
                .filter(candidate -> candidate.pocketId() == 7L)
                .findFirst()
                .orElseThrow();
        assertThat(row.candidateSources())
                .containsExactly("GLOBAL_SHAPE", "POCKET_MATCH");
        assertThat(row.provenance()).isEqualTo("GLOBAL_SHAPE");
        assertThat(row.stageOneRank()).isEqualTo(1);
        assertThat(row.pocketMatchRank()).isEqualTo(5);
        assertThat(row.pocketMatchSymmetricRank()).isEqualTo(3);
        assertThat(row.pocketMatchQueryCoverageRank()).isEqualTo(2);
        assertThat(row.pocketMatchQueryCoverage()).isEqualTo(0.9);
        assertThat(row.assessment()).isNotBlank();
    }

    private PocketSimilarityService service(
            PocketSearchProperties properties
    ) {
        return new PocketSimilarityService(
                repository,
                structureRepository,
                geometryLoader,
                residueLoader,
                keyResidues,
                sequenceAlignmentService,
                pocketMatchProvider,
                properties,
                new PocketComparisonEvidenceAssembler()
        );
    }

    private List<Long> diagnose(PocketSimilarityService service) {
        return service.diagnoseSimilar(QUERY_POCKET_ID, 10).stream()
                .map(PocketSimilarityDiagnostic::pocketId)
                .toList();
    }

    private void stubQuerySummary() {
        when(repository.findById(QUERY_POCKET_ID))
                .thenReturn(Optional.of(
                        new TestPocketSummaryEntity(QUERY_POCKET_ID)
                ));
    }

    private void stubSqlCandidates(Long... pocketIds) {
        when(repository.findDescriptorCandidates(
                anyLong(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any()
        )).thenReturn(Arrays.stream(pocketIds)
                .map(this::candidateSummary)
                .toList());
    }

    private PocketSummaryEntity candidateSummary(long pocketId) {
        return new TestPocketSummaryEntity(pocketId);
    }

    private void registerPair(long pocketId) {
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(pocketId, cloud(IRREGULAR_CLOUD));
        residueLoader.register(
                QUERY_POCKET_ID,
                residuePoints("A", 1)
        );
        residueLoader.register(pocketId, residuePoints("B", 101));
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
        List<Point3D> positions = Arrays.stream(IRREGULAR_CLOUD)
                .map(coordinate -> new Point3D(
                        coordinate[0],
                        coordinate[1],
                        coordinate[2]
                ))
                .toList();

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
                PocketSearchUnionConfigurationTest.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException(
                            "DataSource must not be used in this test"
                    );
                }
        );
    }

    private static final class TestPocketSummaryEntity
            extends PocketSummaryEntity {

        private final long pocketId;

        TestPocketSummaryEntity(long pocketId) {
            this.pocketId = pocketId;
        }

        @Override
        public Long getPocketId() {
            return pocketId;
        }

        @Override
        public Long getReceptorId() {
            return 100L + pocketId;
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
        public Double getVolume() {
            // Constant so Stage 3 ties break deterministically by
            // pocket id.
            return 100.0;
        }

        @Override
        public Integer getResidueCount() {
            return 20;
        }

        @Override
        public Integer getAlphaSphereCount() {
            return 0;
        }

        @Override
        public String getGeometryBasis() {
            return BASIS.name();
        }

        @Override
        public Double getHydrophobicFraction() {
            return 0.5;
        }

        @Override
        public Double getAromaticFraction() {
            return 0.1;
        }

        @Override
        public Double getPolarFraction() {
            return 0.2;
        }

        @Override
        public Double getPositiveFraction() {
            return 0.1;
        }

        @Override
        public Double getNegativeFraction() {
            return 0.1;
        }

        @Override
        public Double getRadiusOfGyration() {
            return 10.0;
        }

        @Override
        public Double getExtentMajor() {
            return 20.0;
        }

        @Override
        public Double getElongation() {
            return 0.6;
        }

        @Override
        public Double getFlatness() {
            return 0.2;
        }

        @Override
        public double[] getRadialHistogram() {
            double[] histogram = new double[12];
            Arrays.fill(histogram, 1.0 / 12);
            return histogram;
        }
    }

    private static final class FakeGeometryLoader
            extends PocketPointCloudLoader {

        private final Map<Long, PocketPointCloud> clouds =
                new HashMap<>();

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
            Map<Long, PocketPointCloud> result = new HashMap<>();
            for (long pocketId : pocketIds) {
                PocketPointCloud cloud = clouds.get(pocketId);
                if (cloud != null) {
                    result.put(pocketId, cloud);
                }
            }
            return new LoadedPointClouds(result, Map.of());
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
            List<PocketResiduePoint> points = residues.get(pocketId);
            if (points == null) {
                throw new IllegalArgumentException(
                        "No fake residues for pocket " + pocketId
                );
            }
            return points;
        }
    }

    private static final class FakeSequenceAlignmentService
            extends ProteinSequenceAlignmentService {

        private FakeSequenceAlignmentService() {
            super(null, null, null, null, null);
        }

        @Override
        public SequenceAlignment alignmentFor(
                long queryReceptorId,
                long candidateReceptorId
        ) {
            return null;
        }
    }

    private static final class FakePocketMatchProvider
            extends PocketMatchCandidateProvider {

        private boolean enabled;
        private List<PocketMatchCandidate> hits = List.of();

        private FakePocketMatchProvider() {
            super(
                    new PocketMatchProperties(),
                    new PocketMatchSignatureLoader(
                            unusedDataSource(),
                            System.getProperty("java.io.tmpdir"),
                            ""
                    )
            );
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public List<PocketMatchCandidate> topCandidates(
                long queryPocketId
        ) {
            return hits;
        }
    }
}
