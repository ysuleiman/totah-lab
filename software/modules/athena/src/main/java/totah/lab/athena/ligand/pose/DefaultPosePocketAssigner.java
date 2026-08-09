package totah.lab.athena.ligand.pose;

import totah.lab.athena.ligand.contact.ContactAnalyzer;
import totah.lab.athena.ligand.contact.DefaultContactAnalyzer;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default {@link PosePocketAssigner}. Every candidate pocket is
 * evaluated into {@link PosePocketMetrics} (alpha-sphere occupancy is
 * the primary signal; centroid proximity is the weakest and can never
 * win on its own), scored with the injected {@link PosePocketScorer},
 * and ranked by score descending with {@code PocketId} ascending as the
 * deterministic tiebreak.
 *
 * <p>Assignment rules are deterministic and never force a match:
 * <ul>
 *   <li>NOT_ASSIGNED when every candidate has zero containment and zero
 *       contact coverage, or the best score is below
 *       {@code minimumAssignmentScore};</li>
 *   <li>AMBIGUOUS when the margin to the runner-up is below
 *       {@code ambiguityMargin} (the best pocket is still reported,
 *       flagged);</li>
 *   <li>ASSIGNED otherwise.</li>
 * </ul>
 */
public final class DefaultPosePocketAssigner implements PosePocketAssigner {

    private final ContactAnalyzer contactAnalyzer;
    private final PosePocketScorer scorer;
    private final PosePocketAssignmentOptions options;

    public DefaultPosePocketAssigner() {
        this(
                new DefaultContactAnalyzer(),
                new DefaultPosePocketScorer(),
                PosePocketAssignmentOptions.defaults()
        );
    }

