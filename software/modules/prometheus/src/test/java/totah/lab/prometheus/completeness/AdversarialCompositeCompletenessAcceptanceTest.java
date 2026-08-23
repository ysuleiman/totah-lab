package totah.lab.prometheus.completeness;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Independent adversarial falsification audit (ADV-COMP-*) of the composite-observable
 * completeness defect class behind the TSL PBE-D3(BJ) Hessian incident: a producer ran a
 * PBE-only analytic Hessian while the campaign claimed a composite PBE-D3(BJ) Hessian.
 *
 * <p>General invariant under audit: a claimed scientific observable must equal the set of
 * all required executed components, which must equal the set of all persisted components.
 *
 * <p>Fixtures are synthetic: a 2-atom asymmetric H/Cl molecule with hand-picked,
 * non-accidental symmetric 6x6 Hessian components; the total Hessian is computed
 * elementwise BY THIS TEST. All digests are computed here with an independent SHA-256
 * implementation. Several tests (ADV-COMP-R1..R6) are EXPECTED TO FAIL on the current
 * working tree: they encode invariants the gate does not yet enforce and must be
 * preserved red until the production code grows to satisfy them. Assertions must not be
 * weakened to make this suite green.
 */
class AdversarialCompositeCompletenessAcceptanceTest {

    @TempDir
    Path bundleRoot;

    private final ScientificResultCompletenessValidator validator =
            new ScientificResultCompletenessValidator();

    // ------------------------------------------------------------------ fixtures

    /** Complete valid composite PBE-D3(BJ) bundle; the control every mutation clones. */
    private QmBundle completeCompositeBundle() throws Exception {
        QmBundle bundle = new QmBundle();
        bundle.writeCommonScalars("PBE-D3(BJ)/def2-SVP\n",
                "D3(BJ) a1=0.4289 a2=4.4407 alp=14.0 s8=0.7875 s9=0.0\n");
        bundle.writeHessianRequested("true\n");
        bundle.total = add(bundle.electronic, bundle.dispersion);
        bundle.writeArtifact("hessian_units", bundle.units);
        bundle.writeArtifact("hessian_dimensions", "6x6\n");
        bundle.writeArtifact("hessian_geometry_identity", bundle.geometrySha + "\n");
        bundle.writeArtifact("electronic_hessian", matrixText(bundle.electronic));
        bundle.writeArtifact("dispersion_hessian", matrixText(bundle.dispersion));
        bundle.writeArtifact("total_hessian", matrixText(bundle.total));
        bundle.writeArtifact("hessian_component_identity",
                componentIdentity(bundle.geometrySha, true));
        return bundle;
    }

    /** Complete valid non-composite PBE bundle with an explicitly PBE-only Hessian. */
    private QmBundle completePurePbeBundle() throws Exception {
        QmBundle bundle = new QmBundle();
        bundle.writeCommonScalars("PBE/def2-SVP\n", "none\n");
        bundle.writeHessianRequested("true\n");
        bundle.writeArtifact("hessian_units", bundle.units);
        bundle.writeArtifact("hessian_dimensions", "6x6\n");
        bundle.writeArtifact("hessian_geometry_identity", bundle.geometrySha + "\n");
        bundle.writeArtifact("electronic_hessian", matrixText(bundle.electronic));
        bundle.writeArtifact("hessian_component_identity",
                componentIdentity(bundle.geometrySha, false));
        return bundle;
    }

    private void rewriteHessianMatrices(QmBundle bundle) throws Exception {
        bundle.writeArtifact("electronic_hessian", matrixText(bundle.electronic));
        if (bundle.dispersion != null) {
            bundle.writeArtifact("dispersion_hessian", matrixText(bundle.dispersion));
        }
        if (bundle.total != null) {
            bundle.writeArtifact("total_hessian", matrixText(bundle.total));
        }
    }

