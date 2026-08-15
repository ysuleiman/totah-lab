package totah.lab.prometheus.strategy;

import static totah.lab.prometheus.strategy.StrategyRequirements.development;
import static totah.lab.prometheus.strategy.StrategyRequirements.holdout;

import java.util.List;
import java.util.Set;

/** QUBE scientific methodology, deliberately independent of QUBEKit as an implementation. */
public final class QubeLikeStrategy implements ScientificParameterizationStrategy {
    private static final ScientificStrategyDescriptor DESCRIPTOR = new ScientificStrategyDescriptor(
            "qube-like",
            "QUBE-like whole-molecule parameterization",
            "QM-derived bespoke force field using DDEC-like atoms-in-molecule electrostatics and dispersion",
            "Fixed atom-centred charges and pairwise 12-6 LJ; harmonic bonds/angles; periodic proper and improper torsions",
            List.of(
                    development(ScientificEvidenceKind.OPTIMIZED_GEOMETRY,
                            "stationary geometry at the declared bonded-derivation protocol", true, false,
                            "defines the reference structure and atom mapping"),
                    development(ScientificEvidenceKind.HESSIAN,
                            "Cartesian Hessian at the same geometry and compatible QM protocol", true, false,
                            "derives harmonic bond and angle terms"),
                    development(ScientificEvidenceKind.ELECTRON_DENSITY,
                            "wavefunction electron density suitable for the selected DDEC/AIM partition", true, false,
                            "derives atomic charges and atomic volumes"),
                    development(ScientificEvidenceKind.ATOM_IN_MOLECULE_VOLUMES,
                            "DDEC/AIM partition tied to the electron-density calculation", true, true,
                            "scales element reference dispersion parameters into molecule-specific LJ terms"),
                    development(ScientificEvidenceKind.TORSION_PROFILE,
                            "relaxed QM profiles for each fitted rotatable torsion at one internally consistent protocol",
                            true, false, "fits periodic torsion amplitudes against QM-MM residual energies"),
                    holdout(ScientificEvidenceKind.CONFORMATIONAL_ENERGIES,
                            "independent conformers or profiles excluded from torsion fitting", false, true,
                            "tests transfer beyond fitted scans")),
            List.of(
                    new ExternalDependencyDescriptor("QM electronic-structure engine",
                            "geometry, Hessian, density, and torsion energies", true,
                            "any engine producing equivalent authoritative artifacts"),
                    new ExternalDependencyDescriptor("DDEC/AIM partitioner",
                            "atomic charges and volumes", true,
                            "Chargemol or a scientifically equivalent declared partitioner"),
                    new ExternalDependencyDescriptor("torsion optimizer",
                            "regularized periodic torsion fit", true,
                            "ForceBalance, QUBEKit torsion fitting, or an equivalent audited optimizer")),
            Set.of(ParameterizationCapability.ATOMIC_CHARGES, ParameterizationCapability.BONDS,
                    ParameterizationCapability.ANGLES, ParameterizationCapability.PROPER_TORSIONS,
                    ParameterizationCapability.IMPROPERS, ParameterizationCapability.VAN_DER_WAALS,
                    ParameterizationCapability.WHOLE_MOLECULE_PARAMETERIZATION),
            EngineCompatibility.DIRECT,
            "Requires explicit export of the declared combination rules, exclusions, and 1-4 scaling",
            EngineCompatibility.REQUIRES_DOCUMENTED_TRANSLATION,
            "Amber export must preserve the QUBE LJ convention, exclusions, 1-4 scaling, and torsion convention",
            List.of(
                    "DDEC/AIM charges and volumes cannot be replaced by RESP evidence without changing methodology",
                    "Transferability is limited by the density protocol and torsion-training coverage",
                    "The standard harmonic bonded and separable torsion form cannot represent arbitrary cross-coupling"));

    @Override
    public ScientificStrategyDescriptor scientificDescriptor() {
        return DESCRIPTOR;
    }
}
