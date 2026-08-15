package totah.lab.prometheus.strategy;

import static totah.lab.prometheus.strategy.StrategyRequirements.development;
import static totah.lab.prometheus.strategy.StrategyRequirements.holdout;
import static totah.lab.prometheus.strategy.StrategyRequirements.optional;

import java.util.List;
import java.util.Set;

/** General simultaneous target-fitting methodology; ForceBalance is an optimizer, not a force field. */
public final class ForceBalanceStyleStrategy implements ScientificParameterizationStrategy {
    private static final ScientificStrategyDescriptor DESCRIPTOR = new ScientificStrategyDescriptor(
            "forcebalance-style", "ForceBalance-style simultaneous fit", "regularized multi-target optimization",
            "Selected by the project: the optimizer only varies explicitly exposed parameters in an existing force-field form",
            List.of(
                    development(ScientificEvidenceKind.INITIAL_FORCE_FIELD,
                            "complete executable topology plus an explicit list of independently justifiable fit parameters",
                            true, false, "defines the model form and prevents accidental parameter movement"),
                    development(ScientificEvidenceKind.CARTESIAN_FORCES,
                            "QM forces on prospectively selected, common-protocol training geometries", true, false,
                            "constrains local derivatives rather than energies alone"),
                    development(ScientificEvidenceKind.CONFORMATIONAL_ENERGIES,
                            "relative energies within comparable protocol groups", true, true,
                            "constrains conformational energetics"),
                    optional(ScientificEvidenceKind.INTERACTION_ENERGIES,
                            "counterpoise-consistent interaction targets with geometry-valid probes", true, true,
                            "constrains selected nonbonded terms only when the dataset makes them identifiable"),
                    optional(ScientificEvidenceKind.HESSIAN,
                            "Hessian or vibrational targets compatible with the training protocol", true, false,
                            "adds curvature information"),
                    holdout(ScientificEvidenceKind.VALIDATION_HOLDOUT,
                            "entire conformers or target families sealed before parameter selection", false, false,
                            "detects overfit and controls model selection")),
            List.of(
                    new ExternalDependencyDescriptor("ForceBalance-compatible optimizer",
                            "regularized objective evaluation and parameter updates", true,
                            "ForceBalance or a verified equivalent optimizer"),
                    new ExternalDependencyDescriptor("classical energy/force engine",
                            "evaluates the selected production functional form", true,
                            "OpenMM, Amber, or another engine with exact parameter semantics")),
            Set.of(ParameterizationCapability.FORCE_MATCHING, ParameterizationCapability.BONDS,
                    ParameterizationCapability.ANGLES, ParameterizationCapability.PROPER_TORSIONS,
                    ParameterizationCapability.IMPROPERS, ParameterizationCapability.ATOMIC_CHARGES,
                    ParameterizationCapability.VAN_DER_WAALS, ParameterizationCapability.WHOLE_MOLECULE_PARAMETERIZATION),
            EngineCompatibility.DEPENDS_ON_SELECTED_FUNCTIONAL_FORM,
            "Compatibility is inherited from the fitted topology and target engine",
            EngineCompatibility.DEPENDS_ON_SELECTED_FUNCTIONAL_FORM,
            "Amber compatibility requires fitting only terms representable with the intended Amber conventions",
            List.of(
                    "Optimization cannot make an inadequate functional form adequate",
                    "Charges and LJ are not identifiable unless independent targets perturb those terms distinctly",
                    "Targets from materially different QM protocols must remain separate or be excluded",
                    "A sufficiently large prospectively sealed holdout is mandatory"));

    @Override
    public ScientificStrategyDescriptor scientificDescriptor() {
        return DESCRIPTOR;
    }
}
