package totah.lab.web.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import totah.lab.daedalus.docking.VinaDockingOptions;
import totah.lab.daedalus.docking.VinaPose;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.web.docking.DockingPersistenceRunner;
import totah.lab.web.docking.DockingPersistenceService;
import totah.lab.web.docking.PoseContactCalculator.ResidueContact;
import totah.lab.web.persistence.ArtifactEntity;
import totah.lab.web.persistence.ArtifactRepository;
import totah.lab.web.persistence.DockingPoseEntity;
import totah.lab.web.persistence.DockingPoseRepository;
import totah.lab.web.persistence.DockingRunEntity;
import totah.lab.web.persistence.DockingRunRepository;
import totah.lab.web.persistence.PipelineRunEntity;
import totah.lab.web.persistence.PipelineRunRepository;
import totah.lab.web.persistence.PocketAtomEntity;
import totah.lab.web.persistence.PocketEntity;
import totah.lab.web.persistence.PocketRepository;
import totah.lab.web.persistence.PocketResidueEntity;
import totah.lab.web.persistence.PoseResidueContactEntity;
import totah.lab.web.persistence.PoseResidueContactRepository;
import totah.lab.web.persistence.ReceptorEntity;
import totah.lab.web.persistence.ReceptorRepository;
import totah.lab.web.persistence.ResidueEntity;
import totah.lab.web.persistence.ResidueRepository;
import totah.lab.web.persistence.StructureEntity;
import totah.lab.web.persistence.StructureRepository;
import totah.lab.web.persistence.TargetEntity;
import totah.lab.web.persistence.TargetRepository;
import totah.lab.web.service.DockingTestSchemaSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of the docking persistence runner against the
 * docking_test schema: a fake vina executable produces a two-pose run
 * over a fixture pocket with known geometry, so the expected box,
 * scores and contact rows are all computable by hand.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=docking_test"
})
class DockingPersistenceIntegrationTest extends DockingTestSchemaSupport {

    static {
        recreateTestSchema();
    }

    @TempDir
    Path temporaryDirectory;

    @Autowired
    private DockingPersistenceService persistenceService;
    @Autowired
    private DockingRunRepository dockingRunRepository;
    @Autowired
    private DockingPoseRepository dockingPoseRepository;
    @Autowired
    private PoseResidueContactRepository contactRepository;
    @Autowired
    private StructureRepository structureRepository;
    @Autowired
    private PocketRepository pocketRepository;
    @Autowired
    private ArtifactRepository artifactRepository;
    @Autowired
    private ReceptorRepository receptorRepository;
    @Autowired
    private ResidueRepository residueRepository;
    @Autowired
    private TargetRepository targetRepository;
    @Autowired
    private PipelineRunRepository pipelineRunRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private StructureEntity structure;
    private PocketEntity pocket;
    private ResidueEntity nearResidue;
    private ResidueEntity farResidue;
    private Path receptorPdbqt;
    private Path ligandPdbqt;
    private Path fakeVina;

    @BeforeEach
    void seedFixture() throws Exception {
        TargetEntity target = targetRepository.save(
                new TargetEntity("METTL7B", "Q6UX53"));
        PipelineRunEntity pipelineRun = pipelineRunRepository.save(
                PipelineRunEntity.finishedNow());
        ArtifactEntity structureArtifact = artifactRepository.save(
                new ArtifactEntity("structure.pdb", "RAW_PDB_FILE",
                        "/import/structure.pdb", pipelineRun, target));
        ReceptorEntity receptor = new ReceptorEntity();
        receptor.setTargetName("METTL7B");
        receptor.setUniProtId("Q6UX53");
        receptor = receptorRepository.save(receptor);

        structure = new StructureEntity();
        structure.setReceptor(receptor);
        structure.setSource("ALPHAFOLD");
        structure.setSourceAccession("AF-Q6UX53-F1-model_v6");
        structure.setArtifact(structureArtifact);
        structure = structureRepository.save(structure);

        nearResidue = residueRepository.save(new ResidueEntity(
                structure, "A", 100, "", "CYS"));
        farResidue = residueRepository.save(new ResidueEntity(
                structure, "A", 101, "", "SER"));

        pocket = new PocketEntity();
        pocket.setStructure(structure);
        pocket.setArtifact(structureArtifact);
        pocket.setPocketNumber(1);
        pocket.setSource(PocketSource.FPOCKET);
        addMembership(pocket, nearResidue, 0.0, 0.0, 0.0);
        addMembership(pocket, farResidue, 10.0, 0.0, 0.0);
        pocket = pocketRepository.save(pocket);

        receptorPdbqt = temporaryDirectory.resolve("receptor.pdbqt");
        Files.writeString(receptorPdbqt, "RECEPTOR\n");
        ligandPdbqt = temporaryDirectory.resolve("ligand.pdbqt");
        Files.writeString(ligandPdbqt, "LIGAND\n");
        fakeVina = fakeVina(0);
    }

