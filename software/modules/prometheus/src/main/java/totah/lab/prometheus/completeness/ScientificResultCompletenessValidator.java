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
import java.util.Properties;
import java.util.Set;

/**
 * Fail-closed persistence gate for publishable or qualified scientific results.
 * Presence is insufficient: every mandatory artifact must match its recorded digest.
 */
public final class ScientificResultCompletenessValidator {
    // Matrices are serialized as decimal text. Symmetry and component-sum checks therefore use
    // predeclared absolute/relative tolerances rather than bitwise equality.
    private static final double HESSIAN_SYMMETRY_TOLERANCE = 1.0e-8;
    private static final double HESSIAN_SUM_ABSOLUTE_TOLERANCE = 1.0e-12;
    private static final double HESSIAN_SUM_RELATIVE_TOLERANCE = 1.0e-10;
    private static final double OBSERVABLE_SUM_ABSOLUTE_TOLERANCE = 1.0e-12;
    private static final double OBSERVABLE_SUM_RELATIVE_TOLERANCE = 1.0e-10;
    private static final String HESSIAN_UNIT = "hartree/bohr^2";
    private static final Set<String> PROVENANCE = Set.of(
            "software_versions", "code_commit", "input_checksums", "output_checksums");
    private static final Set<String> QM = union(PROVENANCE, Set.of(
            "geometry.xyz", "atom_order", "charge", "multiplicity", "method", "basis", "grid",
            "dispersion_configuration", "scf_configuration", "electronic_energy", "electronic_gradient",
            "dispersion_energy", "dispersion_gradient", "total_energy", "total_gradient", "force",
            "convergence_diagnostics", "hardware_runtime_identity", "hessian_requested"));
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
            validateArtifact(root, manifest.artifacts(), name, missing, issues, true);
        }
        // A manifest entry is an integrity claim regardless of whether the artifact is
        // mandatory for its result type. Never leave optional/derived evidence unchecked.
        for (String name : manifest.artifacts().keySet()) {
            if (!required.contains(name)) {
                validateArtifact(root, manifest.artifacts(), name, missing, issues, false);
            }
        }
        if (manifest.type() == ScientificResultType.PARAMETER_FIT
                || manifest.type() == ScientificResultType.ML_MODEL_FIT) {
            validateParameterOrdering(root, manifest.artifacts(), missing, issues);
        }
        if (manifest.type() == ScientificResultType.QM_CALCULATION) {
            validateQmComposition(root, manifest.artifacts(), missing, issues);
            if (!missing.contains("hessian_requested")) {
                validateHessianCompleteness(root, manifest.artifacts(), missing, issues);
            }
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

    private static void validateArtifact(Path root, Map<String, ScientificArtifactReference> artifacts,
            String name, Set<String> missing, List<String> issues, boolean required) throws IOException {
        ScientificArtifactReference reference = artifacts.get(name);
        if (reference == null) {
            if (required) {
                missing.add(name);
                issues.add("missing manifest artifact: " + name);
            }
            return;
        }
        Path artifact = root.resolve(reference.path()).normalize();
        if (!artifact.startsWith(root) || !Files.isRegularFile(artifact)) {
            missing.add(name);
            issues.add("missing artifact file: " + name + " at " + reference.path());
        } else if (!sha256(artifact).equals(reference.sha256())) {
            missing.add(name);
            issues.add("checksum mismatch: " + name + " at " + reference.path());
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

    private static void validateQmComposition(Path root,
            Map<String, ScientificArtifactReference> artifacts, Set<String> missing, List<String> issues)
            throws IOException {
        if (missing.contains("atom_order")) return;
        int atomCount = nonblankLines(root.resolve(artifacts.get("atom_order").path())).size();
        String method = readArtifact(root, artifacts, "method", missing, issues);
        String dispersionConfiguration = readArtifact(
                root, artifacts, "dispersion_configuration", missing, issues);
        if (method == null || dispersionConfiguration == null) return;
        boolean composite = hasDispersion(method, dispersionConfiguration);

        Double electronicEnergy = parseFiniteScalar(
                "electronic_energy", readArtifact(root, artifacts, "electronic_energy", missing, issues),
                missing, issues);
        Double dispersionEnergy = parseFiniteScalar(
                "dispersion_energy", readArtifact(root, artifacts, "dispersion_energy", missing, issues),
                missing, issues);
        Double totalEnergy = parseFiniteScalar(
                "total_energy", readArtifact(root, artifacts, "total_energy", missing, issues),
                missing, issues);
        if (composite && electronicEnergy != null && dispersionEnergy != null && totalEnergy != null
                && !numericallyEqual(totalEnergy, electronicEnergy + dispersionEnergy,
                OBSERVABLE_SUM_ABSOLUTE_TOLERANCE, OBSERVABLE_SUM_RELATIVE_TOLERANCE)) {
            missing.add("total_energy");
            issues.add("total_energy != electronic_energy + dispersion_energy within serialized-value tolerance");
        }

        Matrix electronicGradient = parseHessian("electronic_gradient",
                readArtifact(root, artifacts, "electronic_gradient", missing, issues), missing, issues);
        Matrix dispersionGradient = parseHessian("dispersion_gradient",
                readArtifact(root, artifacts, "dispersion_gradient", missing, issues), missing, issues);
        Matrix totalGradient = parseHessian("total_gradient",
                readArtifact(root, artifacts, "total_gradient", missing, issues), missing, issues);
        Matrix force = parseHessian("force", readArtifact(root, artifacts, "force", missing, issues),
                missing, issues);
        boolean electronicShape = validateCartesianVectorShape(
                "electronic_gradient", electronicGradient, atomCount, missing, issues);
        boolean dispersionShape = validateCartesianVectorShape(
                "dispersion_gradient", dispersionGradient, atomCount, missing, issues);
        boolean totalShape = validateCartesianVectorShape(
                "total_gradient", totalGradient, atomCount, missing, issues);
        boolean forceShape = validateCartesianVectorShape("force", force, atomCount, missing, issues);
        if (composite && electronicShape && dispersionShape && totalShape) {
            validateMatrixComposition("total_gradient", electronicGradient, dispersionGradient,
                    totalGradient, 1.0, missing, issues);
        }
        if (totalShape && forceShape) {
            validateMatrixComposition("force", totalGradient, null, force, -1.0, missing, issues);
        }
    }

    private static boolean hasDispersion(String method, String dispersionConfiguration) {
        String normalized = dispersionConfiguration.strip().toLowerCase();
        return method.toLowerCase().contains("d3")
                || !(normalized.equals("none") || normalized.equals("false")
                || normalized.equals("no_dispersion"));
    }

    private static Double parseFiniteScalar(String name, String text,
            Set<String> missing, List<String> issues) {
        if (text == null) return null;
        try {
            double value = Double.parseDouble(text.strip());
            if (!Double.isFinite(value)) throw new NumberFormatException("nonfinite");
            return value;
        } catch (NumberFormatException exception) {
            missing.add(name);
            issues.add(name + " must be one finite numeric scalar");
            return null;
        }
    }

    private static boolean validateCartesianVectorShape(String name, Matrix matrix, int atomCount,
            Set<String> missing, List<String> issues) {
        if (matrix == null) return false;
        if (matrix.rows() != atomCount || matrix.columns() != 3) {
            missing.add(name);
            issues.add(name + " dimensions " + matrix.rows() + "x" + matrix.columns()
                    + " must be exactly Nx3 = " + atomCount + "x3");
            return false;
        }
        return true;
    }

    private static void validateMatrixComposition(String resultName, Matrix first, Matrix second,
            Matrix result, double resultSign, Set<String> missing, List<String> issues) {
        for (int row = 0; row < result.rows(); row++) {
            for (int column = 0; column < result.columns(); column++) {
                double expected = first.values()[row][column]
                        + (second == null ? 0.0 : second.values()[row][column]);
                expected *= resultSign;
                if (!numericallyEqual(result.values()[row][column], expected,
                        OBSERVABLE_SUM_ABSOLUTE_TOLERANCE, OBSERVABLE_SUM_RELATIVE_TOLERANCE)) {
                    missing.add(resultName);
                    issues.add(resultName + (second == null
                            ? " != -total_gradient" : " != electronic_gradient + dispersion_gradient")
                            + " at [" + row + "," + column + "] within serialized-matrix tolerance");
                    return;
                }
            }
        }
    }

    private static boolean numericallyEqual(double actual, double expected,
            double absoluteTolerance, double relativeTolerance) {
        double tolerance = absoluteTolerance
                + relativeTolerance * Math.max(Math.abs(actual), Math.abs(expected));
        return Math.abs(actual - expected) <= tolerance;
    }

    private static List<String> nonblankLines(Path path) throws IOException {
        return Files.readAllLines(path).stream().filter(line -> !line.isBlank()).toList();
    }

    private static void validateHessianCompleteness(Path root,
            Map<String, ScientificArtifactReference> artifacts, Set<String> missing, List<String> issues)
            throws IOException {
        String requestedText = readArtifact(root, artifacts, "hessian_requested", missing, issues);
        if (requestedText == null) return;
        boolean requested;
        if (requestedText.strip().equalsIgnoreCase("true")) requested = true;
        else if (requestedText.strip().equalsIgnoreCase("false")) requested = false;
        else {
            missing.add("hessian_requested");
            issues.add("hessian_requested must contain true or false");
            return;
        }
        if (!requested) return;

        String method = readArtifact(root, artifacts, "method", missing, issues);
        String dispersion = readArtifact(root, artifacts, "dispersion_configuration", missing, issues);
        String units = requireHessianArtifact(root, artifacts, "hessian_units", missing, issues);
        String dimensions = requireHessianArtifact(root, artifacts, "hessian_dimensions", missing, issues);
        String geometryIdentity = requireHessianArtifact(root, artifacts, "hessian_geometry_identity", missing, issues);
        String componentText = requireHessianArtifact(root, artifacts, "hessian_component_identity", missing, issues);
        String electronicText = requireHessianArtifact(root, artifacts, "electronic_hessian", missing, issues);
        if (units == null || dimensions == null || geometryIdentity == null || componentText == null
                || electronicText == null || method == null || dispersion == null
                || missing.contains("atom_order") || missing.contains("geometry.xyz")) return;

        boolean composite = hasDispersion(method, dispersion);
        String totalText = composite
                ? requireHessianArtifact(root, artifacts, "total_hessian", missing, issues) : null;
        String dispersionText = composite
                ? requireHessianArtifact(root, artifacts, "dispersion_hessian", missing, issues) : null;
        if (composite && (totalText == null || dispersionText == null)) return;

        int atomCount = nonblankLines(root.resolve(artifacts.get("atom_order").path())).size();
        int expectedDimension = Math.multiplyExact(3, atomCount);
        int[] declared = parseDimensions(dimensions, missing, issues);
        Matrix electronic = parseHessian("electronic_hessian", electronicText, missing, issues);
        Matrix dispersionMatrix = composite
                ? parseHessian("dispersion_hessian", dispersionText, missing, issues) : null;
        Matrix total = composite ? parseHessian("total_hessian", totalText, missing, issues) : null;
        if (declared == null || electronic == null || (composite && (dispersionMatrix == null || total == null))) return;
        if (declared[0] != expectedDimension || declared[1] != expectedDimension) {
            missing.add("hessian_dimensions");
            issues.add("hessian_dimensions must be exactly 3N x 3N = " + expectedDimension + "x" + expectedDimension);
        }
        boolean electronicValid = validateMatrixIdentity(
                "electronic_hessian", electronic, declared, missing, issues);
        boolean dispersionValid = false;
        boolean totalValid = false;
        if (composite) {
            dispersionValid = validateMatrixIdentity(
                    "dispersion_hessian", dispersionMatrix, declared, missing, issues);
            totalValid = validateMatrixIdentity("total_hessian", total, declared, missing, issues);
            if (dispersionValid && isIdenticallyZero(dispersionMatrix)) {
                missing.add("dispersion_hessian");
                issues.add("dispersion_hessian is identically zero and cannot establish executed-component provenance");
                dispersionValid = false;
            }
            if (electronicValid && dispersionValid && totalValid) {
                validateHessianSum(electronic, dispersionMatrix, total, missing, issues);
            }
        }
        validateHessianGeometry(root, artifacts, geometryIdentity.strip(), componentText, composite, missing, issues);
        if (!units.strip().equals(HESSIAN_UNIT)) {
            missing.add("hessian_units");
            issues.add("hessian_units must use the locked vocabulary " + HESSIAN_UNIT);
        }
        validateExecutedHessianComponents(root, artifacts, geometryIdentity.strip(), method, dispersion,
                composite, missing, issues);
        validateDerivedHessianBindings(root, artifacts, composite, missing, issues);
    }

    private static String requireHessianArtifact(Path root,
            Map<String, ScientificArtifactReference> artifacts, String name, Set<String> missing, List<String> issues)
            throws IOException {
        String value = readArtifact(root, artifacts, name, missing, issues);
        if (value == null) {
            missing.add(name);
            if (artifacts.get(name) == null) issues.add("missing manifest artifact: " + name);
        }
        return value;
    }

    private static String readArtifact(Path root, Map<String, ScientificArtifactReference> artifacts,
            String name, Set<String> missing, List<String> issues) throws IOException {
        ScientificArtifactReference reference = artifacts.get(name);
        if (reference == null || missing.contains(name)) return null;
        Path path = root.resolve(reference.path()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            missing.add(name);
            issues.add("missing artifact file: " + name);
            return null;
        }
        if (!sha256(path).equals(reference.sha256())) {
            missing.add(name);
            issues.add("checksum mismatch: " + name);
            return null;
        }
        return Files.readString(path);
    }

    private static int[] parseDimensions(String text, Set<String> missing, List<String> issues) {
        String[] parts = text.strip().toLowerCase().split("x", -1);
        try {
            if (parts.length != 2) throw new NumberFormatException();
            int rows = Integer.parseInt(parts[0].strip());
            int columns = Integer.parseInt(parts[1].strip());
            if (rows <= 0 || columns <= 0) throw new NumberFormatException();
            return new int[] {rows, columns};
        } catch (NumberFormatException exception) {
            missing.add("hessian_dimensions");
            issues.add("hessian_dimensions must use positive ROWSxCOLUMNS form");
            return null;
        }
    }

    private static Matrix parseHessian(String name, String text, Set<String> missing, List<String> issues) {
        if (text == null) return null;
        List<double[]> rows = new ArrayList<>();
        int columns = -1;
        try {
            for (String line : text.lines().filter(value -> !value.isBlank()).toList()) {
                String[] fields = line.strip().split("\\s+");
                if (columns < 0) columns = fields.length;
                if (fields.length != columns) throw new NumberFormatException("ragged matrix");
                double[] row = new double[columns];
                for (int index = 0; index < columns; index++) {
                    row[index] = Double.parseDouble(fields[index]);
                    if (!Double.isFinite(row[index])) throw new NumberFormatException("nonfinite matrix");
                }
                rows.add(row);
            }
            if (rows.isEmpty()) throw new NumberFormatException("empty matrix");
            return new Matrix(rows.toArray(double[][]::new), rows.size(), columns);
        } catch (NumberFormatException exception) {
            missing.add(name);
            issues.add(name + " must be a finite rectangular numeric matrix: " + exception.getMessage());
            return null;
        }
    }

    private static boolean validateMatrixIdentity(String name, Matrix matrix, int[] declared,
            Set<String> missing, List<String> issues) {
        if (matrix.rows() != matrix.columns() || matrix.rows() != declared[0] || matrix.columns() != declared[1]) {
            missing.add(name);
            issues.add(name + " dimensions " + matrix.rows() + "x" + matrix.columns()
                    + " do not match declared square dimensions " + declared[0] + "x" + declared[1]);
            return false;
        }
        for (int row = 0; row < matrix.rows(); row++) {
            for (int column = row + 1; column < matrix.columns(); column++) {
                if (Math.abs(matrix.values()[row][column] - matrix.values()[column][row])
                        > HESSIAN_SYMMETRY_TOLERANCE) {
                    missing.add(name);
                    issues.add(name + " fails symmetry diagnostic at [" + row + "," + column + "]");
                    return false;
                }
            }
        }
        return true;
    }

    private static void validateHessianSum(Matrix electronic, Matrix dispersion, Matrix total,
            Set<String> missing, List<String> issues) {
        for (int row = 0; row < total.rows(); row++) {
            for (int column = 0; column < total.columns(); column++) {
                double expected = electronic.values()[row][column] + dispersion.values()[row][column];
                if (!numericallyEqual(total.values()[row][column], expected,
                        HESSIAN_SUM_ABSOLUTE_TOLERANCE, HESSIAN_SUM_RELATIVE_TOLERANCE)) {
                    missing.add("total_hessian");
                    issues.add("total_hessian != electronic_hessian + dispersion_hessian at ["
                            + row + "," + column + "] within serialized-matrix tolerance");
                    return;
                }
            }
        }
    }

    private static boolean isIdenticallyZero(Matrix matrix) {
        for (double[] row : matrix.values()) {
            for (double value : row) {
                if (value != 0.0) return false;
            }
        }
        return true;
    }

    private static void validateExecutedHessianComponents(Path root,
            Map<String, ScientificArtifactReference> artifacts, String geometrySha256,
            String method, String parameters, boolean composite,
            Set<String> missing, List<String> issues) throws IOException {
        String producer = readArtifact(root, artifacts, "software_versions", missing, issues);
        String codeCommit = readArtifact(root, artifacts, "code_commit", missing, issues);
        String inputChecksums = readArtifact(root, artifacts, "input_checksums", missing, issues);
        String outputChecksums = readArtifact(root, artifacts, "output_checksums", missing, issues);
        if (producer == null || codeCommit == null || inputChecksums == null || outputChecksums == null) return;
        validateExecutedComponent(new ExecutedComponentProvenance(
                "hessian", "electronic", producer.strip(), method.strip(), "electronic method",
                geometrySha256, artifacts.get("electronic_hessian").sha256(),
                executionIdentity(codeCommit, inputChecksums, outputChecksums, "electronic_hessian")),
                "electronic_hessian", missing, issues);
        if (composite && artifacts.containsKey("dispersion_hessian")) {
            validateExecutedComponent(new ExecutedComponentProvenance(
                    "hessian", "dispersion", producer.strip(), method.strip(), parameters.strip(),
                    geometrySha256, artifacts.get("dispersion_hessian").sha256(),
                    executionIdentity(codeCommit, inputChecksums, outputChecksums, "dispersion_hessian")),
                    "dispersion_hessian", missing, issues);
        }
    }

    private static void validateExecutedComponent(ExecutedComponentProvenance provenance,
            String artifactName, Set<String> missing, List<String> issues) {
        if (provenance.observable().isBlank() || provenance.component().isBlank()
                || provenance.producerBackend().isBlank() || provenance.method().isBlank()
                || provenance.parameters().isBlank()
                || !provenance.geometrySha256().matches("[0-9a-f]{64}")
                || !provenance.outputSha256().matches("[0-9a-f]{64}")
                || !provenance.executionReceipt().matches("[0-9a-f]{64}")) {
            missing.add(artifactName);
            issues.add(artifactName + " lacks complete executed-component provenance");
        }
    }

    private static String executionIdentity(String codeCommit, String inputChecksums,
            String outputChecksums, String artifactName) {
        return sha256Text(codeCommit.strip() + "\n" + inputChecksums.strip() + "\n"
                + outputChecksums.strip() + "\nobservable=hessian\nartifact=" + artifactName + "\n");
    }

    private static void validateDerivedHessianBindings(Path root,
            Map<String, ScientificArtifactReference> artifacts, boolean composite,
            Set<String> missing, List<String> issues) throws IOException {
        String sourceName = composite ? "total_hessian" : "electronic_hessian";
        ScientificArtifactReference source = artifacts.get(sourceName);
        if (source == null || missing.contains(sourceName)) return;
        validateDerivedBinding(root, artifacts, List.of("frequencies", "frequencies_cm-1"),
                "frequencies_hessian_sha256", source.sha256(), missing, issues);
        validateDerivedBinding(root, artifacts, List.of("normal_modes", "normal_modes_mass_weighted"),
                "normal_modes_hessian_sha256", source.sha256(), missing, issues);
    }

    private static void validateDerivedBinding(Path root,
            Map<String, ScientificArtifactReference> artifacts, List<String> observableNames,
            String bindingName, String expectedHessianSha256,
            Set<String> missing, List<String> issues) throws IOException {
        String observable = observableNames.stream().filter(artifacts::containsKey).findFirst().orElse(null);
        if (observable == null || missing.contains(observable)) return;
        String binding = requireHessianArtifact(root, artifacts, bindingName, missing, issues);
        if (binding != null && !binding.strip().equals(expectedHessianSha256)) {
            missing.add(bindingName);
            issues.add(bindingName + " does not identify the exact Hessian used to derive " + observable);
        }
    }

    private record ExecutedComponentProvenance(
            String observable,
            String component,
            String producerBackend,
            String method,
            String parameters,
            String geometrySha256,
            String outputSha256,
            String executionReceipt) {}

    private static void validateHessianGeometry(Path root,
            Map<String, ScientificArtifactReference> artifacts, String geometryIdentity, String componentText,
            boolean composite, Set<String> missing, List<String> issues) throws IOException {
        String actualGeometry = sha256(root.resolve(artifacts.get("geometry.xyz").path()));
        if (!geometryIdentity.equals(actualGeometry)) {
            missing.add("hessian_geometry_identity");
            issues.add("hessian geometry identity does not match geometry.xyz");
        }
        Properties properties = new Properties();
        properties.load(new java.io.StringReader(componentText));
        String electronicIdentity = properties.getProperty("electronic_identity", "");
        if (!electronicIdentity.equals("TRUSTED_PBE_ONLY_HESSIAN")
                || !properties.getProperty("electronic_geometry_sha256", "").equals(geometryIdentity)) {
            missing.add("hessian_component_identity");
            issues.add("electronic Hessian must be explicitly identified as TRUSTED_PBE_ONLY_HESSIAN at the same geometry");
        }
        if (composite) {
            if (!properties.getProperty("dispersion_identity", "").equals("D3_BJ_ONLY_HESSIAN")
                    || !properties.getProperty("dispersion_geometry_sha256", "").equals(geometryIdentity)
                    || !properties.getProperty("total_identity", "").equals("PBE_D3_BJ_TOTAL_HESSIAN")
                    || !properties.getProperty("total_geometry_sha256", "").equals(geometryIdentity)) {
                missing.add("hessian_component_identity");
                issues.add("composite Hessian component identities or geometry identities are incomplete");
            }
        } else if (properties.containsKey("total_identity")
                && !properties.getProperty("total_identity").equals("TRUSTED_PBE_ONLY_HESSIAN")) {
            missing.add("hessian_component_identity");
            issues.add("pure PBE Hessian is mislabeled as a composite total Hessian");
        }
    }

    private record Matrix(double[][] values, int rows, int columns) {}

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
        if (missing.contains("electronic_hessian") || missing.contains("dispersion_hessian")
                || missing.contains("total_hessian") || missing.contains("hessian_component_identity")
                || missing.contains("hessian_units") || missing.contains("hessian_dimensions")
                || missing.contains("hessian_geometry_identity")) {
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

    private static String sha256Text(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
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
