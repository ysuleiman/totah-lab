package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;

import java.util.List;
import java.util.Objects;

/**
 * Aggregate pocket-architecture comparison of two WT pockets with
 * their docked poses: per-pocket descriptors, backbone displacement,
 * alpha-sphere architecture, ligand space and wall geometry. Every
 * raw component metric is preserved on the part records; this
 * aggregate adds only the textual rendering.
 *
 * <p>The "Shape explanation" section of {@link #render()} is computed
 * strictly from the metrics. It quantifies geometry; it does not
 * speculate about mechanism and does not recommend mutations.</p>
 */
public record PocketArchitectureReport(
        PocketArchitecture pocketA,
        PocketArchitecture pocketB,
        BackboneArchitectureComparison backbone,
        AlphaSphereArchitectureComparison alphaSpheres,
        LigandSpaceComparison ligandSpace,
        WallGeometryComparison wall,
        LoopRegionAnalysis loopRegion
) {

    /**
     * CA RMSD below which backbone displacement is reported as
     * negligible. Calibration-pending.
     */
    public static final double NEGLIGIBLE_BACKBONE_RMSD_ANGSTROMS = 1.0;

    /**
     * Mean sphere-to-wall distance above which a pocket's alpha
     * spheres are flagged as inconsistent with its pocket residues
     * (typically a sign that the two originate from different
     * coordinate artifacts). Sphere-derived metrics for such a pocket
     * are unreliable. Calibration-pending.
     */
    public static final double SPHERE_WALL_CONSISTENCY_ANGSTROMS =
            10.0;

    /**
     * Mean segment CA displacement above which the "Shape explanation"
     * localizes the difference to that segment. Calibration-pending.
     */
    public static final double LOCALIZED_SEGMENT_DISPLACEMENT_ANGSTROMS =
            1.0;

    public PocketArchitectureReport {
        Objects.requireNonNull(pocketA, "pocketA");
        Objects.requireNonNull(pocketB, "pocketB");
        Objects.requireNonNull(backbone, "backbone");
        Objects.requireNonNull(alphaSpheres, "alphaSpheres");
        Objects.requireNonNull(ligandSpace, "ligandSpace");
        Objects.requireNonNull(wall, "wall");
        Objects.requireNonNull(loopRegion, "loopRegion");
    }

    /**
     * Renders all parts with their raw numbers plus the computed
     * "Shape explanation" section.
     */
    public String render() {
        StringBuilder out = new StringBuilder();

        out.append("Pocket architecture comparison\n");
        out.append("==============================\n");
        out.append("Pocket A: ").append(pocketA.pocket().id().value())
                .append("  Pocket B: ")
                .append(pocketB.pocket().id().value())
                .append('\n');

        appendArchitecture(out, "A", pocketA);
        appendArchitecture(out, "B", pocketB);

        out.append("\nBackbone (receptors aligned on ")
                .append(backbone.fittedResiduePairs())
                .append(" CA pairs)\n");
        out.append(String.format(
                "  pocket-region pairs: %d  CA RMSD: %.3f A  "
                        + "backbone RMSD: %.3f A  heavy-atom RMSD: "
                        + "%.3f A%n",
                backbone.pocketRegionResiduePairs(),
                backbone.caRmsd(),
                backbone.backboneRmsd(),
                backbone.heavyAtomRmsd()
        ));
        for (BackboneArchitectureComparison.ResidueDisplacement
                residue : backbone.displacementProfile()) {
            out.append(String.format(
                    "  %s:%s%d/%s%d  CA %.3f A%n",
                    residue.residueNameA(),
                    residue.residueA().chainId(),
                    residue.residueA().residueNumber(),
                    residue.residueNameB(),
                    residue.residueB().residueNumber(),
                    residue.caDisplacement()
            ));
        }

        out.append("\nAlpha spheres\n");
        out.append(String.format(
                "  components A=%d %s  B=%d %s%n",
                alphaSpheres.componentsA().componentCount(),
                alphaSpheres.componentsA().componentSizes(),
                alphaSpheres.componentsB().componentCount(),
                alphaSpheres.componentsB().componentSizes()
        ));
        out.append(String.format(
                "  principal-axis angle: %.2f deg%n",
                alphaSpheres.principalAxisAngleDegrees()
        ));
        out.append(String.format(
                "  unique spheres A=%s  B=%s%n",
                alphaSpheres.uniqueSpheresA(),
                alphaSpheres.uniqueSpheresB()
        ));
        out.append(String.format(
                "  sphere volume sums: A=%.1f A^3  B=%.1f A^3  "
                        + "delta=%.1f A^3%n",
                alphaSpheres.sphereVolumeSumA(),
                alphaSpheres.sphereVolumeSumB(),
                alphaSpheres.sphereVolumeSumDelta()
        ));
        out.append(String.format(
                "  pocket comparison: overall %.3f  geometry %.3f  "
                        + "mean bidirectional %.3f A%n",
                alphaSpheres.alignment().comparison().overallSimilarity(),
                alphaSpheres.alignment().comparison().geometrySimilarity(),
                alphaSpheres.alignment().comparison()
                        .meanBidirectionalDistance()
        ));

        out.append("\nLigand space\n");
        out.append(String.format(
                "  occupied spheres A=%s  B=%s%n",
                ligandSpace.occupiedSpheresA(),
                ligandSpace.occupiedSpheresB()
        ));
        out.append(String.format(
                "  occupied A-components: pose A %s  aligned pose B %s"
                        + "  (occupancy Jaccard %.3f)%n",
                ligandSpace.occupiedComponentsPoseA(),
                ligandSpace.occupiedComponentsPoseB(),
                ligandSpace.occupancyJaccard()
        ));
        out.append(String.format(
                "  aligned centroid displacement: %.2f A total; "
                        + "u1 (depth) %+.2f A, u2 %+.2f A, u3 %+.2f A "
                        + "(lateral %.2f A)%n",
                ligandSpace.alignedCentroidDisplacement(),
                ligandSpace.displacementAlongU1(),
                ligandSpace.displacementAlongU2(),
                ligandSpace.displacementAlongU3(),
                ligandSpace.lateralDisplacement()
        ));
        out.append(String.format(
                "  pose centroid depth: A=%.2f A  B=%.2f A;  "
                        + "mouth-center distance: A=%.2f A  "
                        + "B=%.2f A%n",
                ligandSpace.depthPoseA(),
                ligandSpace.depthPoseB(),
                ligandSpace.mouthDistancePoseA(),
                ligandSpace.mouthDistancePoseB()
        ));
        out.append(String.format(
                "  mean wall distance: A=%.2f A  B=%.2f A%n",
                ligandSpace.poseA().meanWallDistance(),
                ligandSpace.poseB().meanWallDistance()
        ));
        out.append("  dominant difference: ")
                .append(ligandSpace.dominantDifference())
                .append(" — ")
                .append(ligandSpace.reason())
                .append('\n');

        out.append("\nWall geometry\n");
        out.append(String.format(
                "  max side-chain displacement: %.3f A",
                wall.maxSideChainDisplacement()
        ));
        if (wall.maxDisplacementResidueA() != null) {
            out.append(String.format(
                    " at %s%d/%s%d",
                    wall.maxDisplacementResidueA().chainId(),
                    wall.maxDisplacementResidueA().residueNumber(),
                    wall.maxDisplacementResidueB().chainId(),
                    wall.maxDisplacementResidueB().residueNumber()
            ));
        }
        out.append('\n');
        out.append(String.format(
                "  mean wall distance: A=%.3f A  B=%.3f A%n",
                wall.meanWallDistanceA(),
                wall.meanWallDistanceB()
        ));
        out.append(String.format(
                "  wall normal angles: mean %.2f deg  max %.2f deg%n",
                wall.meanNormalAngleDegrees(),
                wall.maxNormalAngleDegrees()
        ));
        out.append(String.format(
                "  wall roughness: A=%.3f A  B=%.3f A%n",
                wall.meanRoughnessA(),
                wall.meanRoughnessB()
        ));
        appendConsistencyWarning(out, "A", wall.meanWallDistanceA());
        appendConsistencyWarning(out, "B", wall.meanWallDistanceB());

        appendLoopRegion(out);

        out.append("\nShape explanation\n");
        out.append("-----------------\n");
        appendShapeExplanation(out);

        return out.toString();
    }

    private void appendShapeExplanation(StringBuilder out) {
        int componentsA =
                alphaSpheres.componentsA().componentCount();
        int componentsB =
                alphaSpheres.componentsB().componentCount();

        out.append(String.format(
                "  extra cavity: %s (components A=%d, B=%d)%n",
                componentsA == componentsB ? "no" : "yes",
                componentsA,
                componentsB
        ));

        // Centroid offset of B (in the A frame) decomposed onto A's
        // principal axes.
        Point3D alignedCentroidB = alphaSpheres.alignment()
                .alignment().transform()
                .apply(pocketB.centroid());
        Vector3D offset = alignedCentroidB.vectorFrom(
                pocketA.centroid());
        StringBuilder offsets = new StringBuilder();
        for (int axis = 0; axis < 3; axis++) {
            offsets.append(String.format(
                    "u%d %+.2f A%s",
                    axis + 1,
                    offset.dot(
                            pocketA.principalComponents().axes()
                                    .get(axis)),
                    axis < 2 ? ", " : ""
            ));
        }
        out.append("  shifted cavity: centroid offset along A's "
                + "principal axes: ")
                .append(offsets)
                .append('\n');

        out.append(String.format(
                "  wider mouth: width delta %+.2f A, area delta "
                        + "%+.2f A^2%n",
                pocketB.mouthWidth() - pocketA.mouthWidth(),
                pocketB.mouthArea() - pocketA.mouthArea()
        ));
        out.append(String.format(
                "  deeper interior: depth delta %+.2f A%n",
                pocketB.cavityDepth() - pocketA.cavityDepth()
        ));
        out.append(String.format(
                "  different bottleneck: radius delta %+.2f A%n",
                pocketB.bottleneckRadius() - pocketA.bottleneckRadius()
        ));

        if (wall.maxDisplacementResidueA() == null) {
            out.append("  shifted wall: no pocket-residue pairs\n");
        } else {
            out.append(String.format(
                    "  shifted wall: max side-chain displacement "
                            + "%.2f A at %s%s%d (B %s%d)%n",
                    wall.maxSideChainDisplacement(),
                    wall.maxDisplacementResidueA().chainId(),
                    "",
                    wall.maxDisplacementResidueA().residueNumber(),
                    wall.maxDisplacementResidueB().chainId(),
                    wall.maxDisplacementResidueB().residueNumber()
            ));
        }

        if (backbone.caRmsd() < NEGLIGIBLE_BACKBONE_RMSD_ANGSTROMS) {
            out.append(String.format(
                    "  backbone: displacement negligible (CA RMSD "
                            + "%.3f A)%n",
                    backbone.caRmsd()
            ));
        } else {
            out.append(String.format(
                    "  backbone: NOT negligible (CA RMSD %.3f A)%n",
                    backbone.caRmsd()
            ));
        }

        List<BackboneArchitectureComparison.SegmentDisplacement>
                segments = backbone.segmentProfile();
        if (!segments.isEmpty()
                && segments.get(0).meanCaDisplacement()
                        >= LOCALIZED_SEGMENT_DISPLACEMENT_ANGSTROMS) {
            BackboneArchitectureComparison.SegmentDisplacement top =
                    segments.get(0);
            out.append(String.format(
                    "  localization: segment A %d-%d (B %d-%d), mean "
                            + "CA displacement %.2f A%n",
                    top.startResidueA(),
                    top.endResidueA(),
                    top.startResidueB(),
                    top.endResidueB(),
                    top.meanCaDisplacement()
            ));
        } else {
            out.append("  localization: no segment exceeds the "
                    + "displacement threshold\n");
        }
    }

    private void appendLoopRegion(StringBuilder out) {
        out.append(String.format(
                "%nLoop region (%d-%d)%n",
                loopRegion.rangeStart(),
                loopRegion.rangeEnd()
        ));

        if (loopRegion.rows().isEmpty()) {
            out.append("  no aligned residue pairs in the range\n");
        }

        for (LoopRegionAnalysis.LoopRegionResidueRow row
                : loopRegion.rows()) {
            out.append(String.format(
                    "  %s A%d / %s B%d  CA %.2f  backbone %.2f  "
                            + "side-chain %.2f  sc-RMSD %s  poseA %.2f "
                            + "(%s)  poseB %.2f (%s)  wall %s/%s  "
                            + "burial %d/%d  cavity %s  "
                            + "free-volume %+.2f%n",
                    row.residueNameA(),
                    row.residueA().residueNumber(),
                    row.residueNameB(),
                    row.residueB().residueNumber(),
                    row.caDisplacement(),
                    row.backboneDisplacement(),
                    row.sideChainCentroidDisplacement(),
                    row.sideChainRmsd() == null
                            ? "n/a"
                            : String.format("%.2f", row.sideChainRmsd()),
                    row.minDistanceToPoseA(),
                    row.contactA() ? "contact" : "no contact",
                    row.minDistanceToPoseB(),
                    row.contactB() ? "contact" : "no contact",
                    row.pocketWallA() ? "y" : "n",
                    row.pocketWallB() ? "y" : "n",
                    row.burialA(),
                    row.burialB(),
                    row.localCavityDisplacement() == null
                            ? "n/a"
                            : String.format("%.2f",
                                    row.localCavityDisplacement()),
                    row.localFreeVolumeDifference()
            ));
        }

        if (loopRegion.loopCentroidA() != null) {
            out.append(String.format(
                    "  loop CA centroid to pose centroid: A %.2f A  "
                            + "B (aligned) %.2f A%n",
                    loopRegion.poseACentroidToLoopAngstroms(),
                    loopRegion.poseBCentroidToLoopAngstroms()
            ));
        }

        out.append(String.format(
                "  pose displacement toward loop: %+.2f A%n",
                loopRegion.poseDisplacementTowardLoopAngstroms()
        ));
        out.append("  verdict: ")
                .append(loopRegion.verdict())
                .append(" — ")
                .append(loopRegion.reason())
                .append('\n');
    }

    private void appendConsistencyWarning(
            StringBuilder out,
            String label,
            double meanWallDistance
    ) {
        if (meanWallDistance > SPHERE_WALL_CONSISTENCY_ANGSTROMS) {
            out.append(String.format(
                    "  WARNING: pocket %s's spheres are inconsistent "
                            + "with its pocket wall (mean "
                            + "sphere-to-wall distance %.2f A exceeds "
                            + "%.2f A); sphere-derived metrics for "
                            + "this pocket are unreliable%n",
                    label,
                    meanWallDistance,
                    SPHERE_WALL_CONSISTENCY_ANGSTROMS
            ));
        }
    }

    private static void appendArchitecture(
            StringBuilder out,
            String label,
            PocketArchitecture architecture
    ) {
        out.append(String.format(
                "%nPocket %s architecture%n",
                label
        ));
        out.append(String.format(
                "  spheres: %d  mean radius %.2f A%n",
                architecture.alphaSphereCount(),
                architecture.meanSphereRadius()
        ));
        out.append(String.format(
                "  extents along axes: %.2f / %.2f / %.2f A%n",
                architecture.extentsAlongAxes().get(0),
                architecture.extentsAlongAxes().get(1),
                architecture.extentsAlongAxes().get(2)
        ));
        out.append(String.format(
                "  cavity depth %.2f A  mouth width %.2f A  mouth "
                        + "area %.2f A^2  bottleneck %.2f A%n",
                architecture.cavityDepth(),
                architecture.mouthWidth(),
                architecture.mouthArea(),
                architecture.bottleneckRadius()
        ));
        out.append(String.format(
                "  reported volume %s  reported SASA %s%n",
                architecture.reportedVolume() == null
                        ? "n/a"
                        : String.format("%.1f A^3",
                                architecture.reportedVolume()),
                architecture.reportedTotalSasa() == null
                        ? "n/a"
                        : String.format("%.1f A^2",
                                architecture.reportedTotalSasa())
        ));
    }
}
