package totah.lab.prometheus.completeness;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fail-closed persistence gate for publishable or qualified scientific results.
 * Presence is insufficient: every mandatory artifact must match its recorded digest.
 */
public final class ScientificResultCompletenessValidator {
    private static final Set<String> PROVENANCE = Set.of(
            "software_versions", "code_commit", "input_checksums", "output_checksums");
    private static final Set<String> QM = union(PROVENANCE, Set.of(
            "geometry.xyz", "atom_order", "charge", "multiplicity", "method", "basis", "grid",
            "dispersion_configuration", "scf_configuration", "electronic_energy", "electronic_gradient",
            "dispersion_energy", "dispersion_gradient", "total_energy", "total_gradient", "force",
            "convergence_diagnostics", "hardware_runtime_identity"));
    private static final Set<String> FIT = union(PROVENANCE, Set.of(
            "model_family", "basis_functions", "basis_ordering", "fitted_coefficients", "parameter_names",
            "parameter_units", "frozen_parameters", "bounds_constraints", "regularization",
            "objective_definition", "objective_weights", "training_ids", "validation_ids",
            "feature_normalization", "target_normalization", "initial_parameter_vector",
            "final_parameter_vector", "optimizer", "optimizer_configuration", "optimizer_state",
            "convergence_state", "seed", "iteration_history", "final_predictions", "residuals", "metrics"));
    private static final Set<String> ML = union(FIT, Set.of(
            "architecture_identity", "model_checkpoint", "pretrained_parent_identity_checksum",
            "trainable_frozen_parameter_masks", "scheduler_state", "normalization_tensors",
            "random_seeds", "selected_epoch_step", "selection_criterion", "split_manifest"));
    private static final Set<String> FORCE_FIELD = union(PROVENANCE, Set.of(
            "atom_typing", "atom_order_mapping", "charges", "bond_parameters", "angle_parameters",
            "torsion_parameters", "improper_parameters", "lj_parameters", "cross_terms",
            "fitted_correction_coefficients", "parameter_provenance", "runnable_topology_parameter_file"));

    public ValidationResult validate(Path bundleRoot, ScientificResultManifest manifest) throws IOException {
        Path root = bundleRoot.toRealPath();
        Set<String> required = required(manifest.type());
        List<String> issues = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        for (String name : required) {
            ScientificArtifactReference reference = manifest.artifacts().get(name);
            if (reference == null) {
                missing.add(name);
                issues.add("missing manifest artifact: " + name);
                continue;
            }
            Path artifact = root.resolve(reference.path()).normalize();
            if (!artifact.startsWith(root) || !Files.isRegularFile(artifact)) {
                missing.add(name);
                issues.add("missing artifact file: " + name);
            } else if (!sha256(artifact).equals(reference.sha256())) {
                missing.add(name);
                issues.add("checksum mismatch: " + name);
            }
        }
        if (manifest.type() == ScientificResultType.PARAMETER_FIT
                || manifest.type() == ScientificResultType.ML_MODEL_FIT) {
            validateParameterOrdering(root, manifest.artifacts(), missing, issues);
        }
        ScientificResultCompleteness status = classify(manifest.type(), missing);
        return new ValidationResult(status, List.copyOf(issues));
    }

    public void requireComplete(Path bundleRoot, ScientificResultManifest manifest) throws IOException {
        ValidationResult result = validate(bundleRoot, manifest);
        if (result.status() != ScientificResultCompleteness.REPRODUCIBLE_COMPLETE) {
            throw new IncompleteScientificResultException(manifest.resultId(), result);
        }
    }

    private static void validateParameterOrdering(Path root,
            Map<String, ScientificArtifactReference> artifacts, Set<String> missing, List<String> issues)
            throws IOException {
        ScientificArtifactReference namesRef = artifacts.get("parameter_names");
        ScientificArtifactReference coefficientsRef = artifacts.get("fitted_coefficients");
        if (namesRef == null || coefficientsRef == null
                || missing.contains("parameter_names") || missing.contains("fitted_coefficients")) return;
        List<String> names = nonblankLines(root.resolve(namesRef.path()));
        List<String> coefficientNames = new ArrayList<>();
        for (String line : nonblankLines(root.resolve(coefficientsRef.path()))) {
            int separator = line.indexOf('\t');
            if (separator <= 0 || separator == line.length() - 1) {
                missing.add("fitted_coefficients");
                issues.add("coefficient rows must be name<TAB>finite-value");
                return;
            }
            try {
                if (!Double.isFinite(Double.parseDouble(line.substring(separator + 1)))) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                missing.add("fitted_coefficients");
                issues.add("non-finite or non-numeric fitted coefficient");
                return;
            }
            coefficientNames.add(line.substring(0, separator));
        }
        if (!names.equals(coefficientNames)) {
            missing.add("fitted_coefficients");
            issues.add("parameter-name ordering differs from coefficient ordering");
        }
    }

    private static List<String> nonblankLines(Path path) throws IOException {
        return Files.readAllLines(path).stream().filter(line -> !line.isBlank()).toList();
    }

    private static ScientificResultCompleteness classify(ScientificResultType type, Set<String> missing) {
        if (missing.isEmpty()) return ScientificResultCompleteness.REPRODUCIBLE_COMPLETE;
        if (missing.contains("electronic_gradient") || missing.contains("dispersion_gradient")
                || missing.contains("total_gradient") || missing.contains("force")) {
            if (missing.contains("electronic_gradient") || missing.contains("dispersion_gradient")) {
                return ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION;
            }
            return ScientificResultCompleteness.INCOMPLETE_MISSING_DERIVATIVES;
        }
        if (missing.contains("electronic_energy") || missing.contains("dispersion_energy")) {
            return ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION;
        }
        if (missing.contains("optimizer_state") || missing.contains("scheduler_state")) {
            return ScientificResultCompleteness.INCOMPLETE_MISSING_OPTIMIZER_STATE;
        }
        if (missing.contains("fitted_coefficients") || missing.contains("final_parameter_vector")
                || missing.contains("model_checkpoint") || missing.contains("runnable_topology_parameter_file")) {
            return ScientificResultCompleteness.INCOMPLETE_MISSING_MODEL_STATE;
        }
        return ScientificResultCompleteness.INCOMPLETE_MISSING_PROVENANCE;
    }

    private static Set<String> required(ScientificResultType type) {
        return switch (type) {
            case QM_CALCULATION -> QM;
            case PARAMETER_FIT -> FIT;
            case ML_MODEL_FIT -> ML;
            case DERIVED_FORCE_FIELD -> FORCE_FIELD;
        };
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) >= 0;) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record ValidationResult(ScientificResultCompleteness status, List<String> issues) {
        public ValidationResult {
            issues = List.copyOf(issues);
        }
    }
}
