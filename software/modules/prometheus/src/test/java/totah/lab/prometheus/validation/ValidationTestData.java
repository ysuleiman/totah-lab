package totah.lab.prometheus.validation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import totah.lab.prometheus.candidate.DerivedParameter;
import totah.lab.prometheus.candidate.EvidenceClass;
import totah.lab.prometheus.candidate.ParameterCandidate;
import totah.lab.prometheus.candidate.ParameterKind;
import totah.lab.prometheus.candidate.ParameterProvenance;
import totah.lab.prometheus.candidate.ValidationStatus;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.identity.GeometryIdentity;

/** Shared builders for the validation-package tests. */
final class ValidationTestData {

    static final Instant T0 = Instant.parse("2026-08-14T00:00:00Z");

    private ValidationTestData() {
    }

    static DerivedParameter angleParam(double value) {
        return new DerivedParameter(
                "tsl-angle-9-10-26",
                TslFixtures.TSL,
                List.of(9, 10, 26),
                ParameterKind.ANGLE_BEND,
                "harmonic",
                value,
                "kcal/mol/rad^2",
                new ParameterProvenance(
                        "modified-Seminario",
                        List.of("abc123"),
                        "dev-1",
                        "prometheus-0.1",
                        "none",
                        "line-1",
                        ValidationStatus.UNVALIDATED));
    }

    static ParameterCandidate candidate(String candidateId, double angleValue) {
        return new ParameterCandidate(
                candidateId,
                TslFixtures.TSL,
                TslFixtures.forceFieldMapGaff2(),
                List.of(angleParam(angleValue)),
                null,
                0,
                EvidenceClass.EVIDENCE,
                T0);
    }

    static ValidationGate rmseGate() {
        return new ValidationGate(
                "gate-rmse", "holdout RMSE must stay within 1 kcal/mol",
                "rmse_kcal_mol", 1.0, Comparison.AT_MOST);
    }

    static ValidationPlan plan(String holdoutDatasetId) {
        return ValidationPlan.preregister("plan-tsl-1", List.of(rmseGate()), holdoutDatasetId);
    }

    /** ACCEPTED, CONVERGED quantum evidence; distinct (type, geometry) pairs give distinct hashes. */
    static QuantumEvidence accepted(CalculationType type, GeometryIdentity geometry) {
        return EvidenceFixtures.acceptedQuantum(
                EvidenceFixtures.identity(type, EvidenceFixtures.PBE_DEF2_SVP, geometry),
                -100.0);
    }

    /** Quantum evidence with explicit convergence/acceptance states. */
    static QuantumEvidence withStates(
            ConvergenceStatus convergence,
            EvidenceAcceptanceState acceptance,
            GeometryIdentity geometry) {

        return new QuantumEvidence(
                EvidenceFixtures.identity(
                        CalculationType.SINGLE_POINT, EvidenceFixtures.PBE_DEF2_SVP, geometry),
                EvidenceFixtures.provenance("/archive/tsl/evidence.log"),
                convergence,
                acceptance,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "test evidence");
    }
}
