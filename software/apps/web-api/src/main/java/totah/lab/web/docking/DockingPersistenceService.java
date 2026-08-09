package totah.lab.web.docking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.daedalus.docking.PocketGridBox;
import totah.lab.daedalus.docking.VinaDockingOptions;
import totah.lab.daedalus.docking.VinaPose;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.web.docking.PoseContactCalculator.PocketAtomPoint;
import totah.lab.web.docking.PoseContactCalculator.ResidueContact;
import totah.lab.web.persistence.ArtifactEntity;
import totah.lab.web.persistence.ArtifactRepository;
import totah.lab.web.persistence.DockingPoseEntity;
import totah.lab.web.persistence.DockingPoseRepository;
import totah.lab.web.persistence.DockingRunEntity;
import totah.lab.web.persistence.DockingRunRepository;
import totah.lab.web.persistence.PipelineRunEntity;
import totah.lab.web.persistence.PocketAtomEntity;
import totah.lab.web.persistence.PocketEntity;
import totah.lab.web.persistence.PocketRepository;
import totah.lab.web.persistence.PocketResidueEntity;
import totah.lab.web.persistence.PoseResidueContactEntity;
import totah.lab.web.persistence.PoseResidueContactRepository;
import totah.lab.web.persistence.StructureEntity;
import totah.lab.web.persistence.StructureRepository;
import totah.lab.web.persistence.TargetEntity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Database side of the docking persistence runner. All writes of one
 * run happen here in a single transaction after vina execution and
 * pose parsing succeeded, so a failed run never leaves partial rows.
 */
@Service
public class DockingPersistenceService {

    /** Artifact label for the vina pose PDBQT of a persisted run. */
    public static final String POSES_ARTIFACT_LABEL = "VINA_POSES";

    /** Everything the runner collected for one executed vina run. */
    public record PersistRequest(
            long structureId,
            long pocketId,
            VinaDockingOptions box,
            String vinaVersion,
            String sourceSystem,
            String ligandId,
            String ligandLabel,
            Path poseFile,
            List<VinaPose> poses,
            List<List<ResidueContact>> contactsPerPose
    ) {
        public PersistRequest {
            Objects.requireNonNull(box, "box");
            Objects.requireNonNull(poseFile, "poseFile");
            poses = List.copyOf(Objects.requireNonNull(poses, "poses"));
            contactsPerPose = List.copyOf(Objects.requireNonNull(
                    contactsPerPose, "contactsPerPose"));
            if (poses.size() != contactsPerPose.size()) {
                throw new IllegalArgumentException(
                        "poses and contactsPerPose must align one-to-one");
            }
            if (poses.isEmpty()) {
                throw new IllegalArgumentException(
                        "a run without poses cannot be persisted");
            }
        }
    }

    public record PersistResult(
            long runId,
            long artifactId,
            int poseCount,
            int contactCount
    ) {
    }

    private final StructureRepository structureRepository;
    private final PocketRepository pocketRepository;
    private final ArtifactRepository artifactRepository;
    private final DockingRunRepository dockingRunRepository;
    private final DockingPoseRepository dockingPoseRepository;
    private final PoseResidueContactRepository contactRepository;

    public DockingPersistenceService(
            StructureRepository structureRepository,
            PocketRepository pocketRepository,
            ArtifactRepository artifactRepository,
            DockingRunRepository dockingRunRepository,
            DockingPoseRepository dockingPoseRepository,
            PoseResidueContactRepository contactRepository
    ) {
        this.structureRepository =
                Objects.requireNonNull(structureRepository);
        this.pocketRepository = Objects.requireNonNull(pocketRepository);
        this.artifactRepository =
                Objects.requireNonNull(artifactRepository);
        this.dockingRunRepository =
                Objects.requireNonNull(dockingRunRepository);
        this.dockingPoseRepository =
                Objects.requireNonNull(dockingPoseRepository);
        this.contactRepository =
                Objects.requireNonNull(contactRepository);
    }

    /**
     * Derives the vina search box from the pocket geometry: alpha
     * spheres preferred (center + extent + radii), pocket atoms
     * otherwise — the same rule as daedalus' PocketGridBoxLoader.
     */
    @Transactional(readOnly = true)
    public VinaDockingOptions deriveBox(
            long structureId, long pocketId, double padding) {
        PocketEntity pocket = loadPocket(structureId, pocketId);

        if (!pocket.getAlphaSpheres().isEmpty()) {
            List<Point3D> centers = new ArrayList<>();
            List<Double> radii = new ArrayList<>();
            pocket.getAlphaSpheres().forEach(sphere -> {
                centers.add(new Point3D(sphere.getCenterX(),
                        sphere.getCenterY(), sphere.getCenterZ()));
                radii.add(sphere.getRadius());
            });
            return PocketGridBox.fromPoints(centers, radii, padding)
                    .toVinaOptions();
        }

        List<Point3D> atoms = new ArrayList<>();
        for (PocketResidueEntity residue : pocket.getResidues()) {
            for (PocketAtomEntity atom : residue.getAtoms()) {
                atoms.add(new Point3D(
                        atom.getX(), atom.getY(), atom.getZ()));
            }
        }
        if (atoms.isEmpty()) {
            throw new IllegalStateException("Pocket " + pocketId
                    + " has no alpha spheres and no pocket atoms;"
                    + " a grid box cannot be derived");
        }
        return PocketGridBox.fromPoints(atoms, null, padding)
                .toVinaOptions();
    }

    /** Pocket atoms with their canonical residue ids. */
    @Transactional(readOnly = true)
    public List<PocketAtomPoint> pocketAtomPoints(long pocketId) {
        PocketEntity pocket = pocketRepository.findById(pocketId)
                .orElseThrow(() -> new IllegalStateException(
                        "Pocket not found: " + pocketId));
        List<PocketAtomPoint> points = new ArrayList<>();
        for (PocketResidueEntity residue : pocket.getResidues()) {
            long residueId = residue.getResidue().getId();
            for (PocketAtomEntity atom : residue.getAtoms()) {
                points.add(new PocketAtomPoint(
                        atom.getX(), atom.getY(), atom.getZ(),
                        residueId));
            }
        }
        return List.copyOf(points);
    }

    @Transactional
    public PersistResult persist(PersistRequest request) {
        StructureEntity structure = structureRepository
                .findById(request.structureId())
                .orElseThrow(() -> new IllegalStateException(
                        "Structure not found: " + request.structureId()));
        loadPocket(request.structureId(), request.pocketId());

        // Reuse the structure import's target and FINISHED pipeline
        // run, per the AlphaFold find-or-create idiom.
        TargetEntity target = structure.getArtifact().getTarget();
        PipelineRunEntity pipelineRun =
                structure.getArtifact().getPipelineRun();

        Path poseFile = request.poseFile().toAbsolutePath().normalize();
        ArtifactEntity posesArtifact = artifactRepository.save(
                new ArtifactEntity(
                        poseFile.getFileName().toString(),
                        POSES_ARTIFACT_LABEL,
                        poseFile.toString(),
                        pipelineRun,
                        target));

        VinaDockingOptions box = request.box();
        DockingRunEntity run = dockingRunRepository.save(
                new DockingRunEntity(
                        structure.getReceptor(),
                        structure,
                        box.centerX(), box.centerY(), box.centerZ(),
                        box.sizeX(), box.sizeY(), box.sizeZ(),
                        request.vinaVersion(),
                        request.sourceSystem()));

        int contactCount = 0;
        for (int index = 0; index < request.poses().size(); index++) {
            VinaPose pose = request.poses().get(index);
            DockingPoseEntity poseEntity =
                    dockingPoseRepository.saveAndFlush(
                            new DockingPoseEntity(
                                    request.ligandId(),
                                    pose.affinityKcalPerMol(),
                                    poseFile.toString(),
                                    structure.getReceptor().getUniProtId(),
                                    run,
                                    request.sourceSystem(),
                                    request.ligandLabel()));
            for (ResidueContact contact :
                    request.contactsPerPose().get(index)) {
                contactRepository.save(new PoseResidueContactEntity(
                        poseEntity.getId(),
                        contact.residueId(),
                        contact.atomContactCount(),
                        contact.minDistance()));
                contactCount++;
            }
        }

        return new PersistResult(run.getId(), posesArtifact.getId(),
                request.poses().size(), contactCount);
    }

    private PocketEntity loadPocket(long structureId, long pocketId) {
        PocketEntity pocket = pocketRepository.findById(pocketId)
                .orElseThrow(() -> new IllegalStateException(
                        "Pocket not found: " + pocketId));
        if (!pocket.getStructure().getId().equals(structureId)) {
            throw new IllegalStateException("Pocket " + pocketId
                    + " belongs to structure "
                    + pocket.getStructure().getId()
                    + ", not " + structureId);
        }
        return pocket;
    }
}
