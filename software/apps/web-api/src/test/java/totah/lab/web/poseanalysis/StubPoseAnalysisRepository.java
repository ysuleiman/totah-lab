package totah.lab.web.poseanalysis;

import totah.lab.web.service.PocketAlphaSphereProjection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-written in-memory {@link PoseAnalysisRepository} for the
 * pocket-assignment tests: poses, runs, per-run pose lists and the
 * pocket/sphere/residue tables of each structure, all keyed maps the
 * tests fill directly. Lookup methods not exercised by the assignment
 * analyses return empty results.
 */
final class StubPoseAnalysisRepository implements PoseAnalysisRepository {

    final Map<Long, PoseProjection> poses = new HashMap<>();
    final Map<Long, PoseRunProjection> runs = new HashMap<>();
    final Map<Long, Long> runByPose = new HashMap<>();
    final Map<Long, String> ligandByPose = new HashMap<>();
    final Map<Long, List<PoseProjection>> posesByRun = new HashMap<>();
    final Map<Long, List<PoseRunProjection>> runsByReceptor =
            new HashMap<>();
    final Map<Long, List<PosePocketProjection>> pockets =
            new HashMap<>();
    final Map<Long, List<PocketAlphaSphereProjection>> spheres =
            new HashMap<>();
    final Map<Long, List<PosePocketResidueProjection>> residues =
            new HashMap<>();
    final Map<Long, StructureArtifactProjection> structureArtifacts =
            new HashMap<>();

    /**
     * Registers the structure artifact (the model a structure's pocket
     * rows were generated from) for frame-provenance validation.
     * Pointing it at the same file as the run's receptor artifact
     * yields IDENTICAL_ARTIFACT.
     */
    void addStructureArtifact(
            long structureId,
            long artifactId,
            String accession,
            String storageLocation
    ) {
        structureArtifacts.put(structureId, new StructureArtifactProjection() {
            @Override
            public Long getStructureId() {
                return structureId;
            }

            @Override
            public Long getArtifactId() {
                return artifactId;
            }

            @Override
            public String getArtifactFilename() {
                return storageLocation;
            }

            @Override
            public String getArtifactStorageLocation() {
                return storageLocation;
            }

            @Override
            public String getStructureSource() {
                return "ALPHAFOLD";
            }

            @Override
            public String getSourceAccession() {
                return accession;
            }
        });
    }

    void addPose(
            long runId,
            long poseId,
            String label,
            double vinaScore,
            String poseFile
    ) {
        addPose(runId, poseId, "LIG", label, vinaScore, poseFile);
    }

    void addPose(
            long runId,
            long poseId,
            String ligandId,
            String label,
            double vinaScore,
            String poseFile
    ) {
        PoseProjection pose = pose(poseId, label, vinaScore, poseFile);
        poses.put(poseId, pose);
        runByPose.put(poseId, runId);
        ligandByPose.put(poseId, ligandId);
        posesByRun.computeIfAbsent(runId, key -> new java.util.ArrayList<>())
                .add(pose);
    }

    void addRun(
            long runId,
            long structureId,
            String receptorArtifactId,
            String targetName
    ) {
        addRun(runId, 1L, structureId, receptorArtifactId, targetName);
    }

    void addRun(
            long runId,
            long receptorId,
            long structureId,
            String receptorArtifactId,
            String targetName
    ) {
        PoseRunProjection previous = runs.get(runId);
        if (previous != null) {
            runsByReceptor.getOrDefault(
                    previous.getReceptorId(),
                    List.of()
            ).removeIf(run -> run.getId() == runId);
        }
        PoseRunProjection run = run(
                runId,
                receptorId,
                structureId,
                receptorArtifactId,
                targetName
        );
        runs.put(runId, run);
        runsByReceptor.computeIfAbsent(
                receptorId,
                key -> new java.util.ArrayList<>()
        ).add(run);
    }