    private ValidationResultAssertion assertRejected(ScientificResultManifest manifest)
            throws IOException {
        ScientificResultCompletenessValidator.ValidationResult result =
                validator.validate(bundleRoot, manifest);
        assertThat(result.status())
                .as("bundle must not pass the persistence gate")
                .isNotEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
        return new ValidationResultAssertion(result);
    }

    private record ValidationResultAssertion(
            ScientificResultCompletenessValidator.ValidationResult result) {
        ValidationResultAssertion withIssueContaining(String fragment) {
            assertThat(result.issues())
                    .as("issues must name '%s'", fragment)
                    .anyMatch(issue -> issue.contains(fragment));
            return this;
        }

        ValidationResultAssertion withStatus(ScientificResultCompleteness status) {
            assertThat(result.status()).isEqualTo(status);
            return this;
        }
    }

    // ------------------------------------------------------- expected-green cases

    /** ADV-COMP-G1 control: a fully consistent composite bundle must pass. */
    @Test
    void validCompositeBundleIsReproducibleComplete() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        assertThat(validator.validate(bundleRoot, bundle.manifest()).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    /** ADV-COMP-G2: composite bundle missing the dispersion_hessian artifact fails closed. */
    @Test
    void compositeMissingDispersionHessianIsComponentDecompositionIncomplete() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.remove("dispersion_hessian");
        assertRejected(bundle.manifest())
                .withStatus(ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION)
                .withIssueContaining("dispersion_hessian");
    }

