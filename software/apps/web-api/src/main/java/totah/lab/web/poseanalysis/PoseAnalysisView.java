package totah.lab.web.poseanalysis;

import totah.lab.web.chemistry.ResidueChemistryView;
import totah.lab.web.chemistry.ResidueChemistryViewMapper;

import java.util.List;
import java.util.Map;

/**
 * Immutable view DTOs for the receptor-ligand pose analysis API.
 */
public final class PoseAnalysisView {

    private PoseAnalysisView() {
    }

    public record LigandOptionView(
            String ligandId,
            String label,
            String smiles,
            long runCount,
            long poseCount,
            Double bestScore
    ) {
    }

    public record LigandRunOptionView(
            String ligandId,
            String label,
            String smiles,
            long runId,
            String method,
            long poseCount,
            Double bestScore
    ) {
    }

    public record PoseView(
            long poseId,
            String label,
            double score,
            Integer seed,
            Integer mode,
            Integer rank,
            Double confidence
    ) {
    }

    public record RunSummaryView(
            long runId,
            long receptorId,
            String target,
            String uniProtId,
            String method,
            long poseCount,
            Double bestScore,
            Map<Integer, Double> perSeedBestScores,
            List<PoseView> poses
    ) {
    }

    public record ResidueContactView(
            String chain,
            int residueNumber,
            String residueName,
            double minimumDistance,
            ResidueChemistryView chemistry,
            List<InteractionView> interactions
    ) {
        public ResidueContactView {
            interactions = List.copyOf(interactions);
        }

        public ResidueContactView(
                String chain,
                int residueNumber,
                String residueName,
                double minimumDistance
        ) {
            this(chain, residueNumber, residueName, minimumDistance,
                    ResidueChemistryViewMapper.map(residueName), List.of());
        }
    }

    public record InteractionView(
            String type,
            String label,
            String receptorAtom,
            String ligandAtom,
            double distance,
            Double angleDegrees,
            String basis
    ) {
    }

    public record ContactProfileView(
            long runId,
            Long poseId,
            String label,
            String method,
            String target,
            double cutoffAngstroms,
            boolean available,
            String unavailableReason,
            List<ResidueContactView> contacts
    ) {
    }

    public record ClusterMemberView(
            long poseId,
            String label,
            Integer seed,
            Integer mode
    ) {
    }

    public record ClusterSummaryView(
            long runId,
            String target,
            double thresholdAngstroms,
            int poseCount,
            int clusterCount,
            int largestClusterSize,
            List<ClusterMemberView> topClusterMembers,
            boolean available,
            String unavailableReason
    ) {
    }

    public record CysteineProximityView(
            int residueNumber,
            double minimumDistance
    ) {
    }

    public record SamProximityView(
            long runId,
            Long poseId,
            String label,
            String target,
            int samResidueCount,
            Double minimumDistanceToSamSet,
            List<CysteineProximityView> samSetCysteines,
            boolean available,
            String unavailableReason
    ) {
    }

    public record DockingTargetView(
            long receptorId,
            long structureId,
            String targetName,
            String uniProtId,
            long runCount,
            long ligandCount
    ) {
    }

    public record LigandAnalysisView(
            long receptorId,
            String ligandId,
            String ligandLabel,
            String smiles,
            List<RunSummaryView> runs,
            List<ContactProfileView> contactProfiles,
            List<ClusterSummaryView> clusters,
            List<SamProximityView> samProximity
    ) {
    }

    /**
     * A pocket referenced by a pose assignment: database id, the
     * per-source pocket number and the detection source.
     */
    public record AssignedPocketView(
            long pocketId,
            int pocketNumber,
            String source
    ) {
    }

    /**
     * Component metrics of the best candidate pocket of a pose
     * assignment. The alpha-sphere fields are {@code null} when the
     * pocket carries no spheres; {@code containmentBasis} records which
     * geometric basis produced {@code atomContainmentFraction} in that
     * case, so the fallback stays visible.
     */
    public record AssignmentMetricsView(
            Double ligandCentroidDistance,
            Double atomContainmentFraction,
            String containmentBasis,
            Double atomWithin2AOfSphereFraction,
            Double atomWithin3AOfSphereFraction,
            Double meanNearestSphereDistance,
            Double maxNearestSphereDistance,
            Double contactResidueCoverage,
            Double pocketContactCoverage
    ) {
    }

