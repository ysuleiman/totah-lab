package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.recovery.ArtifactChecksums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adversarial acceptance tests for QM truth generation — Layer A (A1, A2, A3,
 * A5, A10, A11) and D2 of docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md. Oracles are
 * external or hand-computed; all fixtures are fabricated text artifacts and no
 * QM is executed. A4 lives in execution.quantum (QuantumResult seam), A6 in
 * identity, A7/A8/A9 in planning.
 */
class AdversarialQmTruthAcceptanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROTOCOL_SHA256 =
            "f5074b2774fb757201d9a43eba4f63d4a5f33d6cc72d420fadd1919be9ede396";

    /**
     * TEST_ID: A1 — an evidence record's energy must be a value the QM backend
     * actually produced. Absent is not zero: a missing energy_hartree must be
     * rejected naming the field, never silently registered as 0.0 hartree.
     */
    @Test
    void a1_missingEnergyHartreeIsRejected(@TempDir Path dir) throws Exception {
        JsonNode withoutEnergy = MAPPER.readTree("""
                {"scf_converged": true, "gradient_hartree_per_bohr": [[0.13, -0.27, 0.41]]}
                """);
        assertThatThrownBy(() -> ForceCampaignPreflightRunner.requiredFiniteDouble(withoutEnergy, "energy_hartree"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("energy_hartree");

        // Public reader path: a full artifact tree missing energy_hartree must not parse.
        Path result = writeReaderFixture(dir.resolve("missing"), "");
        assertThatThrownBy(() -> new PyscfEnergyGradientResultReader().read(result))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("energy_hartree");

        // Control: the same fixture with a real energy parses and returns it.
        Path ok = writeReaderFixture(dir.resolve("ok"), "\"energy_hartree\": -76.4008431,");
        assertThat(new PyscfEnergyGradientResultReader().read(ok).energyHartree())
                .isEqualTo(-76.4008431);
    }

    /**
     * TEST_ID: A2 — unparseable is not zero: "energy_hartree": "N/A" must be
     * rejected naming the field, not coerced to 0.0 via asDouble().
     */
    @Test
    void a2_nonNumericEnergyHartreeIsRejected(@TempDir Path dir) throws Exception {
        JsonNode textual = MAPPER.readTree("""
                {"energy_hartree": "N/A"}
                """);
        assertThatThrownBy(() -> ForceCampaignPreflightRunner.requiredFiniteDouble(textual, "energy_hartree"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("energy_hartree");

        Path result = writeReaderFixture(dir.resolve("textual"), "\"energy_hartree\": \"N/A\",");
        assertThatThrownBy(() -> new PyscfEnergyGradientResultReader().read(result))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("energy_hartree");
    }

    /**
     * TEST_ID: A3 — registered energies/gradients are finite real numbers.
     * 1e400 parses to Infinity, so it exercises the finiteness gate; the
     * gradient artifact is attacked at the first, middle and last component.
     */
    @Test
    void a3_nonFiniteEnergyIsRejected(@TempDir Path dir) throws Exception {
        JsonNode infinite = MAPPER.readTree("""
                {"energy_hartree": 1e400}
                """);
        assertThatThrownBy(() -> ForceCampaignPreflightRunner.requiredFiniteDouble(infinite, "energy_hartree"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("energy_hartree");

        Path result = writeReaderFixture(dir.resolve("infinite"), "\"energy_hartree\": 1e400,");
        assertThatThrownBy(() -> new PyscfEnergyGradientResultReader().read(result))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("energy_hartree");
    }

    /**
     * TEST_ID: A3 — a non-finite gradient token at the first, middle or last
     * component must be rejected through the public reader path; position
     * carries no information.
     */
    @Test
    void a3_nonFiniteGradientComponentIsRejectedAtAnyPosition(@TempDir Path dir) throws Exception {
        String[] variants = {
                "[[1e400, -0.27, 0.41], [-0.11, 0.29, -0.37]]",   // first component
                "[[0.13, -0.27, 0.41], [-0.11, 1e400, -0.37]]",   // middle component
                "[[0.13, -0.27, 0.41], [-0.11, 0.29, 1e400]]"};   // last component
        for (int i = 0; i < variants.length; i++) {
            Path result = writeGradientArtifact(dir.resolve("case-" + i), variants[i]);
            assertThatThrownBy(() -> new PyscfEnergyGradientResultReader().read(result))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("non-finite");
        }
    }

    /**
     * TEST_ID: A5 — a gradient labeled hartree/bohr must be consistent with
     * energy differences under bohr displacements: g = ΔE/2h. No such FD check
     * exists in production; this test pins the oracle and the unit contract it
     * depends on. Hand-computed 1-coordinate model E(r) = E0 + g·r.
     */
    @Test
    void a5_bohrGradientMatchesFiniteDifferencesAndAngstromImpostorIsRejected() {
        double g = 0.173;        // hartree/bohr, hand-chosen
        double r = 2.5;          // bohr
        double h = 1.0e-3;       // bohr
        double e0 = -76.0;       // hartree
        double ePlus = e0 + g * (r + h);
        double eMinus = e0 + g * (r - h);
        double centralDifference = (ePlus - eMinus) / (2.0 * h);
        double tolerance = 1.0e-6 * Math.abs(g);

        // the bohr-unit gradient is accepted by the FD oracle
        assertThat(Math.abs(centralDifference - g)).isLessThanOrEqualTo(tolerance);

        // the Å impostor (same numbers, hartree/Å convention) is ~89% off and rejected
        double angstromImpostor = g * LengthUnit.ANGSTROM.toBohr(1.0);
        double impostorRelativeError = Math.abs(centralDifference - angstromImpostor) / Math.abs(g);
        assertThat(impostorRelativeError).isGreaterThan(0.8);
        assertThat(Math.abs(centralDifference - angstromImpostor)).isGreaterThan(tolerance);

        // the unit contract this oracle depends on
        assertThat(LengthUnit.ANGSTROM.toBohr(1.0)).isEqualTo(1.8897261254578281);
        assertThat(LengthUnit.BOHR.toBohr(1.0)).isEqualTo(1.0);
    }

    /**
     * TEST_ID: A10 — every SHA256SUMS entry names a file that exists and
     * matches. Absence is tampering, not innocence: deleting a manifest-listed
     * file that is never otherwise opened must fail verification.
     */
    @Test
    void a10_deletedFrozenInputFailsVerification(@TempDir Path cloud) throws Exception {
        writeFrozenCloud(cloud);
        TslRshForceCloudQmRunner.verifyFrozenInputs(cloud);   // intact tree passes

        Files.delete(cloud.resolve("geometries/S007.xyz"));

        assertThatThrownBy(() -> TslRshForceCloudQmRunner.verifyFrozenInputs(cloud))
                .isInstanceOf(IOException.class);
    }

    /**
     * TEST_ID: A11 — snapshot identity is content, not label: S007 with one
     * coordinate changed by 0.001 bohr is a different snapshot and must fail
     * the checksum gate.
     */
    @Test
    void a11_changedGeometryUnderSameIdentifierFailsVerification(@TempDir Path cloud) throws Exception {
        writeFrozenCloud(cloud);
        TslRshForceCloudQmRunner.verifyFrozenInputs(cloud);   // intact tree passes

        Path geometry = cloud.resolve("geometries/S007.xyz");
        Files.writeString(geometry, Files.readString(geometry).replace("1.100", "1.101"));

        assertThatThrownBy(() -> TslRshForceCloudQmRunner.verifyFrozenInputs(cloud))
                .isInstanceOf(IOException.class);
    }

    /**
     * TEST_ID: D2 (a) — a holdout that cannot be evaluated is a failed gate,
     * never a vacuous pass: deleting TRAIN_HOLDOUT_SPLIT.csv must fail.
     */
    @Test
    void d2_deletedSplitManifestFailsVerification(@TempDir Path cloud) throws Exception {
        writeFrozenCloud(cloud);
        TslRshForceCloudQmRunner.verifyFrozenInputs(cloud);   // intact tree passes

        Files.delete(cloud.resolve("TRAIN_HOLDOUT_SPLIT.csv"));

        assertThatThrownBy(() -> TslRshForceCloudQmRunner.verifyFrozenInputs(cloud))
                .isInstanceOf(IOException.class);
    }

    /**
     * TEST_ID: D2 (b) — the split file is present but emptied; SHA256SUMS is
     * re-sealed over the empty file so ONLY the holdout seal (which still binds
     * the original split checksum) can catch the removal. Absence of holdout
     * content is a failure, never a pass.
     */
    @Test
    void d2_emptiedHoldoutSplitFailsSealVerification(@TempDir Path cloud) throws Exception {
        writeFrozenCloud(cloud);
        TslRshForceCloudQmRunner.verifyFrozenInputs(cloud);   // intact tree passes

        Files.writeString(cloud.resolve("TRAIN_HOLDOUT_SPLIT.csv"), "");
        writeSums(cloud);   // checksum gate now passes over the empty file

        assertThatThrownBy(() -> TslRshForceCloudQmRunner.verifyFrozenInputs(cloud))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("holdout seal");
    }

    /**
     * Minimal force-cloud tree satisfying verifyFrozenInputs: SHA256SUMS over
     * FORCE_CLOUD_STATUS.json (60/45/15, frozen protocol), HOLDOUT_SEAL.json
     * (holdout_count 15, seal bound to the split checksum), TRAIN_HOLDOUT_SPLIT.csv
     * and one geometry file that is never otherwise opened.
     */
    private static void writeFrozenCloud(Path cloud) throws IOException {
        Files.createDirectories(cloud.resolve("geometries"));
        Files.writeString(cloud.resolve("geometries/S007.xyz"),
                "3\nsnapshot S007\nC 0.000 0.000 0.000\nN 1.100 0.000 0.000\nH 2.200 0.100 0.000\n");
        Path split = Files.writeString(cloud.resolve("TRAIN_HOLDOUT_SPLIT.csv"),
                "snapshot_id,split\nS001,TRAIN\nS007,TRAIN\nS042,HOLDOUT\n");
        Files.writeString(cloud.resolve("FORCE_CLOUD_STATUS.json"), """
                {
                  "retained_count": 60,
                  "training_count": 45,
                  "holdout_count": 15,
                  "protocol_sha256": "%s"
                }
                """.formatted(PROTOCOL_SHA256));
        Files.writeString(cloud.resolve("HOLDOUT_SEAL.json"), """
                {
                  "holdout_count": 15,
                  "split_manifest_sha256": "%s"
                }
                """.formatted(ArtifactChecksums.sha256(split)));
        writeSums(cloud);
    }

    private static void writeSums(Path cloud) throws IOException {
        StringBuilder sums = new StringBuilder();
        for (String name : List.of("FORCE_CLOUD_STATUS.json", "HOLDOUT_SEAL.json",
                "TRAIN_HOLDOUT_SPLIT.csv", "geometries/S007.xyz")) {
            sums.append(ArtifactChecksums.sha256(cloud.resolve(name)))
                    .append("  ").append(name).append('\n');
        }
        Files.writeString(cloud.resolve("SHA256SUMS"), sums.toString());
    }

    /**
     * Full PyscfEnergyGradientResultReader fixture with the spec's A4 gradient
     * (no zero components, not antisymmetric); {@code energyField} is the raw
     * JSON fragment for the energy_hartree field (empty string omits it).
     */
    private static Path writeReaderFixture(Path dir, String energyField) throws IOException {
        Files.createDirectories(dir);
        Path geometry = Files.writeString(dir.resolve("input_geometry.xyz"),
                "2\nadversarial fixture\nC 0.0 0.0 0.0\nN 1.0 0.0 0.0\n");
        Path specification = Files.writeString(dir.resolve("calculation_specification.json"),
                "{\"specification_id\": \"adversarial-1\"}");
        double norm = Math.sqrt(0.13 * 0.13 + 0.27 * 0.27 + 0.41 * 0.41
                + 0.11 * 0.11 + 0.29 * 0.29 + 0.37 * 0.37);
        String json = """
                {
                  "specification_checksum": "adversarial-1",
                  "geometry_identity": "adversarial-geometry",
                  %s
                  "gradient_hartree_per_bohr": [[0.13, -0.27, 0.41], [-0.11, 0.29, -0.37]],
                  "force_hartree_per_bohr": [[-0.13, 0.27, -0.41], [0.11, -0.29, 0.37]],
                  "gradient_norm_hartree_per_bohr": %s,
                  "input_geometry_sha256": "%s",
                  "calculation_specification_sha256": "%s",
                  "finite_difference_audit": {
                    "plus_energy_hartree": -76.0,
                    "minus_energy_hartree": -76.0,
                    "central_difference_hartree_per_bohr": 0.0,
                    "analytic_gradient_projection_hartree_per_bohr": 0.0,
                    "absolute_difference_hartree_per_bohr": 0.0
                  },
                  "scf_converged": true,
                  "software": {"pyscf": "2.14.0", "dftd3": "1.5.0"}
                }
                """.formatted(energyField, Double.toString(norm),
                ArtifactChecksums.sha256(geometry), ArtifactChecksums.sha256(specification));
        return Files.writeString(dir.resolve("result.json"), json);
    }

    /** Minimal result.json whose gradient matrix is rejected before any other check. */
    private static Path writeGradientArtifact(Path dir, String gradientJson) throws IOException {
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve("result.json"), """
                {
                  "gradient_hartree_per_bohr": %s,
                  "force_hartree_per_bohr": [[-0.13, 0.27, -0.41], [0.11, -0.29, 0.37]]
                }
                """.formatted(gradientJson));
    }
}
