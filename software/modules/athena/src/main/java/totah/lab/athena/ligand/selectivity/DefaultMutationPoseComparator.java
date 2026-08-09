package totah.lab.athena.ligand.selectivity;

import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.ligand.pose.LigandGeometry;
import totah.lab.athena.ligand.pose.PosePocketAssignment;
import totah.lab.athena.pocket.compare.KabschRigidPointAligner;
import totah.lab.athena.pocket.compare.RigidPointAligner;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Default {@link MutationPoseComparator}. All geometry is heavy-atom
 * based (via {@link LigandGeometry}). The centroid shift is always
 * computed; the RMSD pairs heavy atoms by index in structure order —
 * the same-compound, same-atom-order assumption of the cross-protein
 * comparator — and is {@code null} when the heavy-atom counts differ.
 * The ligand rotation angle comes from the Kabsch fit between the
 * corresponding heavy atoms and is {@code null} when the
 * correspondence is invalid, fewer than three heavy atoms exist, or
 * either ligand's heavy atoms are effectively collinear (the rotation
 * is then not robustly defined).
 *
 * <p>Contact sets treat DIRECT and SHELL contacts alike. When neither
 * pose has any contact, {@code contactSetJaccard} is {@code 0.0}: an
 * empty union carries no contact evidence (consistent with the
 * cross-protein comparator's convention).
 */
public final class DefaultMutationPoseComparator
        implements MutationPoseComparator {

    private static final double COLLINEARITY_TOLERANCE = 1.0e-9;

    private final RigidPointAligner kabschAligner =
            new KabschRigidPointAligner();

    @Override
    public MutationPoseComparison compareSameFrame(
            String mutationLabel,
            Structure wtReceptor,
            Ligand wtPose,
            List<LigandContact> wtContacts,
            Structure mutantReceptor,
            Ligand mutantPose,
            List<LigandContact> mutantContacts,
            PosePocketAssignment pocketAssignmentBefore,
            PosePocketAssignment pocketAssignmentAfter,
            Double confidenceBefore,
            Double confidenceAfter
    ) {
        Objects.requireNonNull(wtReceptor, "wtReceptor");
        Objects.requireNonNull(wtPose, "wtPose");
        Objects.requireNonNull(wtContacts, "wtContacts");
        Objects.requireNonNull(mutantReceptor, "mutantReceptor");
        Objects.requireNonNull(mutantPose, "mutantPose");
        Objects.requireNonNull(mutantContacts, "mutantContacts");
        Objects.requireNonNull(
                pocketAssignmentBefore,
                "pocketAssignmentBefore"
        );
        Objects.requireNonNull(
                pocketAssignmentAfter,
                "pocketAssignmentAfter"
        );

        List<Point3D> referencePositions =
                LigandGeometry.heavyAtomPositions(wtPose);
        List<Point3D> mutantPositions =
                LigandGeometry.heavyAtomPositions(mutantPose);

        if (referencePositions.isEmpty() || mutantPositions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Both poses must contain heavy atoms"
            );
        }

        double centroidShift = centroid(referencePositions)
                .distance(centroid(mutantPositions));

        Double rmsd = null;
        Double rotationAngle = null;

        if (referencePositions.size() == mutantPositions.size()) {
            rmsd = indexRmsd(referencePositions, mutantPositions);
            rotationAngle = rotationAngle(
                    mutantPositions,
                    referencePositions
            );
        }

        Set<ResidueId> referenceResidues = contactResidues(wtContacts);
        Set<ResidueId> mutantResidues = contactResidues(mutantContacts);

        Set<ResidueId> gained = difference(
                mutantResidues,
                referenceResidues
        );
        Set<ResidueId> lost = difference(
                referenceResidues,
                mutantResidues
        );
        Set<ResidueId> retained = intersection(
                referenceResidues,
                mutantResidues
        );

        Set<ResidueId> union = new TreeSet<>(residueIdOrder());
        union.addAll(referenceResidues);
        union.addAll(mutantResidues);

        double jaccard = union.isEmpty()
                ? 0.0
                : retained.size() / (double) union.size();

        return new MutationPoseComparison(
                mutationLabel,
                wtPose.id(),
                mutantPose.id(),
                centroidShift,
                rmsd,
                rotationAngle,
                jaccard,
                List.copyOf(gained),
                List.copyOf(lost),
                List.copyOf(retained),
                pocketAssignmentBefore,
                pocketAssignmentAfter,
                null,
                confidenceBefore,
                confidenceAfter
        );
    }

    /**
     * Rotation angle (degrees) of the best-fit rigid transform mapping
     * the mutant heavy atoms onto the reference heavy atoms, or
     * {@code null} when the rotation is not robustly defined.
     */
    private Double rotationAngle(
            List<Point3D> mutantPositions,
            List<Point3D> referencePositions
    ) {
        if (mutantPositions.size() < 3
                || effectivelyCollinear(mutantPositions)
                || effectivelyCollinear(referencePositions)) {
            return null;
        }

        try {
            RigidTransform fit = kabschAligner.align(
                    mutantPositions,
                    referencePositions
            );
            double[][] rotation = fit.rotation();
            double trace = rotation[0][0]
                    + rotation[1][1]
                    + rotation[2][2];
            double cosine = Math.max(
                    -1.0,
                    Math.min(1.0, (trace - 1.0) / 2.0)
            );
            return Math.toDegrees(Math.acos(cosine));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Returns {@code true} when the points do not span a plane, so a
     * Kabsch rotation around the line would be underdetermined.
     */
    private static boolean effectivelyCollinear(List<Point3D> points) {
        Point3D first = points.getFirst();
        Point3D farthest = null;
        double maximumDistanceSquared = 0.0;

        for (Point3D point : points) {
            double distanceSquared = first.distanceSquared(point);

            if (distanceSquared > maximumDistanceSquared) {
                maximumDistanceSquared = distanceSquared;
                farthest = point;
            }
        }

        if (farthest == null
                || maximumDistanceSquared <= COLLINEARITY_TOLERANCE) {
            return true;
        }

        double axisX = farthest.x() - first.x();
        double axisY = farthest.y() - first.y();
        double axisZ = farthest.z() - first.z();

        for (Point3D point : points) {
            double dx = point.x() - first.x();
            double dy = point.y() - first.y();
            double dz = point.z() - first.z();

            double crossX = dy * axisZ - dz * axisY;
            double crossY = dz * axisX - dx * axisZ;
            double crossZ = dx * axisY - dy * axisX;

            double crossNormSquared = crossX * crossX
                    + crossY * crossY
                    + crossZ * crossZ;

            double tolerance = COLLINEARITY_TOLERANCE
                    * maximumDistanceSquared
                    * (1.0 + maximumDistanceSquared);

            if (crossNormSquared > tolerance) {
                return false;
            }
        }

        return true;
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

    private static double indexRmsd(
            List<Point3D> referencePositions,
            List<Point3D> mutantPositions
    ) {
        double sum = 0.0;

        for (int index = 0; index < referencePositions.size(); index++) {
            sum += referencePositions.get(index).distanceSquared(
                    mutantPositions.get(index)
            );
        }

        return Math.sqrt(sum / referencePositions.size());
    }

    private static Set<ResidueId> contactResidues(
            List<LigandContact> contacts
    ) {
        Set<ResidueId> residues = new TreeSet<>(residueIdOrder());

        for (LigandContact contact : contacts) {
            residues.add(contact.residue());
        }

        return residues;
    }

    private static Set<ResidueId> difference(
            Set<ResidueId> from,
            Set<ResidueId> minus
    ) {
        Set<ResidueId> difference = new TreeSet<>(residueIdOrder());
        difference.addAll(from);
        difference.removeAll(minus);
        return difference;
    }

    private static Set<ResidueId> intersection(
            Set<ResidueId> first,
            Set<ResidueId> second
    ) {
        Set<ResidueId> intersection = new TreeSet<>(residueIdOrder());
        intersection.addAll(first);
        intersection.retainAll(second);
        return intersection;
    }

    private static Comparator<ResidueId> residueIdOrder() {
        return Comparator
                .comparing(ResidueId::chainId)
                .thenComparingInt(ResidueId::residueNumber)
                .thenComparing(residueId -> residueId.insertionCode()
                        == null
                        ? ""
                        : residueId.insertionCode().toString());
    }
}
