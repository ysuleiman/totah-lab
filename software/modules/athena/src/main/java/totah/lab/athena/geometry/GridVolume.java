package totah.lab.athena.geometry;

import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic voxel-grid volume measurements ported from the proven
 * one-off Python analyses under {@code analysis/dcmb} and
 * {@code analysis/mettl7-closure/stage4}. Grids follow the Python
 * convention exactly: axis points are
 * {@code lo, lo + spacing, ...} while strictly below
 * {@code hi + spacing / 2} (numpy arange semantics), and volumes are
 * voxel counts times {@code spacing^3}. The class is atom-agnostic:
 * inputs are plain point sets.
 *
 * <p>One deliberate deviation from the Python cavity copies
 * ({@code local_cavity_volume} / {@code accessible_volume} /
 * {@code ligand_volume}): the reference (ligand) atoms themselves are
 * honored as occupied, so a voxel sitting on top of a reference atom
 * is never counted as free.</p>
 *
 * <p>All measurements are O(voxels x atoms); no spatial index is
 * used, mirroring the validated Python implementations.</p>
 */
public final class GridVolume {

    private GridVolume() {
    }

    /**
     * Local free volume around a reference atom set (typically a
     * ligand). The grid covers the reference bounding box expanded by
     * {@code options.paddingAngstroms()} on every side. A voxel is in
     * the region when it lies within padding of at least one
     * reference atom; it is free when its distance to EVERY
     * environment atom AND every reference atom is at least
     * {@code options.clearanceAngstroms()}.
     *
     * @param referenceAtoms atoms defining the neighborhood (ligand);
     *        must be non-empty
     * @param environmentAtoms obstructing atoms (protein); may be
     *        empty
     */
    public static FreeVolume localFreeVolume(
            List<Point3D> referenceAtoms,
            List<Point3D> environmentAtoms,
            FreeVolumeOptions options
    ) {
        Objects.requireNonNull(referenceAtoms, "referenceAtoms");
        Objects.requireNonNull(environmentAtoms, "environmentAtoms");
        Objects.requireNonNull(options, "options");
        if (referenceAtoms.isEmpty()) {
            throw new IllegalArgumentException(
                    "referenceAtoms must not be empty");
        }

        double spacing = options.spacingAngstroms();
        double padding = options.paddingAngstroms();
        double clearanceSquared =
                options.clearanceAngstroms() * options.clearanceAngstroms();
        double paddingSquared = padding * padding;

        double[] low = new double[3];
        double[] high = new double[3];
        bounds(referenceAtoms, padding, low, high);

        int nx = axisCount(low[0], high[0], spacing);
        int ny = axisCount(low[1], high[1], spacing);
        int nz = axisCount(low[2], high[2], spacing);

        int region = 0;
        int free = 0;

        for (int ix = 0; ix < nx; ix++) {
            double x = low[0] + ix * spacing;
            for (int iy = 0; iy < ny; iy++) {
                double y = low[1] + iy * spacing;
                for (int iz = 0; iz < nz; iz++) {
                    double z = low[2] + iz * spacing;
                    double referenceDistanceSquared =
                            minDistanceSquared(x, y, z, referenceAtoms);
                    if (referenceDistanceSquared > paddingSquared) {
                        continue;
                    }
                    region++;
                    if (referenceDistanceSquared < clearanceSquared) {
                        continue;
                    }
                    if (minDistanceSquared(x, y, z, environmentAtoms)
                            < clearanceSquared) {
                        continue;
                    }
                    free++;
                }
            }
        }

        return new FreeVolume(
                free * spacing * spacing * spacing,
                free,
                region,
                options
        );
    }

    /**
     * Volume of the union envelope of an atom set: voxels whose
     * distance to at least one atom is at most
     * {@code options.envelopeRadiusAngstroms()}. The grid covers the
     * atom bounding box expanded by the envelope radius.
     *
     * @param atoms must be non-empty
     */
    public static EnvelopeVolume envelopeVolume(
            List<Point3D> atoms,
            EnvelopeOptions options
    ) {
        Objects.requireNonNull(atoms, "atoms");
        Objects.requireNonNull(options, "options");
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("atoms must not be empty");
        }

        double spacing = options.spacingAngstroms();
        double radius = options.envelopeRadiusAngstroms();
        double radiusSquared = radius * radius;

        double[] low = new double[3];
        double[] high = new double[3];
        bounds(atoms, radius, low, high);

        int nx = axisCount(low[0], high[0], spacing);
        int ny = axisCount(low[1], high[1], spacing);
        int nz = axisCount(low[2], high[2], spacing);

        int occupied = 0;

        for (int ix = 0; ix < nx; ix++) {
            double x = low[0] + ix * spacing;
            for (int iy = 0; iy < ny; iy++) {
                double y = low[1] + iy * spacing;
                for (int iz = 0; iz < nz; iz++) {
                    double z = low[2] + iz * spacing;
                    if (minDistanceSquared(x, y, z, atoms)
                            <= radiusSquared) {
                        occupied++;
                    }
                }
            }
        }

