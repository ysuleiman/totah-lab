package totah.lab.prometheus.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.candidate.EvidenceClass;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.ingest.LegacyPhase2ArchiveIngester.IngestionResult;

/**
 * End-to-end ingestion over a small synthetic archive mirroring the real
 * mettl7-phase2 layout. All classification strings live in the fixture
 * artifacts and are parsed, never asserted into existence.
 */
class LegacyPhase2ArchiveIngesterTest {

    private static final String MIN01_GEO_SHA =
            "36331712f69fa6a0f1ded7eb5df44f7dae7bdd0927507dd21eb7218be2320bdd";
    private static final String MIN02_GEO_SHA =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PROBE1_GEO_SHA =
            "7482b18629b07cfeb827fd8b1e4bad3fe09f070a79e32dbfe8365b07bd540fa6";
    private static final String PROBE4_GEO_SHA =
            "f64479ab3dcb8782b2282a849a0f8eff1a15d9f115ed09cd2cfe0c38baa4876f";
    private static final String REPLACEMENT_GEO_SHA =
            "b81f4a01cd6942588819b682f784691a045448267acefd02d976c92dd507300f";
    private static final String INVENTORY_GEO_SHA =
            "0130733926cc563efd106832dba61893a63482dc9bea1559a03ac3127bc09dec";

    @TempDir
    Path archiveRoot;

    private Path unit05O;

    @BeforeEach
    void buildSyntheticArchive() throws IOException {
        unit05O = Files.createDirectories(archiveRoot.resolve("execution-unit-05O"));
        writeCanonicalInventory();
        writeMinimum("MIN01", MIN01_GEO_SHA, false);
        writeMinimum("MIN02", MIN02_GEO_SHA, true);
        writeHessian();
        writeElectrostatics();
        writeRespModel();
        writeFormatRejectedResp();
        writeProbePoints();
        writeReplacementShDonor();
        writeGeometryInventory();
        writeFailedBranch();
        writeAngleCrossReport();
        // tampered checksum entry: the hessian result.json no longer matches
        Files.writeString(unit05O.resolve("SHA256SUMS"),
                "0000000000000000000000000000000000000000000000000000000000000000"
                        + "  hessians/MIN01/result.json\n");
    }