    /**
     * ADV-COMP-G3: the original TSL defect shape. The method string is the full composite
     * campaign label and only the PBE-only electronic Hessian was persisted; the gate must
     * refuse to call this complete.
     */
    @Test
    void compositeMethodWithOnlyPbeOnlyHessianIsRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.writeArtifact("method",
                "PBE-D3(BJ)/def2-SVP density-fitted gas phase analytic Hessian\n");
        bundle.remove("dispersion_hessian");
        bundle.remove("total_hessian");
        bundle.writeArtifact("hessian_component_identity",
                componentIdentity(bundle.geometrySha, false));
        assertRejected(bundle.manifest())
                .withStatus(ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION);
    }

    /**
     * ADV-COMP-G4 relabel trap: the producer ships the electronic matrix under the
     * total_hessian name with correct checksums. Since D != 0, T(=E) != E+D and the sum
     * identity must reject it.
     */
    @Test
    void electronicHessianRelabeledAsTotalFailsSumIdentity() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.writeArtifact("total_hessian", matrixText(bundle.electronic));
        assertRejected(bundle.manifest()).withIssueContaining("total_hessian");
    }

    /** ADV-COMP-G5: a total off by 1e-6 (symmetric perturbation) exceeds the declared tolerance. */
    @Test
    void totalPerturbedBeyondToleranceIsRejectedAndNamesTotalHessian() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.total[0][1] += 1.0e-6;
        bundle.total[1][0] += 1.0e-6;
        bundle.writeArtifact("total_hessian", matrixText(bundle.total));
        assertRejected(bundle.manifest()).withIssueContaining("total_hessian");
    }

    /**
     * ADV-COMP-G6 boundary: a 1e-13 absolute perturbation sits inside the declared sum
     * tolerance (1e-12 abs + 1e-10 rel) and must still pass; documents the tolerance floor.
     */
    @Test
    void totalPerturbedWithinDeclaredToleranceRemainsComplete() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.total[0][1] += 1.0e-13;
        bundle.total[1][0] += 1.0e-13;
        bundle.writeArtifact("total_hessian", matrixText(bundle.total));
        assertThat(validator.validate(bundleRoot, bundle.manifest()).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    /** ADV-COMP-G7: a NaN entry in the electronic Hessian is not finite and must be rejected. */
    @Test
    void nanEntryInElectronicHessianIsRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.electronic[2][4] = Double.NaN;
        bundle.electronic[4][2] = Double.NaN;
        bundle.writeArtifact("electronic_hessian", matrixText(bundle.electronic));
        assertRejected(bundle.manifest()).withIssueContaining("electronic_hessian");
    }

    /** ADV-COMP-G8: an infinite entry in the total Hessian is not finite and must be rejected. */
    @Test
    void infiniteEntryInTotalHessianIsRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.total[3][3] = Double.POSITIVE_INFINITY;
        bundle.writeArtifact("total_hessian", matrixText(bundle.total));
        assertRejected(bundle.manifest()).withIssueContaining("total_hessian");
    }

    /** ADV-COMP-G9: declared 5x5 for a 2-atom (3N=6) system must be rejected. */
    @Test
    void declaredDimensionsBelowThreeNAreRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.writeArtifact("hessian_dimensions", "5x5\n");
        assertRejected(bundle.manifest()).withIssueContaining("hessian_dimensions");
    }

    /**
     * ADV-COMP-G10: a rectangular 6x5 electronic matrix against a declared 6x6 must be
     * rejected. PRESERVED RED on the current tree: the dimension diagnostic fires, but
     * {@code validateHessianSum} then indexes the short matrix with the total's dimensions
     * and the gate escapes with ArrayIndexOutOfBoundsException instead of returning a
     * clean rejection. Fail-closed by accident, not by design — the validator must guard
     * component shapes before the sum identity. Do not weaken.
     */
    @Test
    void rectangularMatrixWithSquareDeclarationIsRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        double[][] rectangular = new double[6][5];
        for (int row = 0; row < 6; row++) {
            System.arraycopy(bundle.electronic[row], 0, rectangular[row], 0, 5);
        }
        bundle.writeArtifact("electronic_hessian", matrixText(rectangular));
        assertRejected(bundle.manifest()).withIssueContaining("electronic_hessian");
    }

    /** ADV-COMP-G11: a 7x7 matrix against a declared 6x6 must be rejected. */
    @Test
    void oversizedMatrixAgainstDeclarationIsRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        double[][] oversized = new double[7][7];
        for (int row = 0; row < 6; row++) {
            System.arraycopy(bundle.electronic[row], 0, oversized[row], 0, 6);
        }
        oversized[6][6] = 0.377;
        oversized[0][6] = 0.011;
        oversized[6][0] = 0.011;
        bundle.writeArtifact("electronic_hessian", matrixText(oversized));
        assertRejected(bundle.manifest()).withIssueContaining("electronic_hessian");
    }

    /**
     * ADV-COMP-G12: E[0][3] vs E[3][0] differing by 1e-7 exceeds the 1e-8 symmetry
     * tolerance; the total is recomputed so ONLY the symmetry diagnostic can fire.
     */
    @Test
    void asymmetryBeyondToleranceIsRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.electronic[0][3] += 1.0e-7;
        bundle.total = add(bundle.electronic, bundle.dispersion);
        rewriteHessianMatrices(bundle);
        assertRejected(bundle.manifest()).withIssueContaining("symmetry");
    }

    /** ADV-COMP-G13 boundary: a 1e-9 asymmetry sits inside the 1e-8 tolerance and must pass. */
    @Test
    void asymmetryWithinToleranceRemainsComplete() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.electronic[0][3] += 1.0e-9;
        bundle.total = add(bundle.electronic, bundle.dispersion);
        rewriteHessianMatrices(bundle);
        assertThat(validator.validate(bundleRoot, bundle.manifest()).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    /** ADV-COMP-G14: hessian_geometry_identity bound to a DIFFERENT geometry must be rejected. */
    @Test
    void hessianGeometryIdentityFromDifferentGeometryIsRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        String otherGeometrySha = sha256("3\nunrelated geometry\nH 0.0 0.0 0.0\nH 0.0 0.0 1.0\nH 1.0 0.0 0.0\n");
        bundle.writeArtifact("hessian_geometry_identity", otherGeometrySha + "\n");
        // keep the component identity internally consistent with the wrong binding so only
        // the geometry-identity-vs-geometry.xyz comparison can fire
        bundle.writeArtifact("hessian_component_identity",
                componentIdentity(otherGeometrySha, true));
        assertRejected(bundle.manifest()).withIssueContaining("geometry identity");
    }

    /** ADV-COMP-G15: a dispersion component bound to a different geometry must be rejected. */
    @Test
    void dispersionComponentAtDifferentGeometryIsRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        String wrongSha = sha256("some other geometry\n");
        String identity = "electronic_identity=TRUSTED_PBE_ONLY_HESSIAN\n"
                + "electronic_geometry_sha256=" + bundle.geometrySha + "\n"
                + "dispersion_identity=D3_BJ_ONLY_HESSIAN\n"
                + "dispersion_geometry_sha256=" + wrongSha + "\n"
                + "total_identity=PBE_D3_BJ_TOTAL_HESSIAN\n"
                + "total_geometry_sha256=" + bundle.geometrySha + "\n";
        bundle.writeArtifact("hessian_component_identity", identity);
        assertRejected(bundle.manifest())
                .withIssueContaining("composite Hessian component identities or geometry identities");
    }

    /** ADV-COMP-G16: an honest pure-PBE Hessian with an explicit PBE-only identity passes. */
    @Test
    void purePbeHessianWithExplicitPbeOnlyIdentityIsComplete() throws Exception {
        QmBundle bundle = completePurePbeBundle();
        assertThat(validator.validate(bundleRoot, bundle.manifest()).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    /** ADV-COMP-G17: a pure-PBE bundle declaring a composite total identity is mislabeled. */
    @Test
    void purePbeHessianDeclaringCompositeTotalIdentityIsRejected() throws Exception {
        QmBundle bundle = completePurePbeBundle();
        String identity = "electronic_identity=TRUSTED_PBE_ONLY_HESSIAN\n"
                + "electronic_geometry_sha256=" + bundle.geometrySha + "\n"
                + "total_identity=PBE_D3_BJ_TOTAL_HESSIAN\n"
                + "total_geometry_sha256=" + bundle.geometrySha + "\n";
        bundle.writeArtifact("hessian_component_identity", identity);
        assertRejected(bundle.manifest()).withIssueContaining("mislabeled");
    }

    /** ADV-COMP-G18: total_hessian deleted after manifest creation must be rejected. */
    @Test
    void totalHessianDeletedAfterManifestCreationIsRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        Files.delete(bundleRoot.resolve("total_hessian"));
        assertRejected(bundle.manifest())
                .withStatus(ScientificResultCompleteness.INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION)
                .withIssueContaining("total_hessian");
    }

    /** ADV-COMP-G19: total_hessian modified after checksumming must trip the digest check. */
    @Test
    void totalHessianModifiedAfterChecksummingIsRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        Path totalPath = bundleRoot.resolve("total_hessian");
        String original = Files.readString(totalPath);
        assertThat(original).contains("0");
        Files.writeString(totalPath, original.replaceFirst("0", "1"));
        assertRejected(bundle.manifest()).withIssueContaining("checksum mismatch: total_hessian");
    }

    /** ADV-COMP-G20: hessian_requested="yes" is not a boolean and must be rejected. */
    @Test
    void nonBooleanHessianRequestedIsRejected() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.writeHessianRequested("yes\n");
        assertRejected(bundle.manifest()).withIssueContaining("true or false");
    }

    /** ADV-COMP-G21: Hessian simply not requested; no Hessian artifacts; must pass. */
    @Test
    void hessianNotRequestedWithoutHessianArtifactsIsComplete() throws Exception {
        QmBundle bundle = new QmBundle();
        bundle.writeCommonScalars("PBE-D3(BJ)/def2-SVP\n",
                "D3(BJ) a1=0.4289 a2=4.4407 alp=14.0 s8=0.7875 s9=0.0\n");
        bundle.writeHessianRequested("false\n");
        assertThat(validator.validate(bundleRoot, bundle.manifest()).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    // -------------------------------------- expected-red cases (invariants not yet enforced)

    /**
     * ADV-COMP-R1 EXPECTED RED. Invariant: Hessian units must be the locked vocabulary
     * "hartree/bohr^2". The current gate checks only nonblankness, so a kcal/mol/angstrom^2
     * Hessian (a ~23.06x numeric misinterpretation) passes today. Do not weaken.
     */
    @Test
    void rejectsNonLockedHessianUnitsVocabulary() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.writeArtifact("hessian_units", "kcal/mol/angstrom^2\n");
        assertRejected(bundle.manifest()).withIssueContaining("hessian_units");
    }

    /**
     * ADV-COMP-R2 EXPECTED RED. Invariant: for a composite method the persisted energies
     * must compose: total_energy == electronic_energy + dispersion_energy. Claiming
     * -76.5 against -76.0 + -0.02 must be rejected; the current gate performs no semantic
     * energy check.
     */
    @Test
    void rejectsInconsistentEnergyComponentSum() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.writeArtifact("total_energy", "-76.5\n");
        assertRejected(bundle.manifest()).withIssueContaining("total_energy");
    }

    /**
     * ADV-COMP-R3 EXPECTED RED. Invariant: total_gradient must equal
     * electronic_gradient + dispersion_gradient componentwise. The current gate checks
     * only presence and digest.
     */
    @Test
    void rejectsInconsistentGradientComponentSum() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.totalGradient[0][0] += 0.05;
        bundle.writeArtifact("total_gradient", gradientText(bundle.totalGradient));
        assertRejected(bundle.manifest()).withIssueContaining("total_gradient");
    }

    /**
     * ADV-COMP-R4 EXPECTED RED. Invariant: force must equal the negative total gradient.
     * The current gate never relates the two required artifacts.
     */
    @Test
    void rejectsForceNotEqualNegativeTotalGradient() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.force[1][2] = -bundle.force[1][2];
        bundle.writeArtifact("force", gradientText(bundle.force));
        assertRejected(bundle.manifest()).withIssueContaining("force");
    }

    /**
     * ADV-COMP-R5 EXPECTED RED. Invariant: EVERY artifact a manifest lists must be
     * integrity-verified, not only the required set. Here the manifest lists
     * frequencies_cm-1 — the real TSL defect's propagation vector, frequencies derived
     * from the PBE-only Hessian riding the composite label — whose content is garbage and
     * whose recorded digest is well-formed but wrong. The current gate never verifies
     * extra artifacts and returns COMPLETE.
     */
    @Test
    void rejectsUnverifiedExtraManifestArtifact() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        Files.writeString(bundleRoot.resolve("frequencies_cm-1"),
                "not-a-frequency garbage riding the composite label\n");
        bundle.artifacts.put("frequencies_cm-1", new ScientificArtifactReference(
                Path.of("frequencies_cm-1"), sha256("the digest the producer should have recorded\n")));
        assertRejected(bundle.manifest()).withIssueContaining("frequencies_cm-1");
    }

    /**
     * ADV-COMP-R6 EXPECTED RED. Invariant: an identically-zero declared-executed component
     * is indistinguishable from an unexecuted one; a real D3(BJ) Hessian of a nonlinear
     * molecule is never exactly zero. T = E + 0 satisfies the sum identity, so the current
     * gate accepts a fabricated zero dispersion component. Do not weaken.
     */
    @Test
    void rejectsIdenticallyZeroDispersionHessianComponent() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.dispersion = new double[6][6];
        bundle.total = add(bundle.electronic, bundle.dispersion);
        rewriteHessianMatrices(bundle);
        assertRejected(bundle.manifest()).withIssueContaining("dispersion_hessian");
    }

    // ------------------------------- characterization cases (document architectural limits)

    /**
     * ADV-COMP-C1 characterization. Atom-order truth is NOT verifiable at this gate: all
     * three Hessians are replaced by their simultaneously atom-permuted versions (atoms
     * 0&lt;-&gt;1, i.e. coordinate blocks swapped consistently). The permutation preserves
     * symmetry and the component sum, so the gate still returns COMPLETE. Detecting a
     * wrong atom order requires the geometry-identity chain PLUS upstream execution
     * provenance tying the matrix row order to atom_order; persistence-time checks alone
     * cannot see it.
     */
    @Test
    void documentsAtomOrderPermutationUndetectableAtPersistenceGate() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        int[] permutation = {3, 4, 5, 0, 1, 2};
        bundle.electronic = permute(bundle.electronic, permutation);
        bundle.dispersion = permute(bundle.dispersion, permutation);
        bundle.total = permute(bundle.total, permutation);
        rewriteHessianMatrices(bundle);
        assertThat(validator.validate(bundleRoot, bundle.manifest()).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    /**
     * ADV-COMP-C2 characterization. The method/protocol truth of a component is
     * producer-asserted text: here the electronic matrix is arbitrary (physically
     * meaningless) numbers, yet the component identity declares TRUSTED_PBE_ONLY_HESSIAN
     * and the gate returns COMPLETE because every check it performs is a consistency
     * check, not an execution-truth check. This is exactly why the general invariant
     * needs executed-component provenance (backend attestation that a given component was
     * actually evaluated), not merely self-declared identity strings.
     */
    @Test
    void documentsComponentMethodIdentityIsSelfDeclared() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        double[][] arbitrary = {
                {9.1, 7.7, -5.5, 3.3, -1.1, 0.7},
                {7.7, -8.8, 6.6, -4.4, 2.2, -0.3},
                {-5.5, 6.6, 7.9, -5.7, 3.5, -1.3},
                {3.3, -4.4, -5.7, 6.8, -7.6, 5.4},
                {-1.1, 2.2, 3.5, -7.6, -4.6, 8.2},
                {0.7, -0.3, -1.3, 5.4, 8.2, 9.9}};
        bundle.electronic = arbitrary;
        bundle.total = add(bundle.electronic, bundle.dispersion);
        rewriteHessianMatrices(bundle);
        assertThat(validator.validate(bundleRoot, bundle.manifest()).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    /**
     * ADV-COMP-C3 characterization. A B3LYP-D3(BJ) composite bundle with honest, internally
     * consistent non-PBE identity strings is rejected today because the component-identity
     * vocabulary is hard-coded to TRUSTED_PBE_ONLY_HESSIAN. That is fail-closed (safe), but
     * the vocabulary is TSL-specific: generalizing to other functionals requires a
     * component-identity registry rather than a literal string.
     */
    @Test
    void documentsNonPbeCompositeRejectedFailClosed() throws Exception {
        QmBundle bundle = completeCompositeBundle();
        bundle.writeArtifact("method", "B3LYP-D3(BJ)/def2-SVP\n");
        String identity = "electronic_identity=TRUSTED_B3LYP_ONLY_HESSIAN\n"
                + "electronic_geometry_sha256=" + bundle.geometrySha + "\n"
                + "dispersion_identity=D3_BJ_ONLY_HESSIAN\n"
                + "dispersion_geometry_sha256=" + bundle.geometrySha + "\n"
                + "total_identity=B3LYP_D3_BJ_TOTAL_HESSIAN\n"
                + "total_geometry_sha256=" + bundle.geometrySha + "\n";
        bundle.writeArtifact("hessian_component_identity", identity);
        assertRejected(bundle.manifest())
                .withIssueContaining("TRUSTED_PBE_ONLY_HESSIAN");
    }

    // ------------------------------------------------------------------ bundle harness

    /** Synthetic 2-atom H/Cl QM bundle living in {@link #bundleRoot}. */
    private final class QmBundle {
        final Map<String, ScientificArtifactReference> artifacts = new LinkedHashMap<>();
        double[][] electronic = electronicTemplate();
        double[][] dispersion = dispersionTemplate();
        double[][] total;
        double[][] totalGradient;
        double[][] force;
        String geometrySha;
        String units = "hartree/bohr^2\n";

        void writeCommonScalars(String method, String dispersionConfiguration) throws Exception {
            writeArtifact("software_versions", "pyscf 2.14.0; simple-dftd3 1.5.0; geomeTRIC 1.1.1\n");
            writeArtifact("code_commit", "0123456789abcdef0123456789abcdef01234567\n");
            writeArtifact("input_checksums", "input.json=" + sha256("synthetic input\n") + "\n");
            writeArtifact("output_checksums", "result.json=" + sha256("synthetic output\n") + "\n");
            writeArtifact("geometry.xyz",
                    "2\nHCl audit geometry (bohr)\nH 0.0 0.0 0.0\nCl 1.3 0.2 0.4\n");
            writeArtifact("atom_order", "H\nCl\n");
            writeArtifact("charge", "0\n");
            writeArtifact("multiplicity", "1\n");
            writeArtifact("method", method);
            writeArtifact("basis", "def2-SVP\n");
            writeArtifact("grid", "SG-1 (99 radial, 590 angular)\n");
            writeArtifact("dispersion_configuration", dispersionConfiguration);
            writeArtifact("scf_configuration", "conv_tol=1e-10 max_cycle=128 diis=adiis\n");
            writeArtifact("electronic_energy", "-76.0\n");
            writeArtifact("dispersion_energy", "-0.02\n");
            writeArtifact("total_energy", "-76.02\n");
            double[][] electronicGradient = {
                    {1.0e-3, -2.0e-3, 3.0e-3},
                    {-1.0e-3, 2.0e-3, -3.0e-3}};
            double[][] dispersionGradient = {
                    {1.0e-5, -2.0e-5, 3.0e-5},
                    {-1.0e-5, 2.0e-5, -3.0e-5}};
            totalGradient = add(electronicGradient, dispersionGradient);
            force = negate(totalGradient);
            writeArtifact("electronic_gradient", gradientText(electronicGradient));
            writeArtifact("dispersion_gradient", gradientText(dispersionGradient));
            writeArtifact("total_gradient", gradientText(totalGradient));
            writeArtifact("force", gradientText(force));
            writeArtifact("convergence_diagnostics",
                    "scf_converged=true rms_density=3.1e-11 max_gradient=4.4e-06\n");
            writeArtifact("hardware_runtime_identity", "audit-cpu; 8 threads; no GPU\n");
            geometrySha = sha256(bundleRoot.resolve("geometry.xyz"));
        }

        void writeHessianRequested(String content) throws Exception {
            writeArtifact("hessian_requested", content);
        }

        void writeArtifact(String name, String content) throws Exception {
            Files.writeString(bundleRoot.resolve(name), content);
            artifacts.put(name, new ScientificArtifactReference(
                    Path.of(name), sha256(bundleRoot.resolve(name))));
        }

        void remove(String name) {
            artifacts.remove(name);
        }

        ScientificResultManifest manifest() {
            return new ScientificResultManifest(
                    "adv-composite-audit", ScientificResultType.QM_CALCULATION, artifacts);
        }
    }

    private static String componentIdentity(String geometrySha, boolean composite) {
        StringBuilder text = new StringBuilder()
                .append("electronic_identity=TRUSTED_PBE_ONLY_HESSIAN\n")
                .append("electronic_geometry_sha256=").append(geometrySha).append('\n');
        if (composite) {
            text.append("dispersion_identity=D3_BJ_ONLY_HESSIAN\n")
                    .append("dispersion_geometry_sha256=").append(geometrySha).append('\n')
                    .append("total_identity=PBE_D3_BJ_TOTAL_HESSIAN\n")
                    .append("total_geometry_sha256=").append(geometrySha).append('\n');
        }
        return text.toString();
    }

    /** Asymmetric, non-accidental symmetric electronic Hessian (hartree/bohr^2 scale). */
    private static double[][] electronicTemplate() {
        return symmetric(new double[]{0.512, 0.487, 0.623, 0.398, 0.441, 0.556},
                new double[][]{
                        {0.0, 0.045, -0.032, 0.021, 0.017, -0.028},
                        {0.0, 0.0, 0.039, -0.024, 0.031, 0.014},
                        {0.0, 0.0, 0.0, 0.027, -0.019, 0.035},
                        {0.0, 0.0, 0.0, 0.0, 0.022, -0.026},
                        {0.0, 0.0, 0.0, 0.0, 0.0, 0.029},
                        {0.0, 0.0, 0.0, 0.0, 0.0, 0.0}});
    }

    /** Asymmetric, non-accidental symmetric dispersion Hessian, distinctly smaller scale. */
    private static double[][] dispersionTemplate() {
        return symmetric(new double[]{-1.2e-4, -9.5e-5, -1.5e-4, -8.7e-5, -1.1e-4, -1.3e-4},
                new double[][]{
                        {0.0, -3.1e-4, 2.2e-4, -1.8e-4, 1.4e-4, -2.6e-4},
                        {0.0, 0.0, -2.4e-4, 1.9e-4, -1.6e-4, 2.1e-4},
                        {0.0, 0.0, 0.0, -2.8e-4, 1.7e-4, -2.3e-4},
                        {0.0, 0.0, 0.0, 0.0, -1.5e-4, 2.5e-4},
                        {0.0, 0.0, 0.0, 0.0, 0.0, -2.0e-4},
                        {0.0, 0.0, 0.0, 0.0, 0.0, 0.0}});
    }

    private static double[][] symmetric(double[] diagonal, double[][] upper) {
        int n = diagonal.length;
        double[][] matrix = new double[n][n];
        for (int row = 0; row < n; row++) {
            matrix[row][row] = diagonal[row];
            for (int column = row + 1; column < n; column++) {
                matrix[row][column] = upper[row][column];
                matrix[column][row] = upper[row][column];
            }
        }
        return matrix;
    }

    private static double[][] add(double[][] first, double[][] second) {
        double[][] sum = new double[first.length][first[0].length];
        for (int row = 0; row < first.length; row++) {
            for (int column = 0; column < first[row].length; column++) {
                sum[row][column] = first[row][column] + second[row][column];
            }
        }
        return sum;
    }

    private static double[][] negate(double[][] matrix) {
        double[][] negated = new double[matrix.length][matrix[0].length];
        for (int row = 0; row < matrix.length; row++) {
            for (int column = 0; column < matrix[row].length; column++) {
                negated[row][column] = -matrix[row][column];
            }
        }
        return negated;
    }

    private static double[][] permute(double[][] matrix, int[] permutation) {
        int n = permutation.length;
        double[][] permuted = new double[n][n];
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                permuted[row][column] = matrix[permutation[row]][permutation[column]];
            }
        }
        return permuted;
    }

    private static String matrixText(double[][] matrix) {
        StringBuilder text = new StringBuilder();
        for (double[] row : matrix) {
            for (int column = 0; column < row.length; column++) {
                if (column > 0) {
                    text.append(' ');
                }
                text.append(Double.toString(row[column]));
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static String gradientText(double[][] gradient) {
        return matrixText(gradient);
    }

    /** Independent SHA-256 of a file; deliberately shares no code with the validator. */
    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[4096];
                for (int count; (count = input.read(buffer)) >= 0;) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