    /**
     * Provenance of one structure-bearing artifact, for the
     * coordinate-frame audit trail of pose-pocket analyses.
     */
    public record ArtifactRefView(
            String artifactId,
            String accession,
            String source,
            String modelVersion,
            String sha256
    ) {
    }

    /**
     * Coordinate-frame provenance of a pose-pocket analysis: the
     * docked receptor artifact, the structure artifact the pocket rows
     * were generated from, their validated compatibility
     * (IDENTICAL_ARTIFACT / VALIDATED_TRANSFORM / INCOMPATIBLE /
     * UNKNOWN) and whether sphere-derived metrics may be trusted
     * ({@code sphereMetrics} AVAILABLE / NOT_AVAILABLE). Contact
     * residue coverage is frame-independent and always computed.
     */
    public record ProvenanceView(
            ArtifactRefView receptorArtifact,
            ArtifactRefView pocketStructureArtifact,
            String compatibility,
            String sphereMetrics,
            Integer transformMatchedPairs,
            Double transformRmsdAngstroms,
            String note
    ) {
    }

    /**
     * The pocket a Vina pose is assigned to. {@code score} is the Vina
     * affinity and {@code assignmentScore} the geometric assignment
     * score — separate fields, never merged. The pose is
     * <i>assigned to</i> a pocket; nothing here asserts binding.
     * {@code status} is ASSIGNED, AMBIGUOUS or NOT_ASSIGNED with the
     * deciding {@code reason}. {@code metrics} carries the best
     * candidate's component metrics even for NOT_ASSIGNED (when
     * candidates existed), so the rejected evidence stays visible.
     * {@code provenance} carries the coordinate-frame audit trail; it
     * is {@code null} when the analysis itself was unavailable.
     */
    public record PosePocketAssignmentView(
            long poseId,
            String label,
            double score,
            boolean available,
            String unavailableReason,
            String status,
            String reason,
            AssignedPocketView assignedPocket,
            Double assignmentScore,
            AssignedPocketView secondBestPocket,
            Double secondBestScore,
            Double scoreMargin,
            boolean ambiguous,
            AssignmentMetricsView metrics,
            ProvenanceView provenance
    ) {
    }

    /**
     * Pose-occupancy summary of one pocket across a docking run:
     * pose frequency only — how often predicted poses occupy the
     * pocket. {@code bestAffinity}/{@code medianAffinity} are Vina
     * affinities of the assigned poses; affinity and assignment score
     * are reported side by side, never merged.
     */
    public record PocketOccupancyEntryView(
            long pocketId,
            int pocketNumber,
            String source,
            int poseCount,
            double fractionOfPoses,
            double bestAffinity,
            double medianAffinity,
            double meanAssignmentScore,
            double bestAssignmentScore,
            List<String> poseLabels
    ) {
    }

    /**
     * Pocket-occupancy report of one docking run: one entry per pocket
     * at least one pose was assigned to, sorted by pose count
     * descending, plus the poses that could not be assigned and the
     * poses whose assignment was ambiguous (those still count toward
     * their reported best pocket).
     */
    public record PocketOccupancyView(
            long runId,
            boolean available,
            String unavailableReason,
            List<PocketOccupancyEntryView> entries,
            int notAssignedCount,
            int ambiguousCount,
            ProvenanceView provenance
    ) {
    }

    /**
     * One side of a cross-protein pose comparison: the pose, its Vina
     * score, its receptor and the pocket it was assigned to
     * ({@code null} when not ASSIGNED).
     */
    public record CrossPoseView(
            long poseId,
            String label,
            double score,
            String target,
            String uniProtId,
            AssignedPocketView assignedPocket
    ) {
    }

    /**
     * Whether two Vina poses docked against two receptors occupy
     * structurally homologous sites, decided by structural pocket
     * alignment. {@code samePocketNumber} is informational only —
     * pocket numbers are per-protein detection artifacts and never
     * evidence of correspondence. The aligned fields are {@code null}
     * when the comparison could not run. This is geometric evidence
     * about predicted poses, not proof about binding sites.
     */
    public record CrossProteinPoseComparisonView(
            boolean available,
            String unavailableReason,
            CrossPoseView query,
            CrossPoseView candidate,
            boolean samePocketNumber,
            boolean pocketsStructurallyHomologous,
            Double pocketSimilarity,
            Double alignedLigandCentroidDistance,
            Double alignedLigandRmsd,
            int sharedAlignedContactResidues,
            double contactResidueSimilarity,
            String relationship,
            String reason
    ) {
    }
}
