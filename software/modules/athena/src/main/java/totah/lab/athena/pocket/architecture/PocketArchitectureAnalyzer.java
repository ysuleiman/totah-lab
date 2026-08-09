package totah.lab.athena.pocket.architecture;

import totah.lab.athena.pocket.compare.PocketAlignmentResult;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.Objects;

/**
 * Facade for the pocket-architecture comparison: runs the pocket
 * alignment once and assembles the full
 * {@link PocketArchitectureReport} from two receptors, their pockets
 * and their docked poses. Ligand contacts are not required — every
 * metric here is geometric.
 */
public final class PocketArchitectureAnalyzer {

    private final AlphaSphereArchitectureAnalyzer sphereAnalyzer;
    private final BackboneArchitectureAnalyzer backboneAnalyzer;
    private final LigandSpaceComparator ligandSpaceComparator;
    private final WallGeometryAnalyzer wallAnalyzer;
    private final LoopRegionAnalyzer loopRegionAnalyzer;

    public PocketArchitectureAnalyzer() {
        this(
                new AlphaSphereArchitectureAnalyzer(),
                new BackboneArchitectureAnalyzer(),
                new LigandSpaceComparator(),
                new WallGeometryAnalyzer(),
                new LoopRegionAnalyzer()
        );
    }

    public PocketArchitectureAnalyzer(
            AlphaSphereArchitectureAnalyzer sphereAnalyzer,
            BackboneArchitectureAnalyzer backboneAnalyzer,
            LigandSpaceComparator ligandSpaceComparator,
            WallGeometryAnalyzer wallAnalyzer,
            LoopRegionAnalyzer loopRegionAnalyzer
    ) {
        this.sphereAnalyzer = Objects.requireNonNull(
                sphereAnalyzer,
                "sphereAnalyzer"
        );
        this.backboneAnalyzer = Objects.requireNonNull(
                backboneAnalyzer,
                "backboneAnalyzer"
        );
        this.ligandSpaceComparator = Objects.requireNonNull(
                ligandSpaceComparator,
                "ligandSpaceComparator"
        );
        this.wallAnalyzer = Objects.requireNonNull(
                wallAnalyzer,
                "wallAnalyzer"
        );
        this.loopRegionAnalyzer = Objects.requireNonNull(
                loopRegionAnalyzer,
                "loopRegionAnalyzer"
        );
    }

    public PocketArchitectureReport analyze(
            Structure receptorA,
            Pocket pocketA,
            Ligand poseA,
            Structure receptorB,
            Pocket pocketB,
            Ligand poseB
    ) {
        Objects.requireNonNull(receptorA, "receptorA");
        Objects.requireNonNull(pocketA, "pocketA");
        Objects.requireNonNull(poseA, "poseA");
        Objects.requireNonNull(receptorB, "receptorB");
        Objects.requireNonNull(pocketB, "pocketB");
        Objects.requireNonNull(poseB, "poseB");

        PocketArchitecture architectureA =
                PocketArchitecture.of(pocketA);
        PocketArchitecture architectureB =
                PocketArchitecture.of(pocketB);

        PocketAlignmentResult alignment =
                PocketArchitectureSupport.alignPockets(
                        receptorA,
                        pocketA,
                        receptorB,
                        pocketB
                );

        AlphaSphereArchitectureComparison spheres =
                sphereAnalyzer.compareAligned(
                        receptorA,
                        pocketA,
                        receptorB,
                        pocketB,
                        alignment
                );

        BackboneArchitectureComparison backbone =
                backboneAnalyzer.compare(
                        receptorA,
                        pocketA,
                        receptorB,
                        pocketB
                );

        LigandSpaceComparison ligandSpace =
                ligandSpaceComparator.compare(
                        receptorA,
                        pocketA,
                        poseA,
                        receptorB,
                        pocketB,
                        poseB,
                        architectureA,
                        architectureB,
                        alignment.alignment().transform(),
                        spheres.componentsA()
                );

        // The wall comparison uses the RECEPTOR-backbone transform,
        // not the pocket-sphere alignment transform: side-chain
        // displacements must be measured in the same frame as the CA
        // displacements, and the sphere-derived frame is unreliable
        // when the two sphere sets differ substantially or the
        // spheres and residues originate from different coordinate
        // artifacts.
        WallGeometryComparison wall = wallAnalyzer.compare(
                receptorA,
                pocketA,
                receptorB,
                pocketB,
                backbone.transformBtoA()
        );

        // The loop-region analysis shares the backbone frame.
        LoopRegionAnalysis loopRegion = loopRegionAnalyzer.analyze(
                receptorA,
                receptorB,
                backbone.transformBtoA(),
                poseA,
                poseB,
                pocketA,
                pocketB
        );

        return new PocketArchitectureReport(
                architectureA,
                architectureB,
                backbone,
                spheres,
                ligandSpace,
                wall,
                loopRegion
        );
    }
}
