package totah.lab.athena.ligand.pose;

import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.pocket.compare.MultiHypothesisPocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignmentResult;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.PocketResiduePointFactory;
import totah.lab.athena.pocket.compare.residue.ResidueMatch;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.athena.sequence.StructureSequences;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Default {@link CrossProteinPoseComparator}. The candidate pocket is
 * aligned onto the query pocket with
 * {@link MultiHypothesisPocketAligner} (sequence-seeded when the
 * receptor sequences provide a usable seed); the resulting
 * candidate-to-query {@link RigidTransform} moves the candidate pose
 * into the query frame, where centroid distance, index-correspondence
 * RMSD and contact-residue overlap are measured.
 *
 * <p>Classification is deterministic, evaluated in order:</p>
 * <ol>
 *   <li>either pose assignment not {@link AssignmentStatus#ASSIGNED}
 *       &rarr; {@link PoseSiteRelationship#AMBIGUOUS};</li>
 *   <li>pocket similarity below the homology threshold &rarr;
 *       {@link PoseSiteRelationship#DIFFERENT_SITE};</li>
 *   <li>aligned heavy-atom centroids within
 *       {@code sameSiteCentroidDistanceAngstroms} &rarr;
 *       {@link PoseSiteRelationship#SAME_HOMOLOGOUS_SITE};</li>
 *   <li>otherwise &rarr;
 *       {@link PoseSiteRelationship#HOMOLOGOUS_SITE_DIFFERENT_POSE}.</li>
 * </ol>
 *
 * <p>The aligned RMSD pairs heavy atoms by index, so it is only
 * meaningful for the same compound in the same atom order (atom
 * ordering is preserved end to end); it is {@code null} when the
 * heavy-atom counts differ.
 *
 * <p>Both pockets must resolve to the same point-cloud basis (e.g. both
 * fpocket alpha-sphere pockets); mixing bases is rejected by the
 * underlying pocket comparator.
 */
public final class DefaultCrossProteinPoseComparator
        implements CrossProteinPoseComparator {

    private final MultiHypothesisPocketAligner pocketAligner =
            new MultiHypothesisPocketAligner();
    private final PocketResiduePointFactory residuePointFactory =
            new PocketResiduePointFactory();
    private final NeedlemanWunschSequenceAligner sequenceAligner =
            new NeedlemanWunschSequenceAligner();

    private final CrossProteinPoseComparisonOptions options;

    public DefaultCrossProteinPoseComparator() {
        this(CrossProteinPoseComparisonOptions.defaults());
    }

    public DefaultCrossProteinPoseComparator(
            CrossProteinPoseComparisonOptions options
    ) {
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public CrossProteinPoseComparison compare(
            String queryPoseLabel,
            Structure queryReceptor,
            PosePocketAssignment queryAssignment,
            Ligand queryPose,
            List<LigandContact> queryContacts,
            String candidatePoseLabel,
            Structure candidateReceptor,
            PosePocketAssignment candidateAssignment,
            Ligand candidatePose,
            List<LigandContact> candidateContacts
    ) {
        Objects.requireNonNull(queryReceptor, "queryReceptor");
        Objects.requireNonNull(queryAssignment, "queryAssignment");
        Objects.requireNonNull(queryPose, "queryPose");
        Objects.requireNonNull(queryContacts, "queryContacts");
        Objects.requireNonNull(candidateReceptor, "candidateReceptor");
        Objects.requireNonNull(
                candidateAssignment,
                "candidateAssignment"
        );
        Objects.requireNonNull(candidatePose, "candidatePose");
        Objects.requireNonNull(candidateContacts, "candidateContacts");

        Pocket queryPocket = queryAssignment.pocket();
        Pocket candidatePocket = candidateAssignment.pocket();

        if (queryAssignment.status() != AssignmentStatus.ASSIGNED
                || candidateAssignment.status()
                        != AssignmentStatus.ASSIGNED) {
            return new CrossProteinPoseComparison(
                    queryPoseLabel,
                    candidatePoseLabel,
                    queryPocket,
                    candidatePocket,
                    samePocketNumber(queryPocket, candidatePocket),
                    false,
                    null,
                    null,
                    null,
                    0,
                    0.0,
                    PoseSiteRelationship.AMBIGUOUS,
                    "pose assignment insufficient: query pose is "
                            + queryAssignment.status()
                            + ", candidate pose is "
                            + candidateAssignment.status()
            );
        }

        PocketAlignmentResult alignment = alignPockets(
                queryReceptor,
                queryPocket,
                candidateReceptor,
                candidatePocket
        );

        double pocketSimilarity =
                alignment.comparison().overallSimilarity();
        boolean homologous = pocketSimilarity
                >= options.homologySimilarityThreshold();

        RigidTransform candidateToQuery =
                alignment.alignment().transform();

        List<Point3D> queryPositions =
                LigandGeometry.heavyAtomPositions(queryPose);
        List<Point3D> alignedCandidatePositions = candidateToQuery.apply(
                LigandGeometry.heavyAtomPositions(candidatePose)
        );

        double centroidDistance = centroid(queryPositions)
                .distance(centroid(alignedCandidatePositions));

        Double rmsd = queryPositions.size()
                == alignedCandidatePositions.size()
                ? rmsd(queryPositions, alignedCandidatePositions)
                : null;

        ContactOverlap contactOverlap = contactOverlap(
                queryContacts,
                candidateContacts,
                alignment
        );

        boolean samePocketNumber =
                samePocketNumber(queryPocket, candidatePocket);

        if (!homologous) {
            return new CrossProteinPoseComparison(
                    queryPoseLabel,
                    candidatePoseLabel,
                    queryPocket,
                    candidatePocket,
                    samePocketNumber,
                    false,
                    pocketSimilarity,
                    centroidDistance,
                    rmsd,
                    contactOverlap.sharedResidues(),
                    contactOverlap.jaccard(),
                    PoseSiteRelationship.DIFFERENT_SITE,
                    String.format(
                            "pocket similarity %.3f is below the "
                                    + "homology threshold %.3f; the "
                                    + "predicted poses occupy "
                                    + "non-homologous sites",
                            pocketSimilarity,
                            options.homologySimilarityThreshold()
                    )
            );
        }

        if (centroidDistance <= options.sameSiteCentroidDistanceAngstroms()) {
            return new CrossProteinPoseComparison(
                    queryPoseLabel,
                    candidatePoseLabel,
                    queryPocket,
                    candidatePocket,
                    samePocketNumber,
                    true,
                    pocketSimilarity,
                    centroidDistance,
                    rmsd,
                    contactOverlap.sharedResidues(),
                    contactOverlap.jaccard(),
                    PoseSiteRelationship.SAME_HOMOLOGOUS_SITE,
                    String.format(
                            "homologous pockets (similarity %.3f) and "
                                    + "aligned pose centroids %.2f A "
                                    + "apart (within %.1f A)",
                            pocketSimilarity,
                            centroidDistance,
                            options.sameSiteCentroidDistanceAngstroms()
                    )
            );
        }

        return new CrossProteinPoseComparison(
                queryPoseLabel,
                candidatePoseLabel,
                queryPocket,
                candidatePocket,
                samePocketNumber,
                true,
                pocketSimilarity,
                centroidDistance,
                rmsd,
                contactOverlap.sharedResidues(),
                contactOverlap.jaccard(),
                PoseSiteRelationship.HOMOLOGOUS_SITE_DIFFERENT_POSE,
                String.format(
                        "homologous pockets (similarity %.3f) but "
                                + "aligned pose centroids %.2f A apart "
                                + "(beyond %.1f A)",
                        pocketSimilarity,
                        centroidDistance,
                        options.sameSiteCentroidDistanceAngstroms()
                )
        );
    }

    private PocketAlignmentResult alignPockets(
            Structure queryReceptor,
            Pocket queryPocket,
            Structure candidateReceptor,
            Pocket candidatePocket
    ) {
        PocketPointCloud queryCloud =
                PocketPointCloud.from(queryReceptor, queryPocket);
        PocketPointCloud candidateCloud =
                PocketPointCloud.from(candidateReceptor, candidatePocket);

        List<PocketResiduePoint> queryResidues =
                residuePointFactory.create(queryReceptor, queryPocket);
        List<PocketResiduePoint> candidateResidues =
                residuePointFactory.create(
                        candidateReceptor,
                        candidatePocket
                );

        SequenceAlignment sequenceAlignment = sequenceAligner.align(
                StructureSequences.sequenceResidues(queryReceptor),
                StructureSequences.sequenceResidues(candidateReceptor)
        );

        return pocketAligner.align(
                queryCloud,
                candidateCloud,
                queryResidues,
                candidateResidues,
                sequenceAlignment
        );
    }

    /**
     * Contact-residue overlap after mapping candidate contact residues
     * into query residue identities through the pocket alignment's
     * {@code ResidueCorrespondence}. Candidate contacts without a
     * corresponding query residue count as non-shared.
     */
    private static ContactOverlap contactOverlap(
            List<LigandContact> queryContacts,
            List<LigandContact> candidateContacts,
            PocketAlignmentResult alignment
    ) {
        Map<ResidueKey, ResidueKey> candidateToQuery = new HashMap<>();

        for (ResidueMatch match : alignment.correspondence().matches()) {
            candidateToQuery.putIfAbsent(
                    key(match.candidate().reference()),
                    key(match.query().reference())
            );
        }

        Set<ResidueKey> queryResidues = new HashSet<>();
        for (LigandContact contact : queryContacts) {
            queryResidues.add(key(contact.residue()));
        }

        Set<ResidueKey> mappedCandidateResidues = new HashSet<>();
        for (LigandContact contact : candidateContacts) {
            ResidueKey mapped =
                    candidateToQuery.get(key(contact.residue()));
            if (mapped != null) {
                mappedCandidateResidues.add(mapped);
            }
        }

        Set<ResidueKey> union = new HashSet<>(queryResidues);
        union.addAll(mappedCandidateResidues);

        Set<ResidueKey> shared = new HashSet<>(queryResidues);
        shared.retainAll(mappedCandidateResidues);

        double jaccard = union.isEmpty()
                ? 0.0
                : shared.size() / (double) union.size();

        return new ContactOverlap(shared.size(), jaccard);
    }

    private static boolean samePocketNumber(
            Pocket queryPocket,
            Pocket candidatePocket
    ) {
        return queryPocket != null
                && candidatePocket != null
                && queryPocket.id().equals(candidatePocket.id());
    }

    private static Point3D centroid(List<Point3D> positions) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (Point3D position : positions) {
            x += position.x();
            y += position.y();
            z += position.z();
        }

        double count = positions.size();

        return new Point3D(x / count, y / count, z / count);
    }

    private static double rmsd(
            List<Point3D> queryPositions,
            List<Point3D> alignedCandidatePositions
    ) {
        double sum = 0.0;

        for (int index = 0; index < queryPositions.size(); index++) {
            sum += queryPositions.get(index).distanceSquared(
                    alignedCandidatePositions.get(index)
            );
        }

        return Math.sqrt(sum / queryPositions.size());
    }

    private static ResidueKey key(ResidueId residueId) {
        return new ResidueKey(
                residueId.chainId(),
                residueId.residueNumber(),
                normalizeInsertionCode(residueId.insertionCode())
        );
    }

    private static ResidueKey key(ResidueReference reference) {
        return new ResidueKey(
                reference.chainId(),
                reference.residueNumber(),
                normalizeInsertionCode(reference.insertionCode())
        );
    }

    private static char normalizeInsertionCode(Character insertionCode) {
        return insertionCode == null
                || Character.isWhitespace(insertionCode)
                ? ' '
                : insertionCode;
    }

    private record ResidueKey(
            String chainId,
            int residueNumber,
            char insertionCode
    ) {
    }

    private record ContactOverlap(
            int sharedResidues,
            double jaccard
    ) {
    }
}
