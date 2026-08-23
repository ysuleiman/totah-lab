package totah.lab.prometheus.completeness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScientificResultCompletenessValidatorTest {
    private static final List<String> PROVENANCE = List.of(
            "software_versions", "code_commit", "input_checksums", "output_checksums");
    private static final List<String> QM = concat(PROVENANCE, List.of(
            "geometry.xyz", "atom_order", "charge", "multiplicity", "method", "basis", "grid",
            "dispersion_configuration", "scf_configuration", "electronic_energy", "electronic_gradient",
            "dispersion_energy", "dispersion_gradient", "total_energy", "total_gradient", "force",
            "convergence_diagnostics", "hardware_runtime_identity", "hessian_requested"));
    private static final List<String> FIT = concat(PROVENANCE, List.of(
            "model_family", "basis_functions", "basis_ordering", "fitted_coefficients", "parameter_names",
            "parameter_units", "frozen_parameters", "bounds_constraints", "regularization",
            "objective_definition", "objective_weights", "training_ids", "validation_ids",
            "feature_normalization", "target_normalization", "initial_parameter_vector",
            "final_parameter_vector", "optimizer", "optimizer_configuration", "optimizer_state",
            "convergence_state", "seed", "iteration_history", "final_predictions", "residuals", "metrics"));
    private static final List<String> ML = concat(FIT, List.of(
            "architecture_identity", "model_checkpoint", "pretrained_parent_identity_checksum",
            "trainable_frozen_parameter_masks", "scheduler_state", "normalization_tensors",
            "random_seeds", "selected_epoch_step", "selection_criterion", "split_manifest"));

    @TempDir Path temp;
    private final ScientificResultCompletenessValidator validator =
            new ScientificResultCompletenessValidator();

    @Test
    void completeQmBundleIsChecksumVerified() throws Exception {
        ScientificResultManifest manifest = manifest(ScientificResultType.QM_CALCULATION, QM);
        assertThat(validator.validate(temp, manifest).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    @Test
    void pbeD3MetadataWithPbeOnlyHessianFailsClosed() throws Exception {
        ScientificResultManifest manifest = hessianManifest(true);
        manifest = withoutArtifacts(manifest, "dispersion_hessian", "total_hessian");
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION);
    }

    @Test
    void pbeD3WithAllThreeConsistentHessiansPasses() throws Exception {
        ScientificResultManifest manifest = hessianManifest(true);
        assertThat(validator.validate(temp, manifest).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    @Test
    void missingDispersionHessianFailsClosed() throws Exception {
        ScientificResultManifest manifest = withoutArtifacts(hessianManifest(true), "dispersion_hessian");
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION);
    }

    @Test
    void incorrectHessianComponentSumFailsClosed() throws Exception {
        ScientificResultManifest manifest = hessianManifest(true);
        Files.writeString(temp.resolve("total_hessian"), "9 0 0\n0 9 0\n0 0 9\n");
        manifest = replaceDigest(manifest, "total_hessian");
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION);
    }

    @Test
    void wrongHessianDimensionsFailClosed() throws Exception {
        ScientificResultManifest manifest = hessianManifest(true);
        Files.writeString(temp.resolve("hessian_dimensions"), "6x6\n");
        manifest = replaceDigest(manifest, "hessian_dimensions");
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION);
    }

    @Test
    void nonfiniteHessianFailsClosed() throws Exception {
        ScientificResultManifest manifest = hessianManifest(true);
        Files.writeString(temp.resolve("dispersion_hessian"), "NaN 0 0\n0 0.2 0\n0 0 0.2\n");
        manifest = replaceDigest(manifest, "dispersion_hessian");
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION);
    }

    @Test
    void infiniteHessianFailsClosed() throws Exception {
        ScientificResultManifest manifest = hessianManifest(true);
        Files.writeString(temp.resolve("dispersion_hessian"), "Infinity 0 0\n0 0.2 0\n0 0 0.2\n");
        manifest = replaceDigest(manifest, "dispersion_hessian");
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION);
    }

    @Test
    void asymmetricHessianFailsClosed() throws Exception {
        ScientificResultManifest manifest = hessianManifest(true);
        Files.writeString(temp.resolve("dispersion_hessian"), "0.2 0.01 0\n0 0.2 0\n0 0 0.2\n");
        manifest = replaceDigest(manifest, "dispersion_hessian");
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION);
    }

    @Test
    void hessianGeometryIdentityMismatchFailsClosed() throws Exception {
        ScientificResultManifest manifest = hessianManifest(true);
        Files.writeString(temp.resolve("hessian_geometry_identity"), "wrong-geometry\n");
        manifest = replaceDigest(manifest, "hessian_geometry_identity");
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION);
    }

    @Test
    void mislabeledPbeOnlyEvidenceFailsClosed() throws Exception {
        ScientificResultManifest manifest = hessianManifest(false);
        String geometrySha = sha256(temp.resolve("geometry.xyz"));
        Files.writeString(temp.resolve("hessian_component_identity"),
                "electronic_identity=TRUSTED_PBE_ONLY_HESSIAN\n"
                        + "electronic_geometry_sha256=" + geometrySha + "\n"
                        + "total_identity=PBE_D3_BJ_TOTAL_HESSIAN\n");
        manifest = replaceDigest(manifest, "hessian_component_identity");
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION);
    }

    @Test
    void purePbeElectronicHessianPassesWhenExplicitlyPbeOnly() throws Exception {
        ScientificResultManifest manifest = hessianManifest(false);
        assertThat(validator.validate(temp, manifest).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    @Test
    void derivedFrequenciesWithoutExactSourceHessianBindingFailClosed() throws Exception {
        ScientificResultManifest manifest = hessianManifest(true);
        Map<String, ScientificArtifactReference> artifacts = new LinkedHashMap<>(manifest.artifacts());
        writeArtifact(artifacts, "frequencies_cm-1", "100.0\n200.0\n");
        manifest = new ScientificResultManifest(manifest.resultId(), manifest.type(), artifacts);
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_PROVENANCE);
    }

    @Test
    void derivedFrequenciesBoundToExactCompositeHessianPass() throws Exception {
        ScientificResultManifest manifest = hessianManifest(true);
        Map<String, ScientificArtifactReference> artifacts = new LinkedHashMap<>(manifest.artifacts());
        writeArtifact(artifacts, "frequencies_cm-1", "100.0\n200.0\n");
        writeArtifact(artifacts, "frequencies_hessian_sha256",
                artifacts.get("total_hessian").sha256() + "\n");
        manifest = new ScientificResultManifest(manifest.resultId(), manifest.type(), artifacts);
        assertThat(validator.validate(temp, manifest).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    @Test
    void deletedCoefficientFileFailsClosed() throws Exception {
        ScientificResultManifest manifest = fitManifest(ScientificResultType.PARAMETER_FIT, FIT);
        Files.delete(temp.resolve("fitted_coefficients"));
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_MODEL_STATE);
    }

    @Test
    void deletedTotalGradientFailsClosed() throws Exception {
        ScientificResultManifest manifest = manifest(ScientificResultType.QM_CALCULATION, QM);
        Files.delete(temp.resolve("total_gradient"));
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_DERIVATIVES);
    }

    @Test
    void absentComponentDecompositionFailsClosedEvenWhenTotalsExist() throws Exception {
        ScientificResultManifest manifest = manifest(ScientificResultType.QM_CALCULATION, QM);
        Files.delete(temp.resolve("dispersion_gradient"));
        assertIncomplete(manifest,
                ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION);
    }

    @Test
    void missingCheckpointFailsClosed() throws Exception {
        ScientificResultManifest manifest = fitManifest(ScientificResultType.ML_MODEL_FIT, ML);
        Files.delete(temp.resolve("model_checkpoint"));
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_MODEL_STATE);
    }

    @Test
    void missingOptimizerStateFailsClosed() throws Exception {
        ScientificResultManifest manifest = fitManifest(ScientificResultType.ML_MODEL_FIT, ML);
        Files.delete(temp.resolve("optimizer_state"));
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_OPTIMIZER_STATE);
    }

    @Test
    void parameterNameOrderingMustMatchCoefficientOrdering() throws Exception {
        ScientificResultManifest manifest = fitManifest(ScientificResultType.PARAMETER_FIT, FIT);
        Files.writeString(temp.resolve("fitted_coefficients"), "beta\t2.0\nalpha\t1.0\n");
        manifest = replaceDigest(manifest, "fitted_coefficients");
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_MODEL_STATE);
    }

    @Test
    void checkpointWithoutTrainingSplitCannotQualify() throws Exception {
        ScientificResultManifest manifest = fitManifest(ScientificResultType.ML_MODEL_FIT, ML);
        Files.delete(temp.resolve("split_manifest"));
        assertIncomplete(manifest, ScientificResultCompleteness.INCOMPLETE_MISSING_PROVENANCE);
    }

    @Test
    void requireCompleteThrowsBeforePublication() throws Exception {
        ScientificResultManifest manifest = fitManifest(ScientificResultType.ML_MODEL_FIT, ML);
        Files.delete(temp.resolve("scheduler_state"));
        assertThatThrownBy(() -> validator.requireComplete(temp, manifest))
                .isInstanceOf(IncompleteScientificResultException.class)
                .hasMessageContaining("INCOMPLETE_MISSING_OPTIMIZER_STATE");
    }

    private void assertIncomplete(ScientificResultManifest manifest,
            ScientificResultCompleteness expected) throws IOException {
        var result = validator.validate(temp, manifest);
        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.status()).isNotEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    private ScientificResultManifest fitManifest(ScientificResultType type, List<String> required)
            throws Exception {
        ScientificResultManifest manifest = manifest(type, required);
        Files.writeString(temp.resolve("parameter_names"), "alpha\nbeta\n");
        Files.writeString(temp.resolve("fitted_coefficients"), "alpha\t1.0\nbeta\t2.0\n");
        manifest = replaceDigest(manifest, "parameter_names");
        return replaceDigest(manifest, "fitted_coefficients");
    }

    private ScientificResultManifest manifest(ScientificResultType type, List<String> required)
            throws Exception {
        Map<String, ScientificArtifactReference> artifacts = new LinkedHashMap<>();
        for (String name : required) {
            Path file = temp.resolve(name);
            Files.writeString(file, name + "\n");
            artifacts.put(name, new ScientificArtifactReference(Path.of(name), sha256(file)));
        }
        if (type == ScientificResultType.QM_CALCULATION) {
            writeArtifact(artifacts, "geometry.xyz", "1\nfixture\nH 0 0 0\n");
            writeArtifact(artifacts, "atom_order", "H\n");
            writeArtifact(artifacts, "method", "PBE\n");
            writeArtifact(artifacts, "dispersion_configuration", "none\n");
            writeArtifact(artifacts, "electronic_energy", "-1.0\n");
            writeArtifact(artifacts, "dispersion_energy", "0.0\n");
            writeArtifact(artifacts, "total_energy", "-1.0\n");
            writeArtifact(artifacts, "electronic_gradient", "0 0 0\n");
            writeArtifact(artifacts, "dispersion_gradient", "0 0 0\n");
            writeArtifact(artifacts, "total_gradient", "0 0 0\n");
            writeArtifact(artifacts, "force", "0 0 0\n");
            Files.writeString(temp.resolve("hessian_requested"), "false\n");
            artifacts.put("hessian_requested", new ScientificArtifactReference(
                    Path.of("hessian_requested"), sha256(temp.resolve("hessian_requested"))));
        }
        return new ScientificResultManifest("fixture", type, artifacts);
    }

    private ScientificResultManifest hessianManifest(boolean composite) throws Exception {
        ScientificResultManifest manifest = manifest(ScientificResultType.QM_CALCULATION, QM);
        Map<String, ScientificArtifactReference> artifacts = new LinkedHashMap<>(manifest.artifacts());
        writeArtifact(artifacts, "hessian_requested", "true\n");
        writeArtifact(artifacts, "method", composite ? "PBE-D3(BJ)\n" : "PBE\n");
        writeArtifact(artifacts, "dispersion_configuration", composite ? "D3(BJ)\n" : "NONE\n");
        writeArtifact(artifacts, "atom_order", "H\n");
        writeArtifact(artifacts, "geometry.xyz", "1\nfixture\nH 0 0 0\n");
        String geometrySha = sha256(temp.resolve("geometry.xyz"));
        writeArtifact(artifacts, "hessian_units", "hartree/bohr^2\n");
        writeArtifact(artifacts, "hessian_dimensions", "3x3\n");
        writeArtifact(artifacts, "hessian_geometry_identity", geometrySha + "\n");
        writeArtifact(artifacts, "electronic_hessian", "1 0 0\n0 1 0\n0 0 1\n");
        String identities = "electronic_identity=TRUSTED_PBE_ONLY_HESSIAN\n"
                + "electronic_geometry_sha256=" + geometrySha + "\n";
        if (composite) {
            writeArtifact(artifacts, "dispersion_hessian", "0.2 0 0\n0 0.2 0\n0 0 0.2\n");
            writeArtifact(artifacts, "total_hessian", "1.2 0 0\n0 1.2 0\n0 0 1.2\n");
            identities += "dispersion_identity=D3_BJ_ONLY_HESSIAN\n"
                    + "dispersion_geometry_sha256=" + geometrySha + "\n"
                    + "total_identity=PBE_D3_BJ_TOTAL_HESSIAN\n"
                    + "total_geometry_sha256=" + geometrySha + "\n";
        }
        writeArtifact(artifacts, "hessian_component_identity", identities);
        return new ScientificResultManifest("hessian-fixture", ScientificResultType.QM_CALCULATION, artifacts);
    }

    private void writeArtifact(Map<String, ScientificArtifactReference> artifacts, String name, String content)
            throws Exception {
        Files.writeString(temp.resolve(name), content);
        artifacts.put(name, new ScientificArtifactReference(Path.of(name), sha256(temp.resolve(name))));
    }

    private ScientificResultManifest withoutArtifacts(ScientificResultManifest manifest, String... names) {
        Map<String, ScientificArtifactReference> artifacts = new LinkedHashMap<>(manifest.artifacts());
        for (String name : names) artifacts.remove(name);
        return new ScientificResultManifest(manifest.resultId(), manifest.type(), artifacts);
    }

    private ScientificResultManifest replaceDigest(ScientificResultManifest manifest, String name)
            throws Exception {
        Map<String, ScientificArtifactReference> artifacts = new LinkedHashMap<>(manifest.artifacts());
        artifacts.put(name, new ScientificArtifactReference(Path.of(name), sha256(temp.resolve(name))));
        return new ScientificResultManifest(manifest.resultId(), manifest.type(), artifacts);
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first);
        result.addAll(second);
        return List.copyOf(result);
    }
}