    @AfterEach
    void truncateTestSchema() {
        jdbc.execute("TRUNCATE docking_test.pose_residue_contact,"
                + " docking_test.docking_pose, docking_test.docking_run,"
                + " docking_test.pocket_atom,"
                + " docking_test.pocket_residue, docking_test.pocket,"
                + " docking_test.residue, docking_test.structure,"
                + " docking_test.artifacts, docking_test.receptor,"
                + " docking_test.targets,"
                + " docking_test.pipeline_runs"
                + " RESTART IDENTITY CASCADE");
    }

    @Test
    void persistsRunPosesContactsAndArtifact() throws Exception {
        runner(false).run();

        List<DockingRunEntity> runs = dockingRunRepository.findAll();
        assertEquals(1, runs.size());
        DockingRunEntity run = runs.get(0);
        assertEquals(structure.getId(), run.getStructure().getId());
        assertEquals(5.0, run.getGridCenterX(), 1.0e-9);
        assertEquals(26.0, run.getGridSizeX(), 1.0e-9);
        assertEquals(16.0, run.getGridSizeY(), 1.0e-9);
        assertEquals("web-api", run.getSourceSystem());
        assertTrue(run.getVinaVersion().startsWith("AutoDock Vina"));

        List<DockingPoseEntity> poses = dockingPoseRepository.findAll()
                .stream()
                .sorted(Comparator.comparingDouble(
                        DockingPoseEntity::getVinaScore))
                .toList();
        assertEquals(2, poses.size());
        assertEquals(-7.5, poses.get(0).getVinaScore(), 1.0e-9);
        assertEquals(-6.1, poses.get(1).getVinaScore(), 1.0e-9);
        assertEquals("ligand", poses.get(0).getLigandId());
        assertEquals("Q6UX53", poses.get(0).getReceptorId());
        String poseFile = poses.get(0).getPoseFile();
        assertTrue(poseFile.endsWith("ligand_out.pdbqt"), poseFile);
        assertTrue(Path.of(poseFile).isAbsolute(), poseFile);
        assertEquals(poseFile, poses.get(1).getPoseFile());

        List<PoseResidueContactEntity> contacts =
                contactRepository.findAll();
        assertEquals(2, contacts.size());
        PoseResidueContactEntity bestContact = contacts.stream()
                .filter(c -> c.getPoseId() == poses.get(0).getId())
                .findFirst().orElseThrow();
        assertEquals(nearResidue.getId(), bestContact.getResidueId());
        assertEquals(2, bestContact.getAtomContactCount());
        assertEquals(1.0, bestContact.getMinDistance(), 1.0e-9);
        PoseResidueContactEntity secondContact = contacts.stream()
                .filter(c -> c.getPoseId() == poses.get(1).getId())
                .findFirst().orElseThrow();
        assertEquals(farResidue.getId(), secondContact.getResidueId());
        assertEquals(1, secondContact.getAtomContactCount());

        List<ArtifactEntity> artifacts = artifactRepository.findAll()
                .stream()
                .filter(a -> DockingPersistenceService
                        .POSES_ARTIFACT_LABEL.equals(a.getLabel()))
                .toList();
        assertEquals(1, artifacts.size());
        ArtifactEntity posesArtifact = artifacts.get(0);
        assertEquals("ligand_out.pdbqt", posesArtifact.getFilename());
        assertEquals(poseFile, posesArtifact.getStorageLocation());
        assertEquals(structure.getArtifact().getTarget().getId(),
                posesArtifact.getTarget().getId());
        assertEquals(structure.getArtifact().getPipelineRun().getId(),
                posesArtifact.getPipelineRun().getId());
    }

    @Test
    void dryRunWritesNothing() throws Exception {
        runner(true).run();

        assertEquals(0, dockingRunRepository.count());
        assertEquals(0, dockingPoseRepository.count());
        assertEquals(0, contactRepository.count());
        assertEquals(List.of(), artifactRepository.findAll().stream()
                .filter(a -> DockingPersistenceService
                        .POSES_ARTIFACT_LABEL.equals(a.getLabel()))
                .toList());
    }