        return new EnvelopeVolume(
                occupied * spacing * spacing * spacing,
                occupied,
                options
        );
    }

    /**
     * Overlap of the envelopes of two atom sets on a shared grid, as
     * in the Python {@code shared_volume}. The grid covers the
     * intersection of the two atom bounding boxes, each expanded by
     * the envelope radius; when the expanded boxes do not intersect
     * in some dimension the overlap is 0.0. Per-envelope volumes are
     * measured independently with
     * {@link #envelopeVolume(List, EnvelopeOptions)} on the same
     * options.
     *
     * @param first must be non-empty
     * @param second must be non-empty
     */
    public static SharedEnvelopeVolume sharedEnvelopeVolume(
            List<Point3D> first,
            List<Point3D> second,
            EnvelopeOptions options
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(options, "options");
        if (first.isEmpty() || second.isEmpty()) {
            throw new IllegalArgumentException(
                    "Both atom sets must be non-empty");
        }

        double spacing = options.spacingAngstroms();
        double radius = options.envelopeRadiusAngstroms();
        double radiusSquared = radius * radius;

        double[] minFirst = new double[3];
        double[] maxFirst = new double[3];
        double[] minSecond = new double[3];
        double[] maxSecond = new double[3];
        minMax(first, minFirst, maxFirst);
        minMax(second, minSecond, maxSecond);

        double[] low = new double[3];
        double[] high = new double[3];
        boolean disjoint = false;
        for (int axis = 0; axis < 3; axis++) {
            low[axis] = Math.max(minFirst[axis], minSecond[axis]) - radius;
            high[axis] = Math.min(maxFirst[axis], maxSecond[axis]) + radius;
            if (low[axis] > high[axis]) {
                disjoint = true;
            }
        }

        EnvelopeVolume firstVolume = envelopeVolume(first, options);
        EnvelopeVolume secondVolume = envelopeVolume(second, options);

        int overlap = 0;
        if (!disjoint) {
            int nx = axisCount(low[0], high[0], spacing);
            int ny = axisCount(low[1], high[1], spacing);
            int nz = axisCount(low[2], high[2], spacing);
            for (int ix = 0; ix < nx; ix++) {
                double x = low[0] + ix * spacing;
                for (int iy = 0; iy < ny; iy++) {
                    double y = low[1] + iy * spacing;
                    for (int iz = 0; iz < nz; iz++) {
                        double z = low[2] + iz * spacing;
                        if (minDistanceSquared(x, y, z, first) <= radiusSquared
                                && minDistanceSquared(x, y, z, second)
                                        <= radiusSquared) {
                            overlap++;
                        }
                    }
                }
            }
        }

        double overlapVolume = overlap * spacing * spacing * spacing;
        double smaller = Math.min(
                firstVolume.volumeCubicAngstroms(),
                secondVolume.volumeCubicAngstroms()
        );
        double fraction = smaller > 0.0 ? overlapVolume / smaller : 0.0;

        return new SharedEnvelopeVolume(
                overlapVolume,
                overlap,
                firstVolume.volumeCubicAngstroms(),
                secondVolume.volumeCubicAngstroms(),
                fraction,
                options
        );
    }

    /**
     * Number of grid points on one axis, replicating numpy
     * {@code arange(lo, hi + spacing / 2, spacing)}: the values
     * {@code lo + k * spacing} strictly below {@code hi + spacing / 2}.
     */
    private static int axisCount(double lo, double hi, double spacing) {
        double limit = hi + spacing / 2.0;
        int count = 0;
        while (lo + count * spacing < limit) {
            count++;
        }
        return count;
    }

    private static void bounds(
            List<Point3D> points,
            double margin,
            double[] low,
            double[] high
    ) {
        minMax(points, low, high);
        for (int axis = 0; axis < 3; axis++) {
            low[axis] -= margin;
            high[axis] += margin;
        }
    }

    private static void minMax(
            List<Point3D> points,
            double[] min,
            double[] max
    ) {
        min[0] = Double.POSITIVE_INFINITY;
        min[1] = Double.POSITIVE_INFINITY;
        min[2] = Double.POSITIVE_INFINITY;
        max[0] = Double.NEGATIVE_INFINITY;
        max[1] = Double.NEGATIVE_INFINITY;
        max[2] = Double.NEGATIVE_INFINITY;
        for (Point3D point : points) {
            min[0] = Math.min(min[0], point.x());
            min[1] = Math.min(min[1], point.y());
            min[2] = Math.min(min[2], point.z());
            max[0] = Math.max(max[0], point.x());
            max[1] = Math.max(max[1], point.y());
            max[2] = Math.max(max[2], point.z());
        }
    }

    private static double minDistanceSquared(
            double x,
            double y,
            double z,
            List<Point3D> atoms
    ) {
        double best = Double.POSITIVE_INFINITY;
        for (Point3D atom : atoms) {
            double dx = atom.x() - x;
            double dy = atom.y() - y;
            double dz = atom.z() - z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < best) {
                best = distanceSquared;
            }
        }
        return best;
    }
}
