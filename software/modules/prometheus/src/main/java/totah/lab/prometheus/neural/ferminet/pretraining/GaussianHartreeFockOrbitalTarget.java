package totah.lab.prometheus.neural.ferminet.pretraining;

import totah.lab.prometheus.neural.ferminet.runtime.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Java-only evaluator for frozen PySCF spherical Gaussian occupied
 * molecular-orbital coefficients.
 *
 * <p>This class is intentionally fail-closed. The H2O FermiNet pretraining
 * artifact must be:
 *
 * <ul>
 *   <li>schema {@code prometheus-hf-orbitals-v1}</li>
 *   <li>converged</li>
 *   <li>expressed in bohr</li>
 *   <li>unrestricted Hartree-Fock</li>
 *   <li>cc-pVDZ</li>
 *   <li>consistent with the supplied molecular geometry and spin sector</li>
 * </ul>
 *
 * <p>The evaluator reproduces the spherical Gaussian AO convention used by
 * the frozen PySCF export for angular momenta l = 0, 1, and 2.
 */
public final class GaussianHartreeFockOrbitalTarget
        implements HartreeFockOrbitalTarget {

    private static final String EXPECTED_SCHEMA =
            "prometheus-hf-orbitals-v1";

    private static final String EXPECTED_UNIT =
            "bohr";

    private static final String EXPECTED_BASIS =
            "cc-pvdz";

    private static final double GEOMETRY_TOLERANCE =
            1.0e-12;

    private static final double SQRT_4PI =
            Math.sqrt(4.0 * Math.PI);

    private final Molecule molecule;
    private final List<Shell> shells;
    private final double[][] alpha;
    private final double[][] beta;
    private final Provenance provenance;

    private GaussianHartreeFockOrbitalTarget(
            Molecule molecule,
            List<Shell> shells,
            double[][] alpha,
            double[][] beta,
            Provenance provenance) {

        this.molecule =
                Objects.requireNonNull(
                        molecule,
                        "molecule");

        this.shells =
                List.copyOf(
                        Objects.requireNonNull(
                                shells,
                                "shells"));

        this.alpha =
                copy(
                        Objects.requireNonNull(
                                alpha,
                                "alpha"));

        this.beta =
                copy(
                        Objects.requireNonNull(
                                beta,
                                "beta"));

        this.provenance =
                Objects.requireNonNull(
                        provenance,
                        "provenance");

        int aoCount =
                this.shells
                        .stream()
                        .mapToInt(Shell::aoCount)
                        .sum();

        if (aoCount < 1) {
            throw new IllegalArgumentException(
                    "HF artifact contains no atomic orbitals");
        }

        for (double[] orbital : this.alpha) {

            if (orbital.length != aoCount) {
                throw new IllegalArgumentException(
                        "alpha MO/AO dimension mismatch");
            }

            for (double coefficient : orbital) {
                if (!Double.isFinite(coefficient)) {
                    throw new IllegalArgumentException(
                            "non-finite alpha MO coefficient");
                }
            }
        }

        for (double[] orbital : this.beta) {

            if (orbital.length != aoCount) {
                throw new IllegalArgumentException(
                        "beta MO/AO dimension mismatch");
            }

            for (double coefficient : orbital) {
                if (!Double.isFinite(coefficient)) {
                    throw new IllegalArgumentException(
                            "non-finite beta MO coefficient");
                }
            }
        }

        if (this.alpha.length
                != molecule.spin()
                .alphaElectrons()
                || this.beta.length
                != molecule.spin()
                .betaElectrons()) {

            throw new IllegalArgumentException(
                    "occupied MO count/spin mismatch");
        }
    }

    /**
     * Reads and validates a frozen PySCF orbital artifact.
     */
    public static GaussianHartreeFockOrbitalTarget read(
            Path artifact,
            Molecule molecule)
            throws IOException {

        Objects.requireNonNull(
                artifact,
                "artifact");

        Objects.requireNonNull(
                molecule,
                "molecule");

        JsonNode root =
                new ObjectMapper()
                        .readTree(
                                artifact.toFile());

        /*
         * ------------------------------------------------------------
         * Schema and scientific-state validation
         * ------------------------------------------------------------
         */

        if (!EXPECTED_SCHEMA.equals(
                root.path("schema")
                        .asText())) {

            throw new IOException(
                    "unsupported HF orbital artifact schema");
        }

        if (!root.path("converged")
                .asBoolean(false)) {

            throw new IOException(
                    "HF orbital artifact is not converged");
        }

        if (!EXPECTED_UNIT.equals(
                root.path("unit")
                        .asText())) {

            throw new IOException(
                    "HF artifact coordinates must be bohr");
        }

        /*
         * This resource is explicitly the unrestricted H2O target.
         */
        if (root.path("restricted")
                .asBoolean(true)) {

            throw new IOException(
                    "H2O FermiNet pretraining requires unrestricted HF artifact");
        }

        String basis =
                root.path("basis")
                        .asText();

        if (!EXPECTED_BASIS.equalsIgnoreCase(
                basis)) {

            throw new IOException(
                    "HF artifact basis must be cc-pVDZ");
        }

        /*
         * ------------------------------------------------------------
         * Spin/electron validation
         * ------------------------------------------------------------
         */

        JsonNode electronNode =
                root.path("electrons");

        if (!electronNode.isArray()
                || electronNode.size() != 2) {

            throw new IOException(
                    "HF artifact electrons field must contain [alpha,beta]");
        }

        if (electronNode.get(0)
                .asInt()
                != molecule.spin()
                .alphaElectrons()
                || electronNode.get(1)
                .asInt()
                != molecule.spin()
                .betaElectrons()) {

            throw new IOException(
                    "HF artifact electron/spin mismatch");
        }

        /*
         * ------------------------------------------------------------
         * Geometry validation
         * ------------------------------------------------------------
         */

        validateGeometry(
                root.path(
                        "molecule_scientific_geometry"),
                molecule);

        /*
         * ------------------------------------------------------------
         * Gaussian basis validation
         * ------------------------------------------------------------
         */

        JsonNode shellNode =
                root.path("shells");

        if (!shellNode.isArray()
                || shellNode.isEmpty()) {

            throw new IOException(
                    "HF artifact contains no Gaussian shells");
        }

        List<Shell> shells =
                new ArrayList<>();

        for (JsonNode node : shellNode) {

            int atom =
                    requiredInteger(
                            node,
                            "atom");

            if (atom < 0
                    || atom
                    >= molecule.nuclei()
                    .size()) {

                throw new IOException(
                        "HF shell references invalid atom index: "
                                + atom);
            }

            int angularMomentum =
                    requiredInteger(
                            node,
                            "l");

            if (angularMomentum < 0
                    || angularMomentum > 2) {

                throw new IOException(
                        "unsupported spherical Gaussian angular momentum: "
                                + angularMomentum);
            }

            double[] exponents =
                    vector(
                            node.path(
                                    "exponents"));

            for (double exponent : exponents) {

                if (!(exponent > 0.0)
                        || !Double.isFinite(exponent)) {

                    throw new IOException(
                            "Gaussian exponent must be finite and positive");
                }
            }

            double[][] contractions =
                    matrix(
                            node.path(
                                    "contractions"));

            if (contractions.length < 1) {
                throw new IOException(
                        "Gaussian shell must contain at least one contraction");
            }

            for (double[] contraction : contractions) {

                if (contraction.length
                        != exponents.length) {

                    throw new IOException(
                            "primitive/contraction mismatch");
                }
            }

            shells.add(
                    new Shell(
                            atom,
                            angularMomentum,
                            exponents,
                            contractions));
        }

        /*
         * ------------------------------------------------------------
         * MO coefficients and provenance
         * ------------------------------------------------------------
         */

        double[][] alpha =
                matrix(
                        root.path(
                                "alpha_occupied_coefficients"));

        double[][] beta =
                matrix(
                        root.path(
                                "beta_occupied_coefficients"));

        double scfEnergy =
                requiredFinite(
                        root,
                        "energy_hartree");

        Provenance provenance =
                new Provenance(
                        requiredText(
                                root,
                                "ferminet_commit"),
                        requiredText(
                                root,
                                "generator"),
                        basis,
                        false,
                        scfEnergy,
                        artifact
                                .toAbsolutePath()
                                .normalize());

        try {

            return new GaussianHartreeFockOrbitalTarget(
                    molecule,
                    shells,
                    alpha,
                    beta,
                    provenance);

        } catch (IllegalArgumentException exception) {

            throw new IOException(
                    "invalid HF orbital artifact: "
                            + exception.getMessage(),
                    exception);
        }
    }

    public Provenance provenance() {
        return provenance;
    }

    /**
     * Evaluates occupied alpha and beta HF orbital matrices at one electronic
     * configuration.
     *
     * <p>Electron ordering is a scientific invariant:
     *
     * <pre>
     * alpha electrons first,
     * beta electrons second.
     * </pre>
     */
    @Override
    public OrbitalMatrices evaluate(
            QuantumCoordinates coordinates) {

        Objects.requireNonNull(
                coordinates,
                "coordinates");

        if (coordinates.particles()
                .size()
                != molecule.electrons()
                .value()) {

            throw new IllegalArgumentException(
                    "electron count mismatch");
        }

        int alphaElectrons =
                molecule.spin()
                        .alphaElectrons();

        int betaElectrons =
                molecule.spin()
                        .betaElectrons();

        /*
         * Fail closed on spin ordering rather than silently using the wrong
         * occupied-orbital coefficient matrix.
         */
        for (int i = 0;
             i < coordinates.particles()
                     .size();
             i++) {

            SpinProjection expected =
                    i < alphaElectrons
                            ? SpinProjection.ALPHA
                            : SpinProjection.BETA;

            if (coordinates.particles()
                    .get(i)
                    .spin()
                    != expected) {

                throw new IllegalArgumentException(
                        "HF target electrons must be ordered alpha then beta");
            }
        }

        double[][] alphaMatrix =
                new double[alphaElectrons]
                        [alphaElectrons];

        double[][] betaMatrix =
                new double[betaElectrons]
                        [betaElectrons];

        /*
         * Alpha occupied orbitals.
         */
        for (int electron = 0;
             electron < alphaElectrons;
             electron++) {

            double[] ao =
                    atomicOrbitals(
                            coordinates.particles()
                                    .get(electron));

            for (int orbital = 0;
                 orbital < alphaElectrons;
                 orbital++) {

                alphaMatrix[electron][orbital] =
                        dot(
                                ao,
                                alpha[orbital]);
            }
        }

        /*
         * Beta occupied orbitals.
         */
        for (int electron = 0;
             electron < betaElectrons;
             electron++) {

            double[] ao =
                    atomicOrbitals(
                            coordinates.particles()
                                    .get(
                                            alphaElectrons
                                                    + electron));

            for (int orbital = 0;
                 orbital < betaElectrons;
                 orbital++) {

                betaMatrix[electron][orbital] =
                        dot(
                                ao,
                                beta[orbital]);
            }
        }

        return new OrbitalMatrices(
                alphaMatrix,
                betaMatrix);
    }

    /**
     * Evaluates every spherical atomic orbital at one electron position.
     *
     * <p>Ordering follows the serialized shell order, contraction order and
     * spherical-component order:
     *
     * <pre>
     * s : s
     * p : px, py, pz
     * d : dxy, dyz, dz2, dxz, dx2-y2
     * </pre>
     */
    private double[] atomicOrbitals(
            QuantumCoordinates.ParticleCoordinate electron) {

        List<Double> values =
                new ArrayList<>();

        for (Shell shell : shells) {

            CartesianPosition center =
                    molecule.nuclei()
                            .get(shell.atom())
                            .position()
                            .inBohr();

            double x =
                    electron.xBohr()
                            - center.x();

            double y =
                    electron.yBohr()
                            - center.y();

            double z =
                    electron.zBohr()
                            - center.z();

            double r2 =
                    x * x
                            + y * y
                            + z * z;

            for (double[] contraction :
                    shell.contractions()) {

                double radial =
                        0.0;

                for (int primitive = 0;
                     primitive
                             < shell.exponents()
                             .length;
                     primitive++) {

                    double exponent =
                            shell.exponents()
                                    [primitive];

                    radial +=
                            contraction[primitive]
                                    * radialNormalization(
                                    shell.angularMomentum(),
                                    exponent)
                                    * Math.exp(
                                    -exponent
                                            * r2);
                }

                appendAngular(
                        values,
                        shell.angularMomentum(),
                        x,
                        y,
                        z,
                        radial);
            }
        }

        double[] result =
                new double[
                        values.size()];

        for (int i = 0;
             i < result.length;
             i++) {

            result[i] =
                    values.get(i);
        }

        return result;
    }

    /**
     * Real spherical harmonic angular factors matching the frozen PySCF
     * spherical AO convention for l <= 2.
     */
    private static void appendAngular(
            List<Double> values,
            int angularMomentum,
            double x,
            double y,
            double z,
            double radial) {

        if (angularMomentum == 0) {

            values.add(
                    radial
                            / SQRT_4PI);

            return;
        }

        if (angularMomentum == 1) {

            double coefficient =
                    Math.sqrt(
                            3.0
                                    / (4.0
                                    * Math.PI));

            values.add(
                    coefficient
                            * x
                            * radial);

            values.add(
                    coefficient
                            * y
                            * radial);

            values.add(
                    coefficient
                            * z
                            * radial);

            return;
        }

        if (angularMomentum == 2) {

            double xy =
                    Math.sqrt(
                            15.0
                                    / (4.0
                                    * Math.PI));

            double z2 =
                    Math.sqrt(
                            5.0
                                    / (16.0
                                    * Math.PI));

            double x2MinusY2 =
                    Math.sqrt(
                            15.0
                                    / (16.0
                                    * Math.PI));

            /*
             * PySCF real-spherical d ordering:
             *
             * xy
             * yz
             * 2z^2-x^2-y^2
             * xz
             * x^2-y^2
             */
            values.add(
                    xy
                            * x
                            * y
                            * radial);

            values.add(
                    xy
                            * y
                            * z
                            * radial);

            values.add(
                    z2
                            * (2.0
                            * z
                            * z
                            - x
                            * x
                            - y
                            * y)
                            * radial);

            values.add(
                    xy
                            * x
                            * z
                            * radial);

            values.add(
                    x2MinusY2
                            * (x
                            * x
                            - y
                            * y)
                            * radial);

            return;
        }

        throw new IllegalArgumentException(
                "unsupported spherical Gaussian angular momentum: "
                        + angularMomentum);
    }

    /**
     * PySCF gto_norm radial normalization:
     *
     * sqrt(
     *   2^(2l+3)
     *   (l+1)!
     *   (2a)^(l+3/2)
     *   /
     *   ((2l+2)! sqrt(pi))
     * )
     */
    private static double radialNormalization(
            int angularMomentum,
            double exponent) {

        if (angularMomentum < 0) {
            throw new IllegalArgumentException(
                    "angular momentum must be non-negative");
        }

        if (!(exponent > 0.0)
                || !Double.isFinite(exponent)) {

            throw new IllegalArgumentException(
                    "Gaussian exponent must be finite and positive");
        }

        return Math.sqrt(
                Math.pow(
                        2.0,
                        2
                                * angularMomentum
                                + 3)
                        * factorial(
                        angularMomentum
                                + 1)
                        * Math.pow(
                        2.0
                                * exponent,
                        angularMomentum
                                + 1.5)
                        / (factorial(
                        2
                                * angularMomentum
                                + 2)
                        * Math.sqrt(
                        Math.PI)));
    }

    private static long factorial(
            int n) {

        if (n < 0) {
            throw new IllegalArgumentException(
                    "factorial argument must be non-negative");
        }

        long value =
                1L;

        for (int i = 2;
             i <= n;
             i++) {

            value =
                    Math.multiplyExact(
                            value,
                            i);
        }

        return value;
    }

    private static double dot(
            double[] left,
            double[] right) {

        if (left.length
                != right.length) {

            throw new IllegalArgumentException(
                    "dot-product dimension mismatch");
        }

        double value =
                0.0;

        for (int i = 0;
             i < left.length;
             i++) {

            value +=
                    left[i]
                            * right[i];
        }

        return value;
    }

    private static void validateGeometry(
            JsonNode geometry,
            Molecule molecule)
            throws IOException {

        if (!geometry.isArray()
                || geometry.size()
                != molecule.nuclei()
                .size()) {

            throw new IOException(
                    "HF artifact nuclear count mismatch");
        }

        for (int i = 0;
             i < geometry.size();
             i++) {

            JsonNode row =
                    geometry.get(i);

            if (!row.isArray()
                    || row.size() != 2) {

                throw new IOException(
                        "invalid HF geometry row at nucleus "
                                + i);
            }

            JsonNode elementNode =
                    row.get(0);

            JsonNode xyz =
                    row.get(1);

            if (!elementNode.isTextual()
                    || !xyz.isArray()
                    || xyz.size() != 3) {

                throw new IOException(
                        "invalid HF geometry row at nucleus "
                                + i);
            }

            CartesianPosition expected =
                    molecule.nuclei()
                            .get(i)
                            .position()
                            .inBohr();

            if (!elementNode.asText()
                    .equals(
                            molecule.nuclei()
                                    .get(i)
                                    .element())
                    || Math.abs(
                    xyz.get(0)
                            .asDouble()
                            - expected.x())
                    > GEOMETRY_TOLERANCE
                    || Math.abs(
                    xyz.get(1)
                            .asDouble()
                            - expected.y())
                    > GEOMETRY_TOLERANCE
                    || Math.abs(
                    xyz.get(2)
                            .asDouble()
                            - expected.z())
                    > GEOMETRY_TOLERANCE) {

                throw new IOException(
                        "HF artifact geometry mismatch at nucleus "
                                + i);
            }
        }
    }

    private static String requiredText(
            JsonNode root,
            String field)
            throws IOException {

        JsonNode value =
                root.path(field);

        if (!value.isTextual()
                || value.asText()
                .isBlank()) {

            throw new IOException(
                    "missing/blank "
                            + field);
        }

        return value.asText();
    }

    private static int requiredInteger(
            JsonNode root,
            String field)
            throws IOException {

        JsonNode value =
                root.path(field);

        if (!value.isIntegralNumber()) {

            throw new IOException(
                    "missing/non-integral "
                            + field);
        }

        return value.asInt();
    }

    private static double requiredFinite(
            JsonNode root,
            String field)
            throws IOException {

        JsonNode value =
                root.path(field);

        if (!value.isNumber()
                || !Double.isFinite(
                value.doubleValue())) {

            throw new IOException(
                    "missing/non-finite "
                            + field);
        }

        return value.doubleValue();
    }

    private static double[] vector(
            JsonNode node)
            throws IOException {

        if (!node.isArray()
                || node.isEmpty()) {

            throw new IOException(
                    "expected non-empty numeric vector");
        }

        double[] result =
                new double[
                        node.size()];

        for (int i = 0;
             i < result.length;
             i++) {

            JsonNode value =
                    node.get(i);

            if (!value.isNumber()
                    || !Double.isFinite(
                    value.doubleValue())) {

                throw new IOException(
                        "invalid numeric vector");
            }

            result[i] =
                    value.doubleValue();
        }

        return result;
    }

    private static double[][] matrix(
            JsonNode node)
            throws IOException {

        if (!node.isArray()
                || node.isEmpty()) {

            throw new IOException(
                    "expected non-empty numeric matrix");
        }

        double[][] result =
                new double[
                        node.size()][];

        int expectedColumns =
                -1;

        for (int i = 0;
             i < result.length;
             i++) {

            result[i] =
                    vector(
                            node.get(i));

            if (expectedColumns < 0) {

                expectedColumns =
                        result[i].length;

            } else if (result[i].length
                    != expectedColumns) {

                throw new IOException(
                        "ragged numeric matrix");
            }
        }

        return result;
    }

    private static double[][] copy(
            double[][] values) {

        double[][] result =
                new double[
                        values.length][];

        for (int i = 0;
             i < values.length;
             i++) {

            result[i] =
                    values[i]
                            .clone();
        }

        return result;
    }

    private record Shell(
            int atom,
            int angularMomentum,
            double[] exponents,
            double[][] contractions) {

        private Shell {

            exponents =
                    Objects.requireNonNull(
                                    exponents,
                                    "exponents")
                            .clone();

            contractions =
                    copy(
                            Objects.requireNonNull(
                                    contractions,
                                    "contractions"));

            if (atom < 0) {
                throw new IllegalArgumentException(
                        "invalid Gaussian shell atom index");
            }

            if (angularMomentum < 0
                    || angularMomentum > 2) {

                throw new IllegalArgumentException(
                        "invalid Gaussian shell angular momentum");
            }

            if (exponents.length < 1) {
                throw new IllegalArgumentException(
                        "Gaussian shell contains no primitives");
            }

            for (double exponent :
                    exponents) {

                if (!(exponent > 0.0)
                        || !Double.isFinite(exponent)) {

                    throw new IllegalArgumentException(
                            "Gaussian exponent must be finite and positive");
                }
            }

            if (contractions.length < 1) {
                throw new IllegalArgumentException(
                        "Gaussian shell contains no contractions");
            }

            for (double[] contraction :
                    contractions) {

                if (contraction.length
                        != exponents.length) {

                    throw new IllegalArgumentException(
                            "primitive/contraction mismatch");
                }

                for (double coefficient :
                        contraction) {

                    if (!Double.isFinite(coefficient)) {
                        throw new IllegalArgumentException(
                                "non-finite contraction coefficient");
                    }
                }
            }
        }

        @Override
        public double[] exponents() {
            return exponents.clone();
        }

        @Override
        public double[][] contractions() {
            return copy(contractions);
        }

        private int aoCount() {

            return Math.multiplyExact(
                    contractions.length,
                    2
                            * angularMomentum
                            + 1);
        }
    }

    public record Provenance(
            String ferminetCommit,
            String generator,
            String basis,
            boolean restricted,
            double scfEnergyHartree,
            Path artifact) {

        public Provenance {

            Objects.requireNonNull(
                    ferminetCommit,
                    "ferminetCommit");

            Objects.requireNonNull(
                    generator,
                    "generator");

            Objects.requireNonNull(
                    basis,
                    "basis");

            Objects.requireNonNull(
                    artifact,
                    "artifact");

            if (ferminetCommit.isBlank()) {
                throw new IllegalArgumentException(
                        "blank FermiNet commit");
            }

            if (generator.isBlank()) {
                throw new IllegalArgumentException(
                        "blank HF generator");
            }

            if (basis.isBlank()) {
                throw new IllegalArgumentException(
                        "blank HF basis");
            }

            if (!Double.isFinite(
                    scfEnergyHartree)) {

                throw new IllegalArgumentException(
                        "non-finite SCF energy");
            }
        }
    }
}