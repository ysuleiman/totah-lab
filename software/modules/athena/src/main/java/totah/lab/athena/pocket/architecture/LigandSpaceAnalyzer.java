package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Computes the per-atom {@link LigandSpaceAnalysis} of a pose in its
 * receptor and pocket. Deterministic: the free-volume shell uses a
 * fixed 14-direction sampling pattern, no randomness.
 */
public final class LigandSpaceAnalyzer {

    private static final Set<String> BACKBONE_ATOMS =
            Set.of("N", "CA", "C", "O", "OXT");

    private final LigandSpaceOptions options;

    public LigandSpaceAnalyzer() {
        this(LigandSpaceOptions.defaults());
    }

    public LigandSpaceAnalyzer(LigandSpaceOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public LigandSpaceAnalysis analyze(
            Structure receptor,
            Pocket pocket,
            Ligand pose
    ) {
        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(pose, "pose");

        List<ReceptorAtom> receptorAtoms =
                receptorAtoms(receptor);
        List<AlphaSphere> spheres = pocket.alphaSphereSet()
                .map(AlphaSphereSet::spheres)
                .orElse(List.of());

        List<LigandSpaceAnalysis.LigandAtomSpace> atomSpaces =
                new ArrayList<>();

        double wallDistanceSum = 0.0;
        int wallDistanceCount = 0;

        List<Atom> ligandAtoms = pose.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .toList();

        for (Atom ligandAtom : ligandAtoms) {
            Point3D position = ligandAtom.getPosition();

            double nearestWall = Double.MAX_VALUE;
            double nearestBackbone = Double.MAX_VALUE;
            ResidueId nearestResidue = null;

            for (ReceptorAtom receptorAtom : receptorAtoms) {
                double distance =
                        position.distance(receptorAtom.position());

                if (distance < nearestWall) {
                    nearestWall = distance;
                    nearestResidue = receptorAtom.residue();
                }

                if (receptorAtom.backbone()
                        && distance < nearestBackbone) {
                    nearestBackbone = distance;
                }
            }

            double nearestSphereSurface = Double.MAX_VALUE;
            for (AlphaSphere sphere : spheres) {
                nearestSphereSurface = Math.min(
                        nearestSphereSurface,
                        Math.max(0.0, position.distance(sphere.center())
                                - sphere.radius())
                );
            }

            if (receptorAtoms.isEmpty()) {
                nearestWall = Double.NaN;
                nearestBackbone = Double.NaN;
            }

            if (spheres.isEmpty()) {
                nearestSphereSurface = Double.NaN;
            }

            double shellFree = ShellFreeVolume.freeFraction(
                    position,
                    receptorAtoms.stream()
                            .map(ReceptorAtom::position)
                            .toList(),
                    options.probeRadiusAngstroms()
            );

            atomSpaces.add(new LigandSpaceAnalysis.LigandAtomSpace(
                    ligandAtom.getName(),
                    nearestWall,
                    nearestResidue,
                    nearestBackbone,
                    nearestSphereSurface,
                    shellFree
            ));

            if (!receptorAtoms.isEmpty()) {
                wallDistanceSum += nearestWall;
                wallDistanceCount++;
            }
        }

        return new LigandSpaceAnalysis(
                pose,
                atomSpaces,
                wallDistanceCount == 0
                        ? Double.NaN
                        : wallDistanceSum / wallDistanceCount
        );
    }

    private static List<ReceptorAtom> receptorAtoms(
            Structure receptor
    ) {
        List<ReceptorAtom> atoms = new ArrayList<>();

        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId residueId = new ResidueId(
                        chain.id(),
                        residue.getNumber(),
                        residue.getInsertionCode()
                );

                for (Atom atom : residue.getAtoms()) {
                    if (atom == null || !atom.isHeavyAtom()) {
                        continue;
                    }

                    atoms.add(new ReceptorAtom(
                            atom.getPosition(),
                            residueId,
                            BACKBONE_ATOMS.contains(atom.getName())
                    ));
                }
            }
        }

        return atoms;
    }

    private record ReceptorAtom(
            Point3D position,
            ResidueId residue,
            boolean backbone
    ) {
    }
}
