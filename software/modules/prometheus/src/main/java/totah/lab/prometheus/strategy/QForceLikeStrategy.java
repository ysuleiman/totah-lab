package totah.lab.prometheus.strategy;

import static totah.lab.prometheus.strategy.StrategyRequirements.development;
import static totah.lab.prometheus.strategy.StrategyRequirements.holdout;

import java.util.List;
import java.util.Set;

/** Scientific descriptor for QM Hessian/scan force-field derivation in the Q-Force family. */
public final class QForceLikeStrategy implements ScientificParameterizationStrategy {
    private static final ScientificStrategyDescriptor DESCRIPTOR = new ScientificStrategyDescriptor(
            "qforce-like", "Q-Force-like bonded parameterization", "QM Hessian and torsion-scan force matching",
            "Classical fixed-charge form with harmonic bonds/angles and separable periodic proper/improper torsions",
            List.of(
                    development(ScientificEvidenceKind.OPTIMIZED_GEOMETRY,
                            "QM minimum whose atom order and connectivity are authoritative", true, false,
                            "defines equilibrium geometry"),
                    development(ScientificEvidenceKind.HESSIAN,
                            "Cartesian Hessian at the reference minimum and compatible protocol", true, false,
                            "fits bonded curvature"),
                    development(ScientificEvidenceKind.ATOMIC_CHARGES,
                            "independently justified common charge model", false, false,
                            "prevents bonded terms compensating for unresolved electrostatics"),
                    development(ScientificEvidenceKind.TORSION_PROFILE,
                            "relaxed QM scan energies for each fitted local torsion", true, false,
                            "fits separable periodic torsions"),
                    holdout(ScientificEvidenceKind.CONFORMATIONAL_ENERGIES,
                            "independent minimum or scan family", false, true,
                            "detects non-transferable harmonic/separable fits")),
            List.of(
                    new ExternalDependencyDescriptor("QM engine", "geometry, Hessian, and scan targets", true,
                            "any supported engine with equivalent protocol and gradients/Hessian"),
                    new ExternalDependencyDescriptor("Q-Force-compatible fitter", "bonded and torsion fitting", true,
                            "Q-Force or an independently verified implementation of the declared objective")),
            Set.of(ParameterizationCapability.BONDS, ParameterizationCapability.ANGLES,
                    ParameterizationCapability.PROPER_TORSIONS, ParameterizationCapability.IMPROPERS),
            EngineCompatibility.DIRECT,
            "Compatible when emitted terms use supported harmonic and periodic forms",
            EngineCompatibility.REQUIRES_DOCUMENTED_TRANSLATION,
            "Atom types, Fourier convention, exclusions, and 1-4 scaling must be translated exactly",
            List.of(
                    "Does not itself establish transferable atomic charges or LJ parameters",
                    "Independent harmonic angles and separable torsions cannot repair a demonstrated coupled-coordinate defect",
                    "Heterogeneous scan protocols cannot be pooled as one fit target without comparability evidence"));

    @Override
    public ScientificStrategyDescriptor scientificDescriptor() {
        return DESCRIPTOR;
    }
}