    static PoseProjection pose(
            long id,
            String label,
            double vinaScore,
            String poseFile
    ) {
        return new PoseProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getLigandLabel() {
                return label;
            }

            @Override
            public Double getVinaScore() {
                return vinaScore;
            }

            @Override
            public String getPoseFile() {
                return poseFile;
            }
        };
    }

    static PoseRunProjection run(
            long id,
            long structureId,
            String receptorArtifactId,
            String targetName
    ) {
        return run(id, 1L, structureId, receptorArtifactId, targetName);
    }

    static PoseRunProjection run(
            long id,
            long receptorId,
            long structureId,
            String receptorArtifactId,
            String targetName
    ) {
        return new PoseRunProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Long getReceptorId() {
                return receptorId;
            }

            @Override
            public Long getStructureId() {
                return structureId;
            }

            @Override
            public String getTargetName() {
                return targetName;
            }

            @Override
            public String getUniProtId() {
                return "Q6UX53";
            }

            @Override
            public String getMethod() {
                return "vina";
            }

            @Override
            public String getReceptorArtifactId() {
                return receptorArtifactId;
            }

            @Override
            public Long getPoseCount() {
                return null;
            }

            @Override
            public Double getBestScore() {
                return null;
            }
        };
    }

    static PosePocketProjection pocket(
            long id,
            int pocketNumber,
            String source
    ) {
        return new PosePocketProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Integer getPocketNumber() {
                return pocketNumber;
            }

            @Override
            public String getSource() {
                return source;
            }
        };
    }

    static PocketAlphaSphereProjection sphere(
            long pocketId,
            int sphereIndex,
            double x,
            double y,
            double z,
            double radius
    ) {
        return new PocketAlphaSphereProjection() {
            @Override
            public Long getPocketId() {
                return pocketId;
            }

            @Override
            public Integer getSphereIndex() {
                return sphereIndex;
            }

            @Override
            public Double getCenterX() {
                return x;
            }

            @Override
            public Double getCenterY() {
                return y;
            }

            @Override
            public Double getCenterZ() {
                return z;
            }

            @Override
            public Double getRadius() {
                return radius;
            }
        };
    }

    static PosePocketResidueProjection pocketResidue(
            long pocketId,
            String chain,
            int residueNumber,
            String residueName
    ) {
        return new PosePocketResidueProjection() {
            @Override
            public Long getPocketId() {
                return pocketId;
            }

            @Override
            public String getChain() {
                return chain;
            }

            @Override
            public Integer getResidueNumber() {
                return residueNumber;
            }

            @Override
            public String getInsertionCode() {
                return null;
            }

            @Override
            public String getResidueName() {
                return residueName;
            }
        };
    }

    @Override
    public List<DockingTargetProjection> findDockingTargets() {
        return List.of();
    }

    @Override
    public List<LigandOptionProjection> findLigandsForReceptor(
            long receptorId, String query, int limit) {
        return List.of();
    }

    @Override
    public Optional<LigandOptionProjection> findLigandOption(
            long receptorId, String ligandId) {
        return Optional.empty();
    }

    @Override
    public List<LigandRunOptionProjection> findLigandRunsForReceptor(
            long receptorId, String query, int limit) {
        return List.of();
    }

    @Override
    public List<PoseRunProjection> findRunsForLigand(
            long receptorId, String ligandId) {
        List<PoseRunProjection> result = new java.util.ArrayList<>();
        for (PoseRunProjection run
                : runsByReceptor.getOrDefault(receptorId, List.of())) {
            List<PoseProjection> runPoses = findPoses(run.getId(), ligandId);
            if (runPoses.isEmpty()) {
                continue;
            }
            double bestScore = runPoses.stream()
                    .mapToDouble(PoseProjection::getVinaScore)
                    .min()
                    .orElse(0.0);
            result.add(withScores(run, runPoses.size(), bestScore));
        }
        return List.copyOf(result);
    }

    /** The run projection with its per-ligand pose count/best score. */
    private static PoseRunProjection withScores(
            PoseRunProjection run,
            long poseCount,
            double bestScore
    ) {
        return new PoseRunProjection() {
            @Override
            public Long getId() {
                return run.getId();
            }

            @Override
            public Long getReceptorId() {
                return run.getReceptorId();
            }

            @Override
            public Long getStructureId() {
                return run.getStructureId();
            }

            @Override
            public String getTargetName() {
                return run.getTargetName();
            }

            @Override
            public String getUniProtId() {
                return run.getUniProtId();
            }

            @Override
            public String getMethod() {
                return run.getMethod();
            }

            @Override
            public String getReceptorArtifactId() {
                return run.getReceptorArtifactId();
            }

            @Override
            public Long getPoseCount() {
                return poseCount;
            }

            @Override
            public Double getBestScore() {
                return bestScore;
            }
        };
    }

    @Override
    public List<PoseProjection> findPoses(long runId, String ligandId) {
        return posesByRun.getOrDefault(runId, List.of()).stream()
                .filter(pose -> ligandId.equals(
                        ligandByPose.get(pose.getId())))
                .sorted(java.util.Comparator
                        .comparingDouble(PoseProjection::getVinaScore)
                        .thenComparing(PoseProjection::getId))
                .toList();
    }

    @Override
    public Optional<PoseProjection> findPose(long poseId) {
        return Optional.ofNullable(poses.get(poseId));
    }

    @Override
    public Optional<PoseRunProjection> findRun(long runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public Optional<PoseRunProjection> findRunForPose(long poseId) {
        Long runId = runByPose.get(poseId);
        return runId == null
                ? Optional.empty()
                : Optional.ofNullable(runs.get(runId));
    }

    @Override
    public Optional<String> findSiblingReceptorArtifactId(long receptorId) {
        return Optional.empty();
    }

    @Override
    public List<String> findBiohubArtifactLocations(long receptorId) {
        return List.of();
    }

    @Override
    public List<PoseProjection> findPosesByRunId(long runId) {
        return posesByRun.getOrDefault(runId, List.of());
    }

    @Override
    public List<PosePocketProjection> findPocketsByStructureId(
            long structureId) {
        return pockets.getOrDefault(structureId, List.of());
    }

    @Override
    public List<PocketAlphaSphereProjection> findAlphaSpheresByStructureId(
            long structureId) {
        return spheres.getOrDefault(structureId, List.of());
    }

    @Override
    public List<PosePocketResidueProjection>
            findPocketResiduesByStructureId(long structureId) {
        return residues.getOrDefault(structureId, List.of());
    }

    @Override
    public Optional<StructureArtifactProjection> findStructureArtifact(
            long structureId) {
        return Optional.ofNullable(structureArtifacts.get(structureId));
    }

    @Override
    public List<StructureArtifactProjection>
            findPocketedStructureArtifacts() {
        return List.copyOf(structureArtifacts.values());
    }
}