    public DefaultPosePocketAssigner(
            ContactAnalyzer contactAnalyzer,
            PosePocketScorer scorer,
            PosePocketAssignmentOptions options
    ) {
        this.contactAnalyzer = Objects.requireNonNull(
                contactAnalyzer,
                "contactAnalyzer"
        );
        this.scorer = Objects.requireNonNull(scorer, "scorer");
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public PosePocketAssignment assign(
            Structure receptor,
            List<Pocket> candidatePockets,
            Ligand pose,
            List<LigandContact> contacts
    ) {
        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(candidatePockets, "candidatePockets");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(contacts, "contacts");

        if (candidatePockets.isEmpty()) {
            return new PosePocketAssignment(
                    null,
                    null,
                    null,
                    null,
                    null,
                    0.0,
                    false,
                    AssignmentStatus.NOT_ASSIGNED,
                    "no candidate pockets"
            );
        }

        LigandShape shape = LigandGeometry.shape(pose);

        Set<ResidueId> contactResidues = contacts.stream()
                .map(LigandContact::residue)
                .collect(Collectors.toCollection(HashSet::new));

        List<ScoredPocket> ranked = candidatePockets.stream()
                .map(pocket -> evaluate(
                        receptor,
                        pocket,
                        pose,
                        shape,
                        contactResidues
                ))
                .map(metrics -> new ScoredPocket(
                        metrics,
                        scorer.score(metrics)
                ))
                .sorted(Comparator
                        .comparingDouble(ScoredPocket::score)
                        .reversed()
                        .thenComparing(scored -> scored.metrics()
                                .pocket()
                                .id()
                                .value()))
                .toList();

        return decide(ranked);
    }

    @Override
    public PosePocketAssignment assign(
            Structure receptor,
            List<Pocket> candidatePockets,
            Ligand pose
    ) {
        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(pose, "pose");

        return assign(
                receptor,
                candidatePockets,
                pose,
                contactAnalyzer.analyze(receptor, pose)
        );
    }

    private PosePocketMetrics evaluate(
            Structure receptor,
            Pocket pocket,
            Ligand pose,
            LigandShape shape,
            Set<ResidueId> contactResidues
    ) {
        boolean hasSpheres = AlphaSphereMetrics.hasSpheres(pocket);

        AlphaSphereOccupancy spheres = hasSpheres
                ? AlphaSphereMetrics.calculate(pose, pocket)
                : null;

        double atomContainmentFraction;
        PosePocketMetrics.ContainmentBasis basis;

        if (hasSpheres) {
            atomContainmentFraction = AlphaSphereMetrics.occupiedFraction(
                    pose,
                    pocket,
                    options.sphereToleranceAngstroms()
            );
            basis = PosePocketMetrics.ContainmentBasis.ALPHA_SPHERES;
        } else if (pocket.bounds()
                .map(bounds -> !bounds.isEmpty())
                .orElse(false)) {
            atomContainmentFraction = boundsContainmentFraction(
                    pose,
                    pocket.bounds().orElseThrow()
            );
            basis = PosePocketMetrics.ContainmentBasis.POCKET_BOUNDS;
        } else {
            atomContainmentFraction = residueProximityFraction(
                    receptor,
                    pocket,
                    pose
            );
            basis = PosePocketMetrics.ContainmentBasis.RESIDUE_ATOMS;
        }

        double centroidDistance =
                shape.centroid().distance(pocket.center());
        double centroidProximity = Math.max(
                0.0,
                1.0 - centroidDistance / options.centroidReferenceDistance()
        );

        Set<ResidueId> pocketResidues = new HashSet<>(pocket.residues());
        long overlap = contactResidues.stream()
                .filter(pocketResidues::contains)
                .count();

        double contactResidueCoverage = contactResidues.isEmpty()
                ? 0.0
                : overlap / (double) contactResidues.size();
        double pocketContactCoverage = pocketResidues.isEmpty()
                ? 0.0
                : overlap / (double) pocketResidues.size();

        return new PosePocketMetrics(
                pocket,
                spheres,
                centroidDistance,
                atomContainmentFraction,
                basis,
                contactResidueCoverage,
                pocketContactCoverage,
                centroidProximity
        );
    }

    private double boundsContainmentFraction(
            Ligand pose,
            BoundingBox bounds
    ) {
        BoundingBox expanded =
                bounds.expand(options.sphereToleranceAngstroms());

        List<Point3D> positions =
                LigandGeometry.heavyAtomPositions(pose);
        long contained = positions.stream()
                .filter(expanded::contains)
                .count();

        return contained / (double) positions.size();
    }

    private double residueProximityFraction(
            Structure receptor,
            Pocket pocket,
            Ligand pose
    ) {
        List<Point3D> residueAtoms = pocket.residues().stream()
                .map(receptor::findResidue)
                .flatMap(Optional::stream)
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .map(Atom::getPosition)
                .toList();

        if (residueAtoms.isEmpty()) {
            return 0.0;
        }

        List<Point3D> positions =
                LigandGeometry.heavyAtomPositions(pose);
        long contained = positions.stream()
                .filter(position -> isWithinAny(
                        position,
                        residueAtoms,
                        options.containmentRadius()
                ))
                .count();

        return contained / (double) positions.size();
    }

    private static boolean isWithinAny(
            Point3D position,
            List<Point3D> targets,
            double radius
    ) {
        for (Point3D target : targets) {
            if (position.distance(target) <= radius) {
                return true;
            }
        }
        return false;
    }

    private PosePocketAssignment decide(List<ScoredPocket> ranked) {
        ScoredPocket best = ranked.get(0);
        ScoredPocket second = ranked.size() > 1
                ? ranked.get(1)
                : null;

        double margin = second == null
                ? best.score()
                : best.score() - second.score();

        Pocket secondBestPocket = second == null
                ? null
                : second.metrics().pocket();
        Double secondBestScore = second == null
                ? null
                : second.score();

        boolean noEvidence = ranked.stream().allMatch(scored ->
                scored.metrics().atomContainmentFraction() == 0.0
                        && scored.metrics().contactResidueCoverage() == 0.0);

        if (noEvidence) {
            return new PosePocketAssignment(
                    null,
                    null,
                    best.metrics(),
                    secondBestPocket,
                    secondBestScore,
                    margin,
                    false,
                    AssignmentStatus.NOT_ASSIGNED,
                    "no pocket shows sphere occupancy or contact overlap"
            );
        }

        if (best.score() < options.minimumAssignmentScore()) {
            return new PosePocketAssignment(
                    null,
                    null,
                    best.metrics(),
                    secondBestPocket,
                    secondBestScore,
                    margin,
                    false,
                    AssignmentStatus.NOT_ASSIGNED,
                    String.format(
                            "best assignment score %.3f is below the "
                                    + "minimum %.3f",
                            best.score(),
                            options.minimumAssignmentScore()
                    )
            );
        }

        if (second != null && margin < options.ambiguityMargin()) {
            return new PosePocketAssignment(
                    best.metrics().pocket(),
                    best.score(),
                    best.metrics(),
                    secondBestPocket,
                    secondBestScore,
                    margin,
                    true,
                    AssignmentStatus.AMBIGUOUS,
                    String.format(
                            "score margin %.3f to pocket %s is below "
                                    + "the ambiguity margin %.3f",
                            margin,
                            second.metrics().pocket().id().value(),
                            options.ambiguityMargin()
                    )
            );
        }

        return new PosePocketAssignment(
                best.metrics().pocket(),
                best.score(),
                best.metrics(),
                secondBestPocket,
                secondBestScore,
                margin,
                false,
                AssignmentStatus.ASSIGNED,
                String.format(
                        "pose occupies pocket %s with score %.3f",
                        best.metrics().pocket().id().value(),
                        best.score()
                )
        );
    }

    private record ScoredPocket(
            PosePocketMetrics metrics,
            double score
    ) {
    }
}
