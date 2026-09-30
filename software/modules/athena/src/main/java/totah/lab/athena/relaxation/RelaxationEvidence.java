package totah.lab.athena.relaxation;

import totah.lab.athena.energy.EnergyEvaluation;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Objects;

/** Pre/post evidence is retained even when physical validity fails. */
public record RelaxationEvidence(
        String configurationId,
        EnergyEvaluation preRelaxation,
        EnergyEvaluation postRelaxation,
        Structure realizedCoordinates,
        Structure relaxedCoordinates,
        Displacements displacements,
        Validity validity,
        boolean minimizationConverged,
        List<String> failures,
        String protocolId) {
    public RelaxationEvidence {
        Objects.requireNonNull(preRelaxation, "preRelaxation");
        Objects.requireNonNull(postRelaxation, "postRelaxation");
        Objects.requireNonNull(realizedCoordinates, "realizedCoordinates");
        Objects.requireNonNull(relaxedCoordinates, "relaxedCoordinates");
        Objects.requireNonNull(displacements, "displacements");
        Objects.requireNonNull(validity, "validity");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        if (validity == Validity.VALID && (!minimizationConverged || !failures.isEmpty())) {
            throw new IllegalArgumentException("valid relaxation cannot contain failures");
        }
        if (protocolId == null || protocolId.isBlank()) {
            throw new IllegalArgumentException("protocolId required");
        }
    }

    public record Displacements(double ligandHeavyAtomRmsdAngstroms,
            double localSideChainRmsdAngstroms, double backboneRmsdAngstroms,
            double samRmsdAngstroms, double maximumAtomDisplacementAngstroms) {
        public Displacements {
            for (double value : new double[] {ligandHeavyAtomRmsdAngstroms,
                    localSideChainRmsdAngstroms, backboneRmsdAngstroms,
                    samRmsdAngstroms, maximumAtomDisplacementAngstroms}) {
                if (!Double.isFinite(value) || value < 0.0) {
                    throw new IllegalArgumentException("displacements must be finite/nonnegative");
                }
            }
        }
    }

    public enum Validity { VALID, INVALID }
}
