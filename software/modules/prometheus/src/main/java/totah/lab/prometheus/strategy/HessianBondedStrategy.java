package totah.lab.prometheus.strategy;

import static totah.lab.prometheus.strategy.StrategyRequirements.development;
import static totah.lab.prometheus.strategy.StrategyRequirements.holdout;

import java.util.List;
import java.util.Set;

/** Minimal Hessian-derived harmonic bond/angle route, such as modified Seminario. */
public final class HessianBondedStrategy implements ScientificParameterizationStrategy {
    private static final ScientificStrategyDescriptor DESCRIPTOR = new ScientificStrategyDescriptor(
            "hessian-bonded", "Hessian-derived bonded terms", "local projection of a QM Cartesian Hessian",
            "Harmonic bonds and harmonic angles only; all nonbonded and torsional terms come from another declared model",
            List.of(
                    development(ScientificEvidenceKind.OPTIMIZED_GEOMETRY,
                            "verified QM local minimum with authoritative connectivity and atom order", true, false,
                            "defines equilibrium bond lengths and angles"),
                    development(ScientificEvidenceKind.HESSIAN,
                            "full Cartesian Hessian evaluated at that exact minimum and protocol", true, false,
                            "derives projected harmonic force constants"),
                    holdout(ScientificEvidenceKind.CONFORMATIONAL_ENERGIES,
                            "independent distorted geometries or conformers", false, true,
                            "checks whether the local harmonic approximation transfers")),
            List.of(new ExternalDependencyDescriptor("Hessian projection implementation",
                    "derives bond and angle constants without rerunning QM", true,
                    "modified Seminario or another declared, regression-tested projection")),
            Set.of(ParameterizationCapability.BONDS, ParameterizationCapability.ANGLES),
            EngineCompatibility.DIRECT, "Produces ordinary harmonic terms supported by OpenMM",
            EngineCompatibility.DIRECT, "Produces ordinary harmonic terms supported by Amber",
            List.of(
                    "Produces no charges, LJ, proper torsions, or complete force field",
                    "It is a local harmonic approximation and cannot represent strong angle/torsion or angle/LJ coupling",
                    "Results depend on the selected minimum and Hessian protocol"));

    @Override
    public ScientificStrategyDescriptor scientificDescriptor() {
        return DESCRIPTOR;
    }
}
