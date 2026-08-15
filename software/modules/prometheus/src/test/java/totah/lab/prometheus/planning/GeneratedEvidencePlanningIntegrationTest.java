package totah.lab.prometheus.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.comparability.EnergyTarget;
import totah.lab.prometheus.comparability.ProtocolComparability;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.store.CanonicalEvidenceStore;
import totah.lab.prometheus.store.EvidenceImportDescriptor;
import totah.lab.prometheus.store.GeneratedEvidenceRegistry;
import totah.lab.prometheus.store.GeneratedEvidenceRole;

class GeneratedEvidencePlanningIntegrationTest {
    @TempDir Path temporary;

    @Test
    void acceptedGeneratedEvidenceIsReusedByNextPlanningRun() throws Exception {
        Path canonicalStore = temporary.resolve("canonical");
        new CanonicalEvidenceStore().compileOrLoad(temporary.resolve("source"), canonicalStore,
                new EvidenceImportDescriptor("empty-source", "empty-sha", "test", "1",
                        CanonicalEvidenceStore.SCHEMA_VERSION), ignored -> new EvidenceBundle());
        Path generatedStore = temporary.resolve("generated");
        Files.createDirectories(generatedStore);

        StrategyEvidenceMatcher matcher = new StrategyEvidenceMatcher(new ProtocolComparability(),
                new HeuristicCostModel(Map.of(CalculationType.HESSIAN, 10.0), 0.01));
        EvidenceRequirement requirement = new EvidenceRequirement(CalculationType.HESSIAN,
                EvidenceFixtures.PBE_DEF2_SVP, EnergyTarget.FORCE_CONSTANT, "bonded curvature",
                DatasetRole.DEVELOPMENT, true, TslFixtures.TSL, TslFixtures.geometryIdentityA());
        StrategyEvidenceRequirement strategyRequirement = new StrategyEvidenceRequirement("hessian",
                ScientificEvidenceRequirement.neutralSinglet(requirement), true, true, Optional.empty(),
                List.of("harmonic projection"), List.of("bond and angle constants"), List.of(), List.of());
        EvidenceRequirementSet requirements = new EvidenceRequirementSet("generated-reuse", List.of(strategyRequirement));
        PlanningEvidenceLoader loader = new PlanningEvidenceLoader();

        MissingEvidencePlan before = matcher.match(requirements,
                loader.load(canonicalStore, generatedStore), Set.of());
        assertThat(before.resolutions().get(0).decision()).isEqualTo(EvidenceReuseDecision.GENERATE_NEW);
        assertThat(before.newCalculations()).hasSize(1);

        var evidence = EvidenceFixtures.acceptedQuantum(
                EvidenceFixtures.identity(CalculationType.HESSIAN, EvidenceFixtures.PBE_DEF2_SVP,
                        TslFixtures.geometryIdentityA()), -100.0);
        new GeneratedEvidenceRegistry(generatedStore).register(before.newCalculations().get(0).checksum(), evidence,
                GeneratedEvidenceRole.PRIMARY, generatedStore, List.of(), "accepted generated Hessian");

        MissingEvidencePlan after = matcher.match(requirements,
                loader.load(canonicalStore, generatedStore), Set.of());
        assertThat(after.resolutions().get(0).decision()).isEqualTo(EvidenceReuseDecision.REUSE_EXISTING);
        assertThat(after.resolutions().get(0).evidenceHashes()).containsExactly(evidence.identity().evidenceHash());
        assertThat(after.newCalculations()).isEmpty();
    }
}