    @Test
    void vinaFailureLeavesNoRows() throws Exception {
        DockingPersistenceRunner failing = runner(fakeVina(3), false);

        assertThrows(IllegalStateException.class, failing::run);
        assertEquals(0, dockingRunRepository.count());
        assertEquals(0, dockingPoseRepository.count());
        assertEquals(0, contactRepository.count());
    }

    @Test
    void writeFailureRollsBackTheWholeRun() {
        long artifactsBefore = artifactRepository.count();
        String tooLongLigandId = "L".repeat(40);

        DockingPersistenceService.PersistRequest request =
                new DockingPersistenceService.PersistRequest(
                        structure.getId(),
                        pocket.getId(),
                        VinaDockingOptions.ofBox(
                                5.0, 0.0, 0.0, 26.0, 16.0, 16.0),
                        "AutoDock Vina v0.0-test",
                        "web-api",
                        tooLongLigandId,
                        null,
                        ligandPdbqt,
                        List.of(new VinaPose(1, -7.5, 0.0, 0.0)),
                        List.of(List.of(new ResidueContact(
                                nearResidue.getId(), 2, 1.0))));

        assertThrows(DataIntegrityViolationException.class,
                () -> persistenceService.persist(request));
        assertEquals(0, dockingRunRepository.count());
        assertEquals(0, dockingPoseRepository.count());
        assertEquals(0, contactRepository.count());
        assertEquals(artifactsBefore, artifactRepository.count());
    }

    // --- fixtures ------------------------------------------------------

    private DockingPersistenceRunner runner(boolean dryRun) {
        return runner(fakeVina, dryRun);
    }

    private DockingPersistenceRunner runner(Path vina, boolean dryRun) {
        return new DockingPersistenceRunner(
                persistenceService,
                receptorPdbqt.toString(),
                ligandPdbqt.toString(),
                structure.getId().toString(),
                pocket.getId().toString(),
                "",
                8.0,
                vina.toString(),
                temporaryDirectory.resolve("runs").toString(),
                8,
                "",
                "",
                "",
                dryRun);
    }

    /*
     * Fake vina: writes the two-model pose file next to the --ligand
     * argument (mirroring vina's default-out behavior) and prints the
     * pose table. Model 1 sits on the near residue's atom, model 2 on
     * the far residue's atom.
     */
    private Path fakeVina(int exitCode) throws IOException {
        String outContent = String.join("\n",
                "MODEL 1",
                "ROOT",
                atomLine(1, "C1", 1.0, 0.0, 0.0),
                atomLine(2, "C2", 4.0, 0.0, 0.0),
                "ENDROOT",
                "ENDMDL",
                "MODEL 2",
                "ROOT",
                atomLine(1, "C1", 9.0, 0.0, 0.0),
                "ENDROOT",
                "ENDMDL");
        Path script = temporaryDirectory.resolve("fake-vina-" + exitCode);
        Files.writeString(script, """
                #!/bin/bash
                while [ $# -gt 0 ]; do
                  case "$1" in
                    --ligand) lig="$2"; shift 2;;
                    *) shift;;
                  esac
                done
                out="${lig%%.pdbqt}_out.pdbqt"
                cat > "$out" <<'POSES'
                %s
                POSES
                echo "AutoDock Vina v1.2.5-fake"
                echo "   1        -7.5      0.000      0.000"
                echo "   2        -6.1      1.000      2.000"
                exit %d
                """.formatted(outContent, exitCode));
        Files.setPosixFilePermissions(script,
                PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    private static String atomLine(
            int serial, String name, double x, double y, double z) {
        return String.format(Locale.ROOT,
                "ATOM  %5d %-4s UNL  %4d    %8.3f%8.3f%8.3f"
                        + "  1.00  0.00    %+6.3f %s",
                serial, name, 1, x, y, z, 0.0, "C");
    }

    private static void addMembership(
            PocketEntity pocket, ResidueEntity residue,
            double x, double y, double z) {
        PocketResidueEntity membership = new PocketResidueEntity();
        membership.setResidue(residue);
        membership.setChain(residue.getChain());
        membership.setResidueNumber(residue.getResidueNumber());
        membership.setResidueName(residue.getResidueName());
        PocketAtomEntity atom = new PocketAtomEntity();
        atom.setAtomName("CA");
        atom.setX(x);
        atom.setY(y);
        atom.setZ(z);
        atom.setElement("C");
        membership.addAtom(atom);
        pocket.addResidue(membership);
    }
}