    private IngestionResult ingest() throws IOException {
        return new LegacyPhase2ArchiveIngester().ingest(archiveRoot);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void recoversFullBundleShape() throws Exception {
        IngestionResult result = ingest();

        // quantum: MIN01 opt, MIN02 opt, hessian, electrostatics, RESP accepted,
        // RESP format-rejected, 2 probes, 1 replacement CP, 1 inventory row
        assertThat(result.bundle().quantum()).hasSize(10);
        // classical: 2 probe decompositions + 1 six-point master-table row
        assertThat(result.bundle().classical()).hasSize(3);
        assertThat(result.bundle().size()).isEqualTo(13);
    }

    @Test
    void threeMinimaShapeAndEmptyLogDefect() throws Exception {
        IngestionResult result = ingest();

        List<QuantumEvidence> optimizations = result.bundle().byType(CalculationType.OPTIMIZATION);
        assertThat(optimizations).hasSize(2);
        assertThat(optimizations).allSatisfy(e -> {
            assertThat(e.acceptance()).isEqualTo(EvidenceAcceptanceState.ACCEPTED);
            assertThat(e.convergence()).isEqualTo(ConvergenceStatus.CONVERGED);
            assertThat(e.identity().protocol().method()).isEqualTo("PBE");
            assertThat(e.identity().protocol().basis()).isEqualTo("def2-SVP");
            assertThat(e.identity().protocol().dispersion()).isEqualTo("D3(BJ)");
            assertThat(e.identity().protocol().softwareVersion()).isEqualTo("2.14.0");
            assertThat(e.energyHartree()).isPresent();
        });
        // MIN02's empty raw_combined.log is a note-level issue, not a failure
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.severity()).isEqualTo("note");
            assertThat(issue.path()).contains("MIN02");
            assertThat(issue.message()).contains("raw_combined.log is empty");
        });
        QuantumEvidence min02 = optimizations.stream()
                .filter(e -> e.provenance().note().contains("minimum_id=MIN02"))
                .findFirst().orElseThrow();
        assertThat(min02.acceptance()).isEqualTo(EvidenceAcceptanceState.ACCEPTED);
    }

    @Test
    void hessianIsRecoveredAndTamperedChecksumMarksItInvalid() throws Exception {
        IngestionResult result = ingest();

        List<QuantumEvidence> hessians = result.bundle().byType(CalculationType.HESSIAN);
        assertThat(hessians).hasSize(1);
        QuantumEvidence hessian = hessians.get(0);
        assertThat(hessian.hessianHartreePerBohr2()).isPresent();
        assertThat(hessian.hessianHartreePerBohr2().orElseThrow()).hasSize(9);
        // the tampered SHA256SUMS entry drives CHECKSUM_INVALID plus an error issue
        assertThat(hessian.acceptance()).isEqualTo(EvidenceAcceptanceState.CHECKSUM_INVALID);
        assertThat(hessian.provenance().note()).contains("VERIFIED_LOCAL_MINIMUM");
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.severity()).isEqualTo("error");
            assertThat(issue.path()).contains("hessians/MIN01/result.json");
            assertThat(issue.message()).contains("checksum mismatch");
        });
    }

    @Test
    void electrostaticDiagnosticCarriesParsedDipole() throws Exception {
        IngestionResult result = ingest();

        List<QuantumEvidence> singlePoints = result.bundle().byType(CalculationType.SINGLE_POINT);
        assertThat(singlePoints).hasSize(1);
        QuantumEvidence esp = singlePoints.get(0);
        assertThat(esp.acceptance()).isEqualTo(EvidenceAcceptanceState.ACCEPTED);
        assertThat(esp.dipoleDebye()).isPresent();
        assertThat(esp.dipoleDebye().orElseThrow())
                .containsExactly(-1.0604089667455414, -0.09957902591355972, 1.1242951624670476);
    }

    @Test
    void respModelAcceptedWithParsedChargesAndRejectedTwinKeptNegative() throws Exception {
        IngestionResult result = ingest();

        List<QuantumEvidence> resp = result.bundle().byType(CalculationType.RESP);
        assertThat(resp).hasSize(2);

        QuantumEvidence accepted = resp.stream()
                .filter(e -> e.acceptance() == EvidenceAcceptanceState.ACCEPTED)
                .findFirst().orElseThrow();
        // S26/H56 charges are parsed from the charges CSV, not asserted
        assertThat(accepted.provenance().note()).contains("S26(atom_id=26)=-0.290064");
        assertThat(accepted.provenance().note()).contains("H56(atom_id=56)=0.172272");
        assertThat(accepted.provenance().note()).contains("ELECTROSTATIC_MODEL_ACCEPTED_FOR_VDW_STAGE");
        assertThat(accepted.identity().protocol().method()).isEqualTo("HF");
        assertThat(accepted.identity().protocol().basis()).isEqualTo("6-31G(d)");

        QuantumEvidence rejected = resp.stream()
                .filter(e -> e.acceptance() == EvidenceAcceptanceState.PROTOCOL_INCOMPLETE)
                .findFirst().orElseThrow();
        assertThat(rejected.provenance().note()).contains("invalid input format");
        assertThat(result.issues()).anySatisfy(issue ->
                assertThat(issue.path()).contains("format-rejected"));
    }

    @Test
    void geometryAuditExcludesDesignFailureProbes() throws Exception {
        IngestionResult result = ingest();

        List<QuantumEvidence> probes = result.bundle().byType(CalculationType.COUNTERPOISE_INTERACTION);
        // 2 vdw probes + 1 replacement run
        assertThat(probes).hasSize(3);

        QuantumEvidence excluded = probes.stream()
                .filter(e -> e.provenance().note().contains("04_MIN01_SH_DONOR_1.7"))
                .findFirst().orElseThrow();
        assertThat(excluded.acceptance()).isEqualTo(EvidenceAcceptanceState.GEOMETRY_INVALID);
        assertThat(excluded.provenance().note()).contains("PROBE_DESIGN_SCAFFOLD_COLLISION");

        QuantumEvidence included = probes.stream()
                .filter(e -> e.provenance().note().contains("01_MIN01_S_ACCEPTOR_2.2"))
                .findFirst().orElseThrow();
        assertThat(included.acceptance()).isEqualTo(EvidenceAcceptanceState.ACCEPTED);
        assertThat(included.interactionEnergyKcalMol()).hasValue(0.4332268549907766);
        assertThat(included.identity().protocol().method()).isEqualTo("PBE0");
        assertThat(included.identity().protocol().basis()).isEqualTo("def2-TZVP");
        assertThat(included.identity().protocol().counterpoise()).isTrue();
    }

    @Test
    void classicalDecompositionFollowsProbeAcceptance() throws Exception {
        IngestionResult result = ingest();

        List<ClassicalEvidence> classical = result.bundle().classical();
        assertThat(classical).hasSize(3);
        assertThat(classical).allSatisfy(e -> assertThat(e.forceFieldId()).isEqualTo("GAFF2"));

        ClassicalEvidence excluded = classical.stream()
                .filter(e -> e.provenance().note().contains("04_MIN01_SH_DONOR_1.7"))
                .findFirst().orElseThrow();
        assertThat(excluded.acceptance()).isEqualTo(EvidenceAcceptanceState.GEOMETRY_INVALID);
        assertThat(excluded.decomposition().electrostaticKcalMol()).isEqualTo(-4.682074956921946);

        ClassicalEvidence included = classical.stream()
                .filter(e -> e.provenance().note().contains("01_MIN01_S_ACCEPTOR_2.2"))
                .findFirst().orElseThrow();
        assertThat(included.acceptance()).isEqualTo(EvidenceAcceptanceState.ACCEPTED);
        assertThat(included.decomposition().totalKcalMol()).isEqualTo(-1.421121884970964);
    }

    @Test
    void geometryInventoryRecoversEarlierCampaignAndDedupsCoveredGeometries() throws Exception {
        IngestionResult result = ingest();

        List<QuantumEvidence> scans = result.bundle().byType(CalculationType.CONSTRAINED_SCAN);
        assertThat(scans).hasSize(1);
        QuantumEvidence recovered = scans.get(0);
        assertThat(recovered.acceptance()).isEqualTo(EvidenceAcceptanceState.ACCEPTED);
        assertThat(recovered.identity().geometry().sha256()).isEqualTo(INVENTORY_GEO_SHA);
        assertThat(recovered.identity().geometry().atomCount()).isEqualTo(3);
        assertThat(recovered.identity().protocol().method()).isEqualTo("PBE");
        assertThat(recovered.identity().protocol().basis()).isEqualTo("3-21G");
        // relative energy is carried in the note, never as an absolute hartree
        assertThat(recovered.energyHartree()).isEmpty();
        assertThat(recovered.provenance().note()).contains("RELATIVE_KCAL_MOL");
        // the second inventory row shares MIN01's geometry sha and must be deduped:
        // exactly one inventory-recovered record exists, and it is the 05D row
        assertThat(result.bundle().quantum().stream()
                .filter(e -> e.provenance().note().contains("recovered via geometry inventory")))
                .hasSize(1);
    }

    @Test
    void failedBranchClassificationsAreParsedNotHardCoded() throws Exception {
        IngestionResult result = ingest();

        assertThat(result.branchOutcomes()).hasSize(3);

        FailedCandidateRecord offcenter = result.branchOutcomes().stream()
                .filter(b -> b.branch().equals("offcenter-thiol-three-parameter"))
                .findFirst().orElseThrow();
        assertThat(offcenter.classification()).isEqualTo("OFFCENTER_FIXED_CHARGE_MODEL_INSUFFICIENT");
        assertThat(offcenter.evidenceClass()).isEqualTo(EvidenceClass.FAILED_CANDIDATE);
        assertThat(offcenter.reportPath()).endsWith("THREE_PARAMETER_DEVELOPMENT_DECISION.json");
        assertThat(offcenter.summary()).contains("three off-center parameters");

        FailedCandidateRecord shDonor = result.branchOutcomes().stream()
                .filter(b -> b.branch().equals("replacement-sh-donor-final-evidence-package"))
                .findFirst().orElseThrow();
        assertThat(shDonor.classification()).isEqualTo("DISTANCE_DEPENDENT_TRANSFERABLE_DISCREPANCY");
        assertThat(shDonor.evidenceClass()).isEqualTo(EvidenceClass.FAILED_CANDIDATE);

        FailedCandidateRecord angleCross = result.branchOutcomes().stream()
                .filter(b -> b.branch().equals("execution-unit-05L-angle-cross"))
                .findFirst().orElseThrow();
        assertThat(angleCross.classification()).isEqualTo("ANGLE_LJ_COUPLED_DEFECT_SUPPORTED");
        assertThat(angleCross.evidenceClass()).isEqualTo(EvidenceClass.VALIDATED_DIAGNOSTIC);
    }

    @Test
    void canonicalAtomsAndProtocolRegistryArePopulated() throws Exception {
        IngestionResult result = ingest();

        assertThat(result.canonicalAtoms().byIndex(10).orElseThrow().label()).isEqualTo("C9");
        assertThat(result.protocolRegistry()).isNotEmpty();
        assertThat(result.protocolRegistry().keySet())
                .anySatisfy(key -> assertThat(key).startsWith("PBE|def2-SVP|D3(BJ)"));
        assertThat(result.protocolRegistry().keySet())
                .anySatisfy(key -> assertThat(key).startsWith("GAFF2|"));
    }

    // ------------------------------------------------------------------
    // Fixture builders
    // ------------------------------------------------------------------

    private void writeCanonicalInventory() throws IOException {
        Path unit02 = Files.createDirectories(archiveRoot.resolve("execution-unit-02"));
        Files.writeString(unit02.resolve("canonical_atom_inventory.csv"), """
                chemical_id,canonical_atom_index,canonical_atom_id,mol2_atom_name,element,formal_charge,chiral_tag,is_explicit_hydrogen,role
                TSL_RSH,9,TSH_C009,C8,C,0,CHI_UNSPECIFIED,false,atom
                TSL_RSH,10,TSH_C010,C9,C,0,CHI_TETRAHEDRAL_CCW,false,atom
                TSL_RSH,11,TSH_C011,C10,C,0,CHI_TETRAHEDRAL_CCW,false,atom
                TSL_RSH,26,TSH_S026,S26,S,0,CHI_UNSPECIFIED,false,reactive_sulfur
                TSL_RSH,56,TSH_H056,H56,H,0,CHI_UNSPECIFIED,true,atom
                """);
    }

    private void writeMinimum(String minimumId, String geometrySha, boolean emptyLog) throws IOException {
        Path dir = Files.createDirectories(unit05O.resolve("qm-native-minima/" + minimumId));
        Files.writeString(dir.resolve("input.json"), """
                {
                  "charge": 0,
                  "multiplicity": 1,
                  "constraints": "NONE",
                  "method": "PBE-D3(BJ)/def2-SVP density-fitted gas phase",
                  "minimum_id": "%s",
                  "software": {"pyscf": "2.14.0", "geometric": "1.1.1"}
                }
                """.formatted(minimumId));
        Files.writeString(dir.resolve("result.json"), """
                {
                  "cycles": 11,
                  "energy_hartree": -1477.9438395697157,
                  "final_xyz_sha256": "%s",
                  "gradient_norm_hartree_per_bohr": 0.0003865126270193723,
                  "scf_converged": true,
                  "status": "CONVERGED_ACCEPTED"
                }
                """.formatted(geometrySha));
        Files.writeString(dir.resolve("final.xyz"), """
                3
                %s unconstrained final
                C   2.0513614816  -0.5984424711   1.7744453029
                S   0.5000000000   0.1000000000   0.2000000000
                H   1.7632263842  -4.3307534074   1.1073983077
                """.formatted(minimumId));
        Files.writeString(dir.resolve("raw_combined.log"), emptyLog ? "" : "log content\n");
    }

    private void writeHessian() throws IOException {
        Path dir = Files.createDirectories(unit05O.resolve("hessians/MIN01"));
        Files.writeString(dir.resolve("input.json"), """
                {
                  "charge": 0,
                  "multiplicity": 1,
                  "method": "PBE-D3(BJ)/def2-SVP density-fitted gas phase analytic Hessian",
                  "minimum_id": "MIN01",
                  "software": {"pyscf": "2.14.0"}
                }
                """);
        Files.writeString(dir.resolve("result.json"), """
                {
                  "energy_hartree": -1477.9438395697284,
                  "frequency_count": 3,
                  "provisional_frequency_classification": "VERIFIED_LOCAL_MINIMUM",
                  "scf_converged": true,
                  "status": "HESSIAN_COMPLETE"
                }
                """);
        StringBuilder flat = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            flat.append(0.01 * (i + 1)).append('\n');
        }
        Files.writeString(dir.resolve("cartesian_hessian_flat_hartree_per_bohr2.txt"), flat.toString());
    }

    private void writeElectrostatics() throws IOException {
        Path dir = Files.createDirectories(unit05O.resolve("electrostatic-diagnostics/MIN01"));
        Files.writeString(dir.resolve("result.json"), """
                {
                  "energy_hartree": -1477.9438395697302,
                  "dipole_debye": [-1.0604089667455414, -0.09957902591355972, 1.1242951624670476],
                  "scf_converged": true,
                  "status": "CONVERGED_DIAGNOSTIC"
                }
                """);
        Files.writeString(dir.resolve("dipole_debye.txt"),
                "-1.060408966746 -0.099579025914 1.124295162467\n");
    }

    private void writeRespModel() throws IOException {
        Files.createDirectories(unit05O.resolve("native-amber-resp3min-hf631gd/regeneration-A/all-three"));
        Files.writeString(unit05O.resolve("TSL_RSH_NATIVE_AMBER_RESP_CHARGES.csv"), """
                atom_id,atom_name,native_resp_charge_e,native_output_precision,canonical_order
                9,C8,-0.287472,6_DECIMAL_NATIVE_QOUT,9
                10,C9,-0.047795,6_DECIMAL_NATIVE_QOUT,10
                11,C10,0.099171,6_DECIMAL_NATIVE_QOUT,11
                26,S26,-0.290064,6_DECIMAL_NATIVE_QOUT,26
                56,H56,0.172272,6_DECIMAL_NATIVE_QOUT,56
                """);
        Files.writeString(unit05O.resolve("NATIVE_AMBER_RESP_DECISION_REPORT.md"), """
                # Native Amber RESP decision report

                ## Decision

                `ELECTROSTATIC_MODEL_ACCEPTED_FOR_VDW_STAGE`

                AmberTools 26 native resp was run twice from clean directories.
                """);
    }

    private void writeFormatRejectedResp() throws IOException {
        Path dir = Files.createDirectories(
                unit05O.resolve("native-amber-resp3min-hf631gd-format-rejected"));
        Files.writeString(dir.resolve("result.json"), """
                {
                  "status": "COMPLETE",
                  "note": "center records omitted the required leading blank field"
                }
                """);
    }

    private void writeProbePoints() throws IOException {
        writeProbePoint("point-1", "01_MIN01_S_ACCEPTOR_2.2", PROBE1_GEO_SHA, 0.4332268549907766);
        writeProbePoint("point-4", "04_MIN01_SH_DONOR_1.7", PROBE4_GEO_SHA, -0.4620302981394552);

        Path diag = Files.createDirectories(unit05O.resolve("final-19-point-force-field-diagnostic"));
        Files.writeString(diag.resolve("ALL_19_PROBE_GEOMETRY_AUDIT.csv"), """
                point_id,probe_interaction_class,geometry_classification,force_field_validation_eligibility,geometry_path,geometry_sha256
                01_MIN01_S_ACCEPTOR_2.2,S_ACCEPTOR,CLOSE_NON_TARGET_CONTACT,ELIGIBLE_WITH_CLOSE_CONTACT_WARNING,vdw-probe-validation-aws/point-1/01_MIN01_S_ACCEPTOR_2.2/geometry.xyz,%s
                04_MIN01_SH_DONOR_1.7,SH_DONOR,PROBE_DESIGN_SCAFFOLD_COLLISION,EXCLUDE_PROBE_DESIGN_FAILURE,vdw-probe-validation-aws/point-4/04_MIN01_SH_DONOR_1.7/geometry.xyz,%s
                """.formatted(PROBE1_GEO_SHA, PROBE4_GEO_SHA));
        Files.writeString(diag.resolve("CLASSICAL_ENERGY_DECOMPOSITION.csv"), """
                point_id,electrostatic,ordinary_LJ_vdW,bond,angle,proper_torsion,improper_torsion,one_four_electrostatic,one_four_vdW,total,cancellation_note
                01_MIN01_S_ACCEPTOR_2.2,-1.6185068584028786,0.19738497343191452,0.0,0.0,0.0,0.0,0.0,0.0,-1.421121884970964,note
                04_MIN01_SH_DONOR_1.7,-4.682074956921946,2.1534481668676317,0.0,0.0,0.0,0.0,0.0,0.0,-2.5286267900543145,note
                """);
    }

    private void writeProbePoint(String pointDir, String pointId, String geometrySha, double qmCp)
            throws IOException {
        Path dir = Files.createDirectories(
                unit05O.resolve("vdw-probe-validation-aws/" + pointDir + "/" + pointId));
        Files.writeString(dir.resolve("geometry.xyz"), """
                4
                %s
                C   2.0513614816  -0.5984424711   1.7744453029
                S   0.5000000000   0.1000000000   0.2000000000
                H   1.7632263842  -4.3307534074   1.1073983077
                O   0.0000000000   0.0000000000   2.2000000000
                """.formatted(pointId));
        Files.writeString(dir.resolve("result.json"), """
                {
                  "point_id": "%s",
                  "minimum_id": "MIN01",
                  "site": "S_ACCEPTOR",
                  "electronic_dimer_hartree": -1555.6778473538784,
                  "qm_cp_pbe0_d3bj_def2tzvp_kcal_mol": %s,
                  "resp_coulomb_kcal_mol": -1.6185068584028786,
                  "gaff2_lj_kcal_mol": 0.19738497343191452,
                  "mm_resp_gaff2_tip3p_kcal_mol": -1.421121884970964,
                  "residual_mm_minus_qm_kcal_mol": -1.8543487399617407,
                  "scf_converged": true,
                  "geometry_sha256": "%s"
                }
                """.formatted(pointId, qmCp, geometrySha));
    }

    private void writeReplacementShDonor() throws IOException {
        Path dir = Files.createDirectories(
                unit05O.resolve("replacement-sh-donor-qm-local/MIN02_SH_DONOR_1.7A"));
        Files.writeString(dir.resolve("geometry.xyz"), """
                4
                MIN02_SH_DONOR_1.7A
                C   2.0513614816  -0.5984424711   1.7744453029
                S   0.5000000000   0.1000000000   0.2000000000
                H   1.7632263842  -4.3307534074   1.1073983077
                O   0.0000000000   0.0000000000   1.7000000000
                """);
        Files.writeString(dir.resolve("result.json"), "{}");
        Files.writeString(unit05O.resolve("replacement-sh-donor-qm-local/RUN_RESULTS.json"), """
                [
                  {
                    "point_id": "MIN02_SH_DONOR_1.7A",
                    "parent_minimum": "MIN02",
                    "method": "CP-PBE0-D3(BJ)/def2-TZVP",
                    "charge": 0,
                    "multiplicity": 1,
                    "constraints": "RIGID_SINGLE_POINT",
                    "status": "COMPLETE_CONVERGED",
                    "electronic_dimer_hartree": -1555.6771800040524,
                    "qm_cp_pbe0_d3bj_def2tzvp_kcal_mol": -0.4620302981394552,
                    "geometry_sha256": "%s"
                  }
                ]
                """.formatted(REPLACEMENT_GEO_SHA));

        Path pkg = Files.createDirectories(
                unit05O.resolve("replacement-sh-donor-final-evidence-package"));
        Files.writeString(pkg.resolve("SIX_POINT_SH_DONOR_MASTER_TABLE.csv"), """
                parent_minimum,target_SH_O_distance_A,geometry_valid_status,qm_interaction_energy_kcal_mol,gaff2_interaction_energy_kcal_mol,electrostatic_interaction_kcal_mol,ordinary_LJ_interaction_kcal_mol,geometry_sha256
                MIN02,1.7,VALID_PROBE_GEOMETRY,-0.4620302981394552,-2.5286267900543145,-4.682074956921946,2.1534481668676317,%s
                """.formatted(REPLACEMENT_GEO_SHA));
        Files.writeString(pkg.resolve("FINAL_SIX_POINT_SH_DONOR_REPORT.md"), """
                # Final six-point S-H donor report

                ## Classification

                `DISTANCE_DEPENDENT_TRANSFERABLE_DISCREPANCY`

                The ordering gates failed.
                """);
    }

    private void writeGeometryInventory() throws IOException {
        Path dftDir = Files.createDirectories(
                archiveRoot.resolve("execution-unit-05D/full-molecule-dft/TSL_RSH"));
        Files.writeString(dftDir.resolve("point-000.xyz"), """
                3
                05D point 0
                C   2.0513614816  -0.5984424711   1.7744453029
                S   0.5000000000   0.1000000000   0.2000000000
                H   1.7632263842  -4.3307534074   1.1073983077
                """);
        Path auditDir = Files.createDirectories(unit05O.resolve("delta-model-data-audit"));
        Files.writeString(auditDir.resolve("TSL_RSH_QM_GEOMETRY_INVENTORY.csv"), """
                artifact_id,execution_unit,geometry_path,geometry_sha256,qm_method_basis,qm_energy_value,qm_energy_kind,constraints,convergence_status,inclusion_exclusion_status,probe_class,corresponding_GAFF2_energy,GAFF2_energy_kind
                05D_RSH_0,05D,analysis/mettl7-phase2/execution-unit-05D/full-molecule-dft/TSL_RSH/point-000.xyz,%s,PBE/3-21G,12.318482,RELATIVE_KCAL_MOL,one constrained torsion; all other coordinates relaxed,CONVERGED,INCLUDE_SEPARATE_LOW_LEVEL_SUBSET,,15.876000,RELATIVE_KCAL_MOL
                05O_MIN01,05O,execution-unit-05O/qm-native-minima/MIN01/final.xyz,%s,PBE-D3(BJ)/def2-SVP,-1477.9438395697157,ABSOLUTE_HARTREE,NONE,CONVERGED,INCLUDE,,,
                """.formatted(INVENTORY_GEO_SHA, MIN01_GEO_SHA));
    }

    private void writeFailedBranch() throws IOException {
        Path dir = Files.createDirectories(unit05O.resolve("offcenter-thiol-three-parameter"));
        Files.writeString(dir.resolve("THREE_PARAMETER_DEVELOPMENT_DECISION.json"), """
                {
                  "classification": "OFFCENTER_FIXED_CHARGE_MODEL_INSUFFICIENT",
                  "summary": "three off-center parameters cannot repair the S-H donor curve"
                }
                """);
    }

    private void writeAngleCrossReport() throws IOException {
        Path unit05L = Files.createDirectories(archiveRoot.resolve("execution-unit-05L"));
        Files.writeString(unit05L.resolve("ANGLE_CROSS_CAUSAL_REPORT.md"), """
                # Sparse two-angle cross causal report

                ## Classification

                **ANGLE_LJ_COUPLED_DEFECT_SUPPORTED**

                TSL-RSH remains METHOD_SENSITIVE_UNRESOLVED until a separately authorized
                parameter strategy is formulated.
                """);
    }
}
