package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.store.GeneratedEvidenceCandidate;
import totah.lab.prometheus.store.GeneratedEvidenceRole;
import totah.lab.prometheus.identity.CanonicalAtomMap;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.gaia.geometry.Point3D;
import java.nio.file.Files;

/** Maps the locked PySCF pilot output into primary and validation-auxiliary evidence. */
public final class PyscfPilotEvidenceMapper implements GeneratedEvidenceMapper {

    private static final double FINITE_DIFFERENCE_TOLERANCE_HARTREE_PER_BOHR = 1.0e-4;
    private final EvidenceIdentity intendedIdentity;
    private final CanonicalAtomMap atomMap;

    public PyscfPilotEvidenceMapper(EvidenceIdentity intendedIdentity, CanonicalAtomMap atomMap) {
        this.intendedIdentity = intendedIdentity;
        this.atomMap = atomMap;
    }

    @Override
    public List<GeneratedEvidenceCandidate> validateAndMap(
            RawCalculationResult raw, Path artifactBase) throws IOException {
        new PyscfFiniteDifferenceArtifactRecovery().recover(artifactBase);
        Path resultPath = artifactBase.resolve("result.json");
        PyscfEnergyGradientResult result = new PyscfEnergyGradientResultReader().read(resultPath);
        List<Double> gradient = result.gradientHartreePerBohr().stream().flatMap(List::stream).toList();
        boolean finiteDifferencePass = result.finiteDifferenceAbsoluteErrorHartreePerBohr()
                <= FINITE_DIFFERENCE_TOLERANCE_HARTREE_PER_BOHR;
        EvidenceAcceptanceState acceptance = result.scfConverged() && finiteDifferencePass
                ? EvidenceAcceptanceState.ACCEPTED : EvidenceAcceptanceState.FAILED_NUMERICALLY;
        EvidenceProvenance provenance = new EvidenceProvenance(resultPath.toString(),
                ArtifactChecksums.sha256(resultPath), Instant.now(), List.of(),
                "locked generated PySCF energy+gradient result");
        QuantumEvidence primary = new QuantumEvidence(intendedIdentity, provenance,
                result.scfConverged() ? ConvergenceStatus.CONVERGED : ConvergenceStatus.NOT_CONVERGED,
                acceptance, Optional.of(result.energyHartree()), Optional.of(gradient), Optional.empty(),
                Optional.empty(), Optional.empty(), "SCF and finite-difference audit evaluated");

        List<RawArtifact> artifacts = artifacts(artifactBase);
        List<GeneratedEvidenceCandidate> candidates = new ArrayList<>();
        candidates.add(new GeneratedEvidenceCandidate(primary, GeneratedEvidenceRole.PRIMARY,
                artifactBase, artifacts, "reusable primary QM target"));
        candidates.add(auxiliary(artifactBase, artifacts, "plus",
                result.finiteDifferencePlusEnergyHartree()));
        candidates.add(auxiliary(artifactBase, artifacts, "minus",
                result.finiteDifferenceMinusEnergyHartree()));
        return List.copyOf(candidates);
    }

    private static List<RawArtifact> artifacts(Path base) throws IOException {
        List<RawArtifact> artifacts = new ArrayList<>();
        try (var paths = Files.walk(base)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                artifacts.add(new RawArtifact(base.relativize(path).toString(),
                        ArtifactChecksums.sha256(path), "generated_qm_artifact"));
            }
        }
        return List.copyOf(artifacts);
    }

    private GeneratedEvidenceCandidate auxiliary(
            Path artifactBase, List<RawArtifact> artifacts, String side, double energy) throws IOException {
        Path xyz = artifactBase.resolve("finite_difference_" + side + ".xyz");
        GeometryIdentity geometry = geometryIdentity(xyz);
        Path auxiliaryJson = artifactBase.resolve("finite_difference_" + side + ".json");
        EvidenceIdentity identity = new EvidenceIdentity(intendedIdentity.molecule(),
                intendedIdentity.atomMapHash(), geometry, intendedIdentity.formalCharge(),
                intendedIdentity.multiplicity(), CalculationType.SINGLE_POINT, intendedIdentity.protocol(),
                List.of(), List.of("VALIDATION_AUXILIARY finite-difference " + side + " energy"));
        QuantumEvidence evidence = new QuantumEvidence(identity,
                new EvidenceProvenance(auxiliaryJson.toString(), ArtifactChecksums.sha256(auxiliaryJson),
                        Instant.now(), List.of(intendedIdentity.evidenceHash()),
                        "displaced finite-difference validation point"),
                ConvergenceStatus.CONVERGED, EvidenceAcceptanceState.ACCEPTED,
                Optional.of(energy), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                "VALIDATION_AUXILIARY; excluded from QM target datasets");
        return new GeneratedEvidenceCandidate(evidence, GeneratedEvidenceRole.VALIDATION_AUXILIARY,
                artifactBase, artifacts, "finite-difference displaced point " + side);
    }

    private GeometryIdentity geometryIdentity(Path xyz) throws IOException {
        List<String> lines = Files.readAllLines(xyz);
        int count = Integer.parseInt(lines.getFirst().trim());
        if (count != atomMap.size()) throw new IOException("auxiliary atom count mismatch");
        List<Point3D> coordinates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String[] fields = lines.get(i + 2).trim().split("\\s+");
            if (!fields[0].equalsIgnoreCase(atomMap.atoms().get(i).elementSymbol())) {
                throw new IOException("auxiliary atom order mismatch at " + (i + 1));
            }
            coordinates.add(new Point3D(Double.parseDouble(fields[1]),
                    Double.parseDouble(fields[2]), Double.parseDouble(fields[3])));
        }
        return GeometryIdentity.of(atomMap, coordinates);
    }
}
