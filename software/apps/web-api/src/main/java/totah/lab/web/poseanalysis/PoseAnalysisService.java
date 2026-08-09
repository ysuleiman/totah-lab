package totah.lab.web.poseanalysis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.athena.ligand.contact.ContactType;
import totah.lab.athena.ligand.contact.DefaultContactAnalyzer;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.ligand.interaction.DefaultLigandInteractionAnalyzer;
import totah.lab.athena.ligand.interaction.LigandInteraction;
import totah.lab.athena.ligand.pose.AlphaSphereOccupancy;
import totah.lab.athena.ligand.pose.AssignmentStatus;
import totah.lab.athena.ligand.pose.CrossProteinPoseComparator;
import totah.lab.athena.ligand.pose.CrossProteinPoseComparison;
import totah.lab.athena.ligand.pose.DefaultCrossProteinPoseComparator;
import totah.lab.athena.ligand.pose.DefaultPosePocketAssigner;
import totah.lab.athena.ligand.pose.LigandPocketOccupancy;
import totah.lab.athena.ligand.pose.LigandPocketOccupancyAnalyzer;
import totah.lab.athena.ligand.pose.PocketOccupancyEntry;
import totah.lab.athena.ligand.pose.PoseAffinity;
import totah.lab.athena.ligand.pose.PosePocketAssigner;
import totah.lab.athena.ligand.pose.PosePocketAssignment;
import totah.lab.athena.ligand.pose.PosePocketMetrics;
import totah.lab.euclid.spatial.RmsdClusterer;
import totah.lab.euclid.spatial.RmsdClusterer.Clustering;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.biohub.artifact.BiohubPocketEvidenceReader;
import totah.lab.hermes.biohub.model.BiohubPocketEvidence;
import totah.lab.hermes.file.pdbqt.PdbqtFile;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.web.chemistry.ResidueChemistryViewMapper;
import totah.lab.web.poseanalysis.PoseAnalysisView.ArtifactRefView;
import totah.lab.web.poseanalysis.PoseAnalysisView.AssignedPocketView;
import totah.lab.web.poseanalysis.PoseAnalysisView.AssignmentMetricsView;
import totah.lab.web.poseanalysis.PoseAnalysisView.ClusterMemberView;
import totah.lab.web.poseanalysis.PoseAnalysisView.ClusterSummaryView;
import totah.lab.web.poseanalysis.PoseAnalysisView.ContactProfileView;
import totah.lab.web.poseanalysis.PoseAnalysisView.CrossPoseView;
import totah.lab.web.poseanalysis.PoseAnalysisView.CrossProteinPoseComparisonView;
import totah.lab.web.poseanalysis.PoseAnalysisView.CysteineProximityView;
import totah.lab.web.poseanalysis.PoseAnalysisView.DockingTargetView;
import totah.lab.web.poseanalysis.PoseAnalysisView.LigandAnalysisView;
import totah.lab.web.poseanalysis.PoseAnalysisView.LigandOptionView;
import totah.lab.web.poseanalysis.PoseAnalysisView.LigandRunOptionView;
import totah.lab.web.poseanalysis.PoseAnalysisView.PocketOccupancyEntryView;
import totah.lab.web.poseanalysis.PoseAnalysisView.PocketOccupancyView;
import totah.lab.web.poseanalysis.PoseAnalysisView.PosePocketAssignmentView;
import totah.lab.web.poseanalysis.PoseAnalysisView.PoseView;
import totah.lab.web.poseanalysis.PoseAnalysisView.ProvenanceView;
import totah.lab.web.poseanalysis.PoseAnalysisView.ResidueContactView;
import totah.lab.web.poseanalysis.PoseAnalysisView.RunSummaryView;
import totah.lab.web.poseanalysis.PoseAnalysisView.SamProximityView;
import totah.lab.web.service.PocketAlphaSphereProjection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only receptor-ligand pose analysis for any structure and ligand
 * present in the docking tables: run and pose summaries, best-pose
 * per-residue contact profiles against the receptor, per-run RMSD pose
 * clustering, and SAM-site proximity when BioHub evidence exists for
 * the receptor.
 *
 * <p>Parsing comes from hermes ({@link PdbqtReader} +
 * {@link PdbqtGaiaMapper}), contact analysis from athena
 * ({@link DefaultContactAnalyzer}), clustering from euclid
 * ({@link RmsdClusterer}); this service only orchestrates persistence
 * loading and those calculations. Runs whose receptor PDBQT cannot be
 * resolved (no {@code receptor_artifact_id} in the run metadata, or
 * the artifact is missing) still appear in the summary; their
 * contact/cluster/SAM sections report {@code available = false} with
 * the reason.</p>
 *
 * <p>Pose-to-pocket assignment, per-run pocket occupancy and
 * cross-protein pose comparison reuse the same loading seams: candidate
 * pockets come from the pocket tables of the run's structure (alpha
 * spheres preferred, pocket residues always), and the athena assigner /
 * occupancy analyzer / cross-protein comparator do the geometry. All
 * three sections degrade to {@code available = false} with the reason
 * instead of failing the request. Terminology everywhere: a Vina pose
 * is <i>assigned to</i> a pocket — never "binds"; the Vina affinity
 * and the assignment score are separate fields end to end.</p>
 */
@Service
public class PoseAnalysisService {

    private static final double CONTACT_CUTOFF_ANGSTROMS =
            DefaultContactAnalyzer.DEFAULT_CONTACT_CUTOFF_ANGSTROMS;

    /**
     * Shell cutoff spanning the whole receptor so the SAM-set minimum
     * distance is exact rather than truncated at the display shell.
     */
    private static final double FULL_SHELL_ANGSTROMS = 500.0;

    private static final double RMSD_THRESHOLD_ANGSTROMS = 2.0;

    /** Hard cap on ligand options returned for a structure picker. */
    private static final int MAX_LIGAND_OPTIONS = 500;

    private final PoseAnalysisRepository repository;
    private final PdbqtReader pdbqtReader = new PdbqtReader();
    private final DefaultContactAnalyzer contactAnalyzer =
            new DefaultContactAnalyzer(
                    CONTACT_CUTOFF_ANGSTROMS,
                    FULL_SHELL_ANGSTROMS
            );
    private final DefaultLigandInteractionAnalyzer interactionAnalyzer =
            new DefaultLigandInteractionAnalyzer();
    private final RmsdClusterer clusterer = new RmsdClusterer();
    private final BiohubPocketEvidenceReader evidenceReader =
            new BiohubPocketEvidenceReader();
    /**
     * Contact analyzer for pocket assignment with athena's default
     * shell (8 A): the SAM-oriented 500 A shell of
     * {@link #contactAnalyzer} would report every receptor residue as
     * a contact and drown the coverage signals of the assigner.
     */
    private final DefaultContactAnalyzer assignmentContactAnalyzer =
            new DefaultContactAnalyzer();
    private final PosePocketAssigner poseAssigner =
            new DefaultPosePocketAssigner();
    private final LigandPocketOccupancyAnalyzer occupancyAnalyzer =
            new LigandPocketOccupancyAnalyzer();
    private final CrossProteinPoseComparator crossProteinComparator =
            new DefaultCrossProteinPoseComparator();

    private final Path chemflowArtifactRoot;
    private final Path artifactRoot;
    private final PocketFrameValidator frameValidator;

    /**
     * The two-argument constructor keeps the default structure-artifact
     * root; Spring uses the three-argument one.
     */
    public PoseAnalysisService(
            PoseAnalysisRepository repository,
            String chemflowArtifactRoot
    ) {
        this(repository, chemflowArtifactRoot, defaultArtifactRoot());
    }

    @Autowired
    public PoseAnalysisService(
            PoseAnalysisRepository repository,
            @Value("${totah.poseanalysis.chemflow-artifact-root:"
                    + "/Users/yazan/projects/chemflow/backend"
                    + "/artifact-storage}")
            String chemflowArtifactRoot,
            @Value("${totah.artifacts.root}")
            String artifactRoot
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.chemflowArtifactRoot =
                Path.of(Objects.requireNonNull(chemflowArtifactRoot,
                        "chemflowArtifactRoot"));
        this.artifactRoot =
                Path.of(Objects.requireNonNull(artifactRoot,
                        "artifactRoot"));
        this.frameValidator = new PocketFrameValidator(this.artifactRoot);
    }

    private static String defaultArtifactRoot() {
        String totahRoot = System.getenv("TOTAH_LAB_ROOT");
        String artifactRoot = System.getenv("TOTAH_ARTIFACT_ROOT");
        if (artifactRoot != null && !artifactRoot.isBlank()) {
            return artifactRoot;
        }
        return (totahRoot == null ? "" : totahRoot)
                + "/resources/shared-resources/src/main/resources";
    }

    @Transactional(readOnly = true)
    /**
     * Receptors with docking runs: the dynamic target list for the
     * pose analysis page.
     */
    public List<DockingTargetView> targets() {
        List<DockingTargetView> targets = new ArrayList<>();
        for (DockingTargetProjection projection
                : repository.findDockingTargets()) {
            targets.add(new DockingTargetView(
                    projection.getReceptorId(),
                    projection.getStructureId(),
                    projection.getTargetName(),
                    projection.getUniProtId(),
                    projection.getRunCount(),
                    projection.getLigandCount()
            ));
        }
        return List.copyOf(targets);
    }

    /**
     * Ligands docked against the target receptor, best-scoring first.
     * A target can carry hundreds of thousands of docked ligands, so
     * callers always pass a limit and an optional id/label/SMILES
     * search.
     */
    public List<LigandOptionView> ligands(
            long receptorId,
            String query,
            int limit
    ) {
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIGAND_OPTIONS));
        String effectiveQuery = query == null || query.isBlank()
                ? null
                : query.trim();
        List<LigandOptionView> options = new ArrayList<>();
        for (LigandOptionProjection projection
                : repository.findLigandsForReceptor(
                        receptorId, effectiveQuery, effectiveLimit)) {
            options.add(new LigandOptionView(
                    projection.getLigandId(),
                    projection.getLabel() != null
                            ? projection.getLabel()
                            : projection.getLigandId(),
                    projection.getSmiles(),
                    projection.getRunCount(),
                    projection.getPoseCount(),
                    projection.getBestScore()
            ));
        }
        return List.copyOf(options);
    }

    /**
     * One picker option per (ligand, run) of a receptor: separate runs
     * are reported individually rather than as a ligand with a run
     * count. Same limit/search semantics as {@link #ligands}.
     */
    public List<LigandRunOptionView> ligandRuns(
            long receptorId,
            String query,
            int limit
    ) {
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIGAND_OPTIONS));
        String effectiveQuery = query == null || query.isBlank()
                ? null
                : query.trim();
        List<LigandRunOptionView> options = new ArrayList<>();
        for (LigandRunOptionProjection projection
                : repository.findLigandRunsForReceptor(
                        receptorId, effectiveQuery, effectiveLimit)) {
            options.add(new LigandRunOptionView(
                    projection.getLigandId(),
                    projection.getLabel() != null
                            ? projection.getLabel()
                            : projection.getLigandId(),
                    projection.getSmiles(),
                    projection.getRunId(),
                    projection.getMethod() != null
                            ? projection.getMethod()
                            : "vina",
                    projection.getPoseCount(),
                    projection.getBestScore()
            ));
        }
        return List.copyOf(options);
    }

    @Transactional(readOnly = true)
    public LigandAnalysisView analysis(long receptorId, String ligandId)
            throws IOException {
        List<PoseRunProjection> runs =
                repository.findRunsForLigand(receptorId, ligandId);
        if (runs.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No docking runs of ligand " + ligandId
                            + " for receptor " + receptorId
            );
        }

        // one analysis context per request: receptor structures and
        // pose models are loaded once and shared between the contact,
        // cluster and SAM-proximity computations
        Map<Path, Structure> receptorCache = new HashMap<>();
        Map<String, List<PdbqtModel>> modelCache = new HashMap<>();

        List<RunSummaryView> runViews = new ArrayList<>();
        List<ContactProfileView> profiles = new ArrayList<>();
        List<ClusterSummaryView> clusters = new ArrayList<>();
        List<SamProximityView> samProximity = new ArrayList<>();

        for (PoseRunProjection run : runs) {
            List<PoseProjection> poses =
                    repository.findPoses(run.getId(), ligandId);

            Structure receptor = null;
            Map<Integer, String> names = null;
            List<LigandContact> contacts = null;
            List<LigandInteraction> interactions = null;
            String unavailable = null;
            try {
                receptor = receptorStructure(run, receptorCache);
                names = residueNames(receptor);
                PoseProjection best = bestPose(run, poses);
                Ligand bestLigand = poseLigand(best, modelCache);
                contacts = contactAnalyzer.analyze(receptor, bestLigand);
                interactions = interactionAnalyzer.analyze(
                        receptor, bestLigand);
            } catch (IOException | IllegalStateException exception) {
                unavailable = exception.getMessage();
            }

            runViews.add(runSummary(run, poses));
            profiles.add(contactProfile(run, poses, contacts, interactions, names,
                    unavailable));
            clusters.add(clusterSummary(run, poses, modelCache));
            samProximity.add(samProximity(
                    run, poses, contacts, unavailable));
        }

        LigandOptionProjection option =
                repository.findLigandOption(receptorId, ligandId)
                        .orElse(null);
        String ligandLabel = option != null ? option.getLabel() : null;
        String smiles = option != null ? option.getSmiles() : null;

        return new LigandAnalysisView(
                receptorId,
                ligandId,
                ligandLabel != null ? ligandLabel : ligandId,
                smiles,
                runViews,
                profiles,
                clusters,
                samProximity
        );
    }

    /**
     * The raw PDBQT content of one pose (all model blocks included;
     * the caller selects the mode by index).
     */
    @Transactional(readOnly = true)
    public String poseFileContent(long poseId) throws IOException {
        PoseProjection pose = repository.findPose(poseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No docking pose " + poseId
                ));
        return Files.readString(Path.of(pose.getPoseFile()));
    }

    /**
     * The raw receptor PDBQT of a run, resolved from the chemflow
     * artifact store the same way the analysis resolves it for contact
     * computation.
     */
    @Transactional(readOnly = true)
    public String receptorFileContent(long runId) throws IOException {
        PoseRunProjection run = repository.findRun(runId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No docking run " + runId
                ));
        return Files.readString(receptorPdbqtPath(run));
    }

    /**
     * The contact profile of one specific pose, computed on demand when
     * the page selects a pose other than the run's best.
     */
    @Transactional(readOnly = true)
    public ContactProfileView poseContactProfile(long poseId) {
        PoseProjection pose = repository.findPose(poseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No docking pose " + poseId
                ));
        PoseRunProjection run = repository.findRunForPose(poseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No docking run for pose " + poseId
                ));
        try {
            Structure receptor = receptorStructure(run, new HashMap<>());
            Ligand ligand = poseLigand(pose, new HashMap<>());
            List<LigandContact> contacts =
                    contactAnalyzer.analyze(receptor, ligand);
            List<LigandInteraction> interactions =
                    interactionAnalyzer.analyze(receptor, ligand);
            return new ContactProfileView(
                    run.getId(),
                    pose.getId(),
                    pose.getLigandLabel(),
                    run.getMethod(),
                    run.getTargetName(),
                    CONTACT_CUTOFF_ANGSTROMS,
                    true,
                    null,
                    directContactViews(
                            contacts, interactions, residueNames(receptor))
            );
        } catch (IOException | IllegalStateException exception) {
            return new ContactProfileView(
                    run.getId(),
                    pose.getId(),
                    pose.getLigandLabel(),
                    run.getMethod(),
                    run.getTargetName(),
                    CONTACT_CUTOFF_ANGSTROMS,
                    false,
                    exception.getMessage(),
                    List.of()
            );
        }
    }

    /**
     * The pocket assignment of one docking pose: the pose is assigned to
     * the best-matching candidate pocket of its run's structure, or
     * reported AMBIGUOUS / NOT_ASSIGNED with the deciding reason. The
     * Vina affinity ({@code score}) and the assignment score are
     * separate fields, never merged. Degrades to
     * {@code available = false} when the receptor or pose file cannot
     * be resolved or parsed.
     */
    @Transactional(readOnly = true)
    public PosePocketAssignmentView pocketAssignment(long poseId) {
        PoseProjection pose = repository.findPose(poseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No docking pose " + poseId
                ));
        try {
            PoseContext context = poseContext(
                    pose,
                    runForPose(poseId),
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>()
            );
            PosePocketAssignment assignment = poseAssigner.assign(
                    context.receptor(),
                    context.candidates().pockets(),
                    context.ligand(),
                    context.contacts()
            );
            return assignmentView(
                    pose,
                    assignment,
                    context.candidates().detailsById(),
                    context.candidates().provenance()
            );
        } catch (IOException | IllegalStateException
                 | IllegalArgumentException exception) {
            return new PosePocketAssignmentView(
                    pose.getId(),
                    pose.getLigandLabel(),
                    pose.getVinaScore(),
                    false,
                    exception.getMessage(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null
            );
        }
    }

    /**
     * Pocket occupancy of every pose of a run: each pose is assigned to
     * a candidate pocket and the assignments are aggregated per pocket
     * with their Vina affinities kept separate from the assignment
     * scores. This is a pose-frequency report, not a binding
     * propensity.
     */
    @Transactional(readOnly = true)
    public PocketOccupancyView pocketOccupancy(long runId) {
        PoseRunProjection run = repository.findRun(runId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No docking run " + runId
                ));
        List<PoseProjection> poses = repository.findPosesByRunId(runId);
        try {
            Structure receptor = receptorStructure(run, new HashMap<>());
            CandidatePockets candidates =
                    candidatePockets(run, receptor, new HashMap<>());
            Map<String, List<PdbqtModel>> modelCache = new HashMap<>();

            List<PosePocketAssignment> assignments = new ArrayList<>();
            List<PoseAffinity> affinities = new ArrayList<>();
            for (PoseProjection pose : poses) {
                Ligand ligand = poseLigand(pose, modelCache);
                assignments.add(poseAssigner.assign(
                        receptor,
                        candidates.pockets(),
                        ligand,
                        assignmentContactAnalyzer.analyze(receptor, ligand)
                ));
                affinities.add(new PoseAffinity(
                        pose.getLigandLabel(),
                        pose.getVinaScore()
                ));
            }

            LigandPocketOccupancy occupancy =
                    occupancyAnalyzer.summarize(assignments, affinities);
            int ambiguousCount = (int) assignments.stream()
                    .filter(assignment -> assignment.status()
                            == AssignmentStatus.AMBIGUOUS)
                    .count();

            List<PocketOccupancyEntryView> entries = new ArrayList<>();
            for (PocketOccupancyEntry entry : occupancy.entries()) {
                AssignedPocketView pocket = pocketView(
                        entry.pocket(),
                        candidates.detailsById()
                );
                entries.add(new PocketOccupancyEntryView(
                        pocket.pocketId(),
                        pocket.pocketNumber(),
                        pocket.source(),
                        entry.poseCount(),
                        entry.fractionOfPoses(),
                        entry.bestAffinity(),
                        entry.medianAffinity(),
                        entry.meanAssignmentScore(),
                        entry.bestAssignmentScore(),
                        entry.poseLabels()
                ));
            }
            return new PocketOccupancyView(
                    runId,
                    true,
                    null,
                    List.copyOf(entries),
                    occupancy.notAssignedCount(),
                    ambiguousCount,
                    provenanceView(candidates.provenance())
            );
        } catch (IOException | IllegalStateException
                 | IllegalArgumentException exception) {
            return new PocketOccupancyView(
                    runId,
                    false,
                    exception.getMessage(),
                    List.of(),
                    0,
                    0,
                    null
            );
        }
    }

    /**
     * Whether two docking poses — each assigned against its own
     * receptor — occupy structurally homologous sites, decided by
     * structural pocket alignment. Poses of the same receptor are a
     * valid input. Either side failing to load (missing receptor
     * artifact, unparsable pose, no candidate pockets) degrades the
     * whole comparison to {@code available = false} with the reason.
     */
    @Transactional(readOnly = true)
    public CrossProteinPoseComparisonView crossProteinComparison(
            long poseId,
            long otherPoseId
    ) {
        PoseProjection queryPose = repository.findPose(poseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No docking pose " + poseId
                ));
        PoseProjection candidatePose = repository.findPose(otherPoseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No docking pose " + otherPoseId
                ));
        try {
            Map<Path, Structure> receptorCache = new HashMap<>();
            Map<String, List<PdbqtModel>> modelCache = new HashMap<>();
            Map<String, CandidatePockets> pocketCache = new HashMap<>();

            PoseContext query = poseContext(
                    queryPose,
                    runForPose(poseId),
                    receptorCache,
                    modelCache,
                    pocketCache
            );
            PoseContext candidate = poseContext(
                    candidatePose,
                    runForPose(otherPoseId),
                    receptorCache,
                    modelCache,
                    pocketCache
            );

            PosePocketAssignment queryAssignment = poseAssigner.assign(
                    query.receptor(),
                    query.candidates().pockets(),
                    query.ligand(),
                    query.contacts()
            );
            PosePocketAssignment candidateAssignment =
                    poseAssigner.assign(
                            candidate.receptor(),
                            candidate.candidates().pockets(),
                            candidate.ligand(),
                            candidate.contacts()
                    );

            CrossProteinPoseComparison comparison =
                    crossProteinComparator.compare(
                            queryPose.getLigandLabel(),
                            query.receptor(),
                            queryAssignment,
                            query.ligand(),
                            query.contacts(),
                            candidatePose.getLigandLabel(),
                            candidate.receptor(),
                            candidateAssignment,
                            candidate.ligand(),
                            candidate.contacts()
                    );

            return new CrossProteinPoseComparisonView(
                    true,
                    null,
                    crossPoseView(queryPose, query, queryAssignment),
                    crossPoseView(
                            candidatePose,
                            candidate,
                            candidateAssignment
                    ),
                    comparison.samePocketNumber(),
                    comparison.pocketsStructurallyHomologous(),
                    comparison.pocketSimilarity(),
                    comparison.alignedLigandCentroidDistance(),
                    comparison.alignedLigandRmsd(),
                    comparison.sharedAlignedContactResidues(),
                    comparison.contactResidueSimilarity(),
                    comparison.relationship().name(),
                    comparison.reason()
            );
        } catch (IOException | IllegalStateException
                 | IllegalArgumentException exception) {
            return new CrossProteinPoseComparisonView(
                    false,
                    exception.getMessage(),
                    null,
                    null,
                    false,
                    false,
                    null,
                    null,
                    null,
                    0,
                    0.0,
                    null,
                    null
            );
        }
    }

    private RunSummaryView runSummary(
            PoseRunProjection run,
            List<PoseProjection> poses
    ) {        List<PoseView> poseViews = new ArrayList<>();
        Map<Integer, Double> perSeedBest = new LinkedHashMap<>();
        for (PoseProjection pose : poses) {
            PoseLabel label = PoseLabel.parse(pose.getLigandLabel());
            poseViews.add(new PoseView(
                    pose.getId(),
                    pose.getLigandLabel(),
                    pose.getVinaScore(),
                    label.seed(),
                    label.mode(),
                    label.rank(),
                    label.confidence()
            ));
            if (label.seed() != null) {
                perSeedBest.merge(
                        label.seed(),
                        pose.getVinaScore(),
                        Math::min
                );
            }
        }
        return new RunSummaryView(
                run.getId(),
                run.getReceptorId(),
                run.getTargetName(),
                run.getUniProtId(),
                run.getMethod() != null ? run.getMethod() : "vina",
                poses.size(),
                run.getBestScore(),
                perSeedBest,
                poseViews
        );
    }

    /**
     * The best pose of a run: highest confidence when the labels carry
     * one (DiffDock imports), otherwise the lowest vina score.
     */
    private PoseProjection bestPose(
            PoseRunProjection run,
            List<PoseProjection> poses
    ) {
        PoseProjection best = null;
        for (PoseProjection pose : poses) {
            if (best == null) {
                best = pose;
                continue;
            }
            Double confidence =
                    PoseLabel.parse(pose.getLigandLabel()).confidence();
            Double bestConfidence =
                    PoseLabel.parse(best.getLigandLabel()).confidence();
            if (confidence != null && bestConfidence != null) {
                if (confidence > bestConfidence) {
                    best = pose;
                }
            } else if (pose.getVinaScore() < best.getVinaScore()) {
                best = pose;
            }
        }
        return best;
    }

    private ContactProfileView contactProfile(
            PoseRunProjection run,
            List<PoseProjection> poses,
            List<LigandContact> contacts,
            List<LigandInteraction> interactions,
            Map<Integer, String> names,
            String unavailable
    ) {
        PoseProjection best = bestPose(run, poses);
        if (contacts == null) {
            return new ContactProfileView(
                    run.getId(),
                    best != null ? best.getId() : null,
                    best != null ? best.getLigandLabel() : null,
                    run.getMethod(),
                    run.getTargetName(),
                    CONTACT_CUTOFF_ANGSTROMS,
                    false,
                    unavailable,
                    List.of()
            );
        }
        return new ContactProfileView(
                run.getId(),
                best.getId(),
                best.getLigandLabel(),
                run.getMethod(),
                run.getTargetName(),
                CONTACT_CUTOFF_ANGSTROMS,
                true,
                null,
                directContactViews(contacts, interactions, names)
        );
    }

    /** Direct contacts of a pose, mapped to view rows. */
    private static List<ResidueContactView> directContactViews(
            List<LigandContact> contacts,
            List<LigandInteraction> interactions,
            Map<Integer, String> names
    ) {
        List<ResidueContactView> views = new ArrayList<>();
        for (LigandContact contact : contacts) {
            if (contact.type() != ContactType.DIRECT) {
                continue;
            }
            views.add(new ResidueContactView(
                    contact.residue().chainId(),
                    contact.residue().residueNumber(),
                    names.get(contact.residue().residueNumber()),
                    contact.distance(),
                    ResidueChemistryViewMapper.map(
                            names.get(contact.residue().residueNumber())),
                    interactionViews(contact, interactions)
            ));
        }
        return views;
    }

    private static List<PoseAnalysisView.InteractionView> interactionViews(
            LigandContact contact,
            List<LigandInteraction> interactions
    ) {
        if (interactions == null) return List.of();
        return interactions.stream()
                .filter(interaction -> interaction.residue()
                        .equals(contact.residue()))
                .map(interaction -> new PoseAnalysisView.InteractionView(
                        interaction.type().name(),
                        switch (interaction.type()) {
                            case HYDROGEN_BOND -> "Hydrogen bond";
                            case SALT_BRIDGE -> "Salt bridge";
                        },
                        interaction.receptorAtom().getName(),
                        interaction.ligandAtom().getName(),
                        interaction.distance(),
                        interaction.angleDegrees(),
                        interaction.basis()
                ))
                .toList();
    }

    private ClusterSummaryView clusterSummary(
            PoseRunProjection run,
            List<PoseProjection> poses,
            Map<String, List<PdbqtModel>> modelCache
    ) {
        try {
            List<List<double[]>> coordinates = new ArrayList<>();
            for (PoseProjection pose : poses) {
                coordinates.add(toArrays(
                        poseLigand(pose, modelCache)));
            }
            Clustering clustering = clusterer.cluster(
                    coordinates,
                    RMSD_THRESHOLD_ANGSTROMS
            );
            List<ClusterMemberView> members = new ArrayList<>();
            for (int index : clustering.topCluster()) {
                PoseProjection pose = poses.get(index);
                PoseLabel label = PoseLabel.parse(pose.getLigandLabel());
                members.add(new ClusterMemberView(
                        pose.getId(),
                        pose.getLigandLabel(),
                        label.seed(),
                        label.mode()
                ));
            }
            return new ClusterSummaryView(
                    run.getId(),
                    run.getTargetName(),
                    RMSD_THRESHOLD_ANGSTROMS,
                    poses.size(),
                    clustering.clusterCount(),
                    clustering.largestClusterSize(),
                    members,
                    true,
                    null
            );
        } catch (IOException | IllegalStateException
                 | IllegalArgumentException exception) {
            return new ClusterSummaryView(
                    run.getId(),
                    run.getTargetName(),
                    RMSD_THRESHOLD_ANGSTROMS,
                    poses.size(),
                    0,
                    0,
                    List.of(),
                    false,
                    exception.getMessage()
            );
        }
    }

    private SamProximityView samProximity(
            PoseRunProjection run,
            List<PoseProjection> poses,
            List<LigandContact> contacts,
            String unavailable
    ) {
        PoseProjection best = bestPose(run, poses);
        if (contacts == null) {
            return new SamProximityView(
                    run.getId(),
                    best != null ? best.getId() : null,
                    best != null ? best.getLigandLabel() : null,
                    run.getTargetName(),
                    0,
                    null,
                    List.of(),
                    false,
                    unavailable
            );
        }
        try {
            BiohubPocketEvidence sam = samEvidence(run.getReceptorId());

            Double minimum = null;
            List<CysteineProximityView> cysteines = new ArrayList<>();
            for (BiohubPocketEvidence.ResidueContact contact
                    : sam.residues()) {
                Double distance = null;
                for (LigandContact ligandContact : contacts) {
                    if (ligandContact.residue().residueNumber()
                            == contact.residueNumber()) {
                        distance = ligandContact.distance();
                        break;
                    }
                }
                if (distance == null) {
                    continue;
                }
                if (minimum == null || distance < minimum) {
                    minimum = distance;
                }
                if ("CYS".equals(contact.residueName())) {
                    cysteines.add(new CysteineProximityView(
                            contact.residueNumber(),
                            distance
                    ));
                }
            }
            cysteines.sort(Comparator.comparingDouble(
                    CysteineProximityView::minimumDistance
            ));

            return new SamProximityView(
                    run.getId(),
                    best.getId(),
                    best.getLigandLabel(),
                    run.getTargetName(),
                    sam.residues().size(),
                    minimum,
                    cysteines,
                    true,
                    null
            );
        } catch (IOException | IllegalStateException exception) {
            return new SamProximityView(
                    run.getId(),
                    best != null ? best.getId() : null,
                    best != null ? best.getLigandLabel() : null,
                    run.getTargetName(),
                    0,
                    null,
                    List.of(),
                    false,
                    exception.getMessage()
            );
        }
    }

    /**
     * The SAM BioHub pocket evidence of a receptor (the same artifact
     * source the ligand-contact conservation report uses).
     */
    private BiohubPocketEvidence samEvidence(long receptorId)
            throws IOException {
        for (String location
                : repository.findBiohubArtifactLocations(receptorId)) {
            BiohubPocketEvidence evidence =
                    evidenceReader.read(Path.of(location));
            if ("SAM".equalsIgnoreCase(evidence.ligandCcd())) {
                return evidence;
            }
        }
        throw new IllegalStateException(
                "No SAM BioHub evidence for receptor " + receptorId
        );
    }

    private static Map<Integer, String> residueNames(Structure receptor) {
        Map<Integer, String> names = new HashMap<>();
        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                names.put(residue.getNumber(), residue.getName());
            }
        }
        return names;
    }

    /**
     * Everything the pocket-assignment analyses need for one pose: the
     * run, the receptor structure, the pose ligand, its contacts and
     * the candidate pockets of the run's structure.
     */
    private record PoseContext(
            PoseRunProjection run,
            Structure receptor,
            Ligand ligand,
            List<LigandContact> contacts,
            CandidatePockets candidates
    ) {
    }

    /**
     * The candidate pockets of one structure as gaia domain objects,
     * plus the database details of each pocket (keyed by pocket id) for
     * mapping assigned pockets back to view rows, and the coordinate
     * provenance of the sphere data. Package-private: the
     * differential-contact report service reuses the same loading seam.
     */
    record CandidatePockets(
            List<Pocket> pockets,
            Map<Long, PosePocketProjection> detailsById,
            CoordinateProvenance provenance
    ) {
    }

    private PoseContext poseContext(
            PoseProjection pose,
            PoseRunProjection run,
            Map<Path, Structure> receptorCache,
            Map<String, List<PdbqtModel>> modelCache,
            Map<String, CandidatePockets> pocketCache
    ) throws IOException {
        Structure receptor = receptorStructure(run, receptorCache);
        Ligand ligand = poseLigand(pose, modelCache);
        return new PoseContext(
                run,
                receptor,
                ligand,
                assignmentContactAnalyzer.analyze(receptor, ligand),
                candidatePockets(run, receptor, pocketCache)
        );
    }

    /** Package-private: reused by the differential-contact report. */
    PoseRunProjection runForPose(long poseId) {
        return repository.findRunForPose(poseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No docking run for pose " + poseId
                ));
    }

    /** Package-private: reused by the differential-contact report. */
    CandidatePockets candidatePockets(
            PoseRunProjection run,
            Structure receptor,
            Map<String, CandidatePockets> cache
    ) {
        Long structureId = run.getStructureId();
        if (structureId == null) {
            throw new IllegalStateException(
                    "Run " + run.getId() + " has no structure; candidate"
                            + " pockets cannot be loaded"
            );
        }
        String cacheKey = structureId + "|"
                + run.getReceptorArtifactId();
        CandidatePockets cached = cache.get(cacheKey);
        if (cached == null) {
            cached = loadCandidatePockets(
                    structureId, receptor, evaluateFrame(run, receptor));
            cache.put(cacheKey, cached);
        }
        return cached;
    }

    /**
     * Candidate pockets for a receptor loaded from an explicit file
     * (for example an external DiffDock directory's
     * {@code target_protein.pdb}), with the frame provenance evaluated
     * against the given matched structure artifact. Package-private:
     * used by the pocket-architecture report's directory side.
     */
    CandidatePockets candidatePocketsForArtifact(
            StructureArtifactProjection structureArtifact,
            Structure receptor,
            Path receptorFile,
            String receptorArtifactLabel
    ) {
        CoordinateProvenance provenance = frameValidator.evaluate(
                receptorArtifactLabel,
                "external receptor file",
                structureArtifact.getStructureId(),
                receptor,
                receptorFile,
                structureArtifact
        );
        return loadCandidatePockets(
                structureArtifact.getStructureId(),
                receptor,
                provenance
        );
    }

    /**
     * The pocket-bearing structure artifact whose file content matches
     * {@code referenceFile} byte for byte (size pre-filter, then
     * SHA-256). Package-private: provenance-based resolution of an
     * external receptor file to its DB structure — no accession or
     * filename guessing.
     */
    Optional<StructureArtifactProjection> findStructureArtifactByContent(
            Path referenceFile
    ) throws IOException {
        long referenceSize = Files.size(referenceFile);
        String referenceHash = PocketFrameValidator.sha256(referenceFile);
        if (referenceHash == null) {
            return Optional.empty();
        }
        for (StructureArtifactProjection candidate
                : repository.findPocketedStructureArtifacts()) {
            Path path = Path.of(candidate.getArtifactStorageLocation());
            Path resolved = path.isAbsolute()
                    ? path
                    : artifactRoot().resolve(path);
            try {
                if (Files.size(resolved) != referenceSize) {
                    continue;
                }
            } catch (IOException exception) {
                continue;
            }
            if (referenceHash.equals(
                    PocketFrameValidator.sha256(resolved))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private Path artifactRoot() {
        return artifactRoot;
    }

    /**
     * The candidate pockets of one structure as gaia domain pockets:
     * persisted alpha spheres when present (the primary occupancy
     * signal), member residues always, and the pocket center derived
     * from the sphere centers or, without spheres, from the pocket
     * residue atoms of the receptor. Unlike the pocket-report mapping
     * ({@code PocketReportApplicationService.toDomainPocket}), the
     * center here is real geometry because the assigner scores
     * centroid proximity with it.
     *
     * <p>Frame safety: the spheres are only attached when the
     * pocket-side structure artifact shares a frame with the run's
     * docked receptor artifact — same file (IDENTICAL_ARTIFACT) or a
     * validated rigid CA fit (VALIDATED_TRANSFORM, spheres moved into
     * the receptor frame through the recorded transform). On
     * INCOMPATIBLE/UNKNOWN the spheres are withheld, so the assigner
     * falls back to frame-independent residue/contact evidence and the
     * provenance labels the sphere metrics NOT_AVAILABLE.</p>
     */
    private CandidatePockets loadCandidatePockets(
            long structureId,
            Structure receptor,
            CoordinateProvenance provenance
    ) {
        Map<Long, List<AlphaSphere>> spheresByPocket =
                new LinkedHashMap<>();
        if (provenance.sphereMetricsAvailable()) {
            for (PocketAlphaSphereProjection sphere
                    : repository.findAlphaSpheresByStructureId(
                            structureId)) {
                Point3D center = new Point3D(
                        sphere.getCenterX(),
                        sphere.getCenterY(),
                        sphere.getCenterZ()
                );
                if (PocketFrameValidator.requiresTransform(provenance)) {
                    center = provenance.transform().transform()
                            .apply(center);
                }
                Point3D finalCenter = center;
                spheresByPocket
                        .computeIfAbsent(
                                sphere.getPocketId(),
                                pocketId -> new ArrayList<>()
                        )
                        .add(new AlphaSphere(
                                sphere.getSphereIndex(),
                                finalCenter,
                                sphere.getRadius()
                        ));
            }
        }

        Map<Long, List<ResidueId>> residuesByPocket =
                new LinkedHashMap<>();
        for (PosePocketResidueProjection residue
                : repository.findPocketResiduesByStructureId(structureId)) {
            residuesByPocket
                    .computeIfAbsent(
                            residue.getPocketId(),
                            pocketId -> new ArrayList<>()
                    )
                    .add(new ResidueId(
                            residue.getChain(),
                            residue.getResidueNumber(),
                            insertionCode(residue.getInsertionCode())
                    ));
        }

        List<Pocket> pockets = new ArrayList<>();
        Map<Long, PosePocketProjection> detailsById = new LinkedHashMap<>();
        for (PosePocketProjection detail
                : repository.findPocketsByStructureId(structureId)) {
            long pocketId = detail.getId();
            List<AlphaSphere> spheres = spheresByPocket
                    .getOrDefault(pocketId, List.of());
            List<ResidueId> residues = residuesByPocket
                    .getOrDefault(pocketId, List.of());

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("sourcePocketNumber",
                    String.valueOf(detail.getPocketNumber()));
            metadata.put("internalPocketId", String.valueOf(pocketId));

            pockets.add(new Pocket(
                    PocketId.of(pocketId),
                    detail.getSource() + " pocket "
                            + detail.getPocketNumber(),
                    pocketSource(detail.getSource()),
                    pocketCenter(receptor, spheres, residues),
                    residues,
                    List.of(),
                    Optional.empty(),
                    spheres.isEmpty()
                            ? Optional.empty()
                            : Optional.of(new AlphaSphereSet(spheres)),
                    metadata
            ));
            detailsById.put(pocketId, detail);
        }
        return new CandidatePockets(
                List.copyOf(pockets), detailsById, provenance);
    }

    /**
     * The frame provenance of one run's receptor artifact against the
     * structure artifact its pocket rows were generated from. Never
     * throws: any failure to load or fit degrades to UNKNOWN or
     * INCOMPATIBLE with the reason in the note.
     */
    private CoordinateProvenance evaluateFrame(
            PoseRunProjection run,
            Structure receptor
    ) {
        try {
            return frameValidator.evaluate(
                    run,
                    receptor,
                    receptorPdbqtPath(run),
                    repository.findStructureArtifact(run.getStructureId())
                            .orElse(null)
            );
        } catch (IOException | RuntimeException exception) {
            return new CoordinateProvenance(
                    new StructureArtifactRef(
                            run.getReceptorArtifactId(),
                            null,
                            "chemflow receptor artifact",
                            null,
                            null
                    ),
                    null,
                    CoordinateCompatibility.UNKNOWN,
                    null,
                    false,
                    "frame validation failed: " + exception.getMessage()
            );
        }
    }

    /**
     * Pocket center: the centroid of the alpha-sphere centers when the
     * pocket has spheres, the centroid of the pocket's residue heavy
     * atoms otherwise. A pocket with neither is degenerate; the origin
     * placeholder keeps it a candidate with a near-zero centroid
     * proximity instead of dropping it silently.
     */
    private static Point3D pocketCenter(
            Structure receptor,
            List<AlphaSphere> spheres,
            List<ResidueId> residues
    ) {
        if (!spheres.isEmpty()) {
            return centroid(spheres.stream()
                    .map(AlphaSphere::center)
                    .toList());
        }
        List<Point3D> atoms = residues.stream()
                .map(receptor::findResidue)
                .flatMap(Optional::stream)
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .map(Atom::getPosition)
                .toList();
        if (!atoms.isEmpty()) {
            return centroid(atoms);
        }
        return new Point3D(0.0, 0.0, 0.0);
    }

    private static Point3D centroid(List<Point3D> points) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Point3D point : points) {
            x += point.x();
            y += point.y();
            z += point.z();
        }
        double count = points.size();
        return new Point3D(x / count, y / count, z / count);
    }

    private static PocketSource pocketSource(String source) {
        try {
            return PocketSource.valueOf(
                    source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException(
                    "Unsupported pocket source: " + source,
                    e
            );
        }
    }

    private static Character insertionCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.charAt(0);
    }

    private static PosePocketAssignmentView assignmentView(
            PoseProjection pose,
            PosePocketAssignment assignment,
            Map<Long, PosePocketProjection> detailsById,
            CoordinateProvenance provenance
    ) {
        return new PosePocketAssignmentView(
                pose.getId(),
                pose.getLigandLabel(),
                pose.getVinaScore(),
                true,
                null,
                assignment.status().name(),
                assignment.reason(),
                pocketView(assignment.pocket(), detailsById),
                assignment.assignmentScore(),
                pocketView(assignment.secondBestPocket(), detailsById),
                assignment.secondBestScore(),
                assignment.scoreMargin(),
                assignment.ambiguous(),
                metricsView(assignment.bestMetrics()),
                provenanceView(provenance)
        );
    }

    /**
     * Maps the frame provenance to its view DTO (athena/gaia-free
     * JSON shape); {@code null} in, {@code null} out.
     */
    private static ProvenanceView provenanceView(
            CoordinateProvenance provenance
    ) {
        if (provenance == null) {
            return null;
        }
        StructureTransform transform = provenance.transform();
        return new ProvenanceView(
                artifactRefView(provenance.receptorArtifact()),
                artifactRefView(provenance.pocketStructureArtifact()),
                provenance.compatibility().name(),
                provenance.sphereMetricsAvailable()
                        ? "AVAILABLE"
                        : "NOT_AVAILABLE",
                transform == null ? null : transform.matchedPairs(),
                transform == null
                        || !Double.isFinite(transform.rmsd())
                        ? null
                        : transform.rmsd(),
                provenance.note()
        );
    }

    private static ArtifactRefView artifactRefView(
            StructureArtifactRef ref
    ) {
        if (ref == null) {
            return null;
        }
        return new ArtifactRefView(
                ref.artifactId(),
                ref.accession(),
                ref.source(),
                ref.modelVersion(),
                ref.sha256()
        );
    }

    private static AssignmentMetricsView metricsView(
            PosePocketMetrics metrics
    ) {
        if (metrics == null) {
            return null;
        }
        AlphaSphereOccupancy spheres = metrics.spheres();
        return new AssignmentMetricsView(
                metrics.ligandCentroidDistance(),
                metrics.atomContainmentFraction(),
                metrics.basis().name(),
                spheres != null
                        ? spheres.atomWithin2AOfSphereFraction()
                        : null,
                spheres != null
                        ? spheres.atomWithin3AOfSphereFraction()
                        : null,
                spheres != null
                        ? spheres.meanNearestSphereDistance()
                        : null,
                spheres != null
                        ? spheres.maxNearestSphereDistance()
                        : null,
                metrics.contactResidueCoverage(),
                metrics.pocketContactCoverage()
        );
    }

    private static AssignedPocketView pocketView(
            Pocket pocket,
            Map<Long, PosePocketProjection> detailsById
    ) {
        if (pocket == null) {
            return null;
        }
        long pocketId = Long.parseLong(pocket.id().value());
        PosePocketProjection detail = detailsById.get(pocketId);
        return new AssignedPocketView(
                pocketId,
                detail != null ? detail.getPocketNumber() : 0,
                detail != null
                        ? detail.getSource()
                        : pocket.source().name()
        );
    }

    private static CrossPoseView crossPoseView(
            PoseProjection pose,
            PoseContext context,
            PosePocketAssignment assignment
    ) {
        return new CrossPoseView(
                pose.getId(),
                pose.getLigandLabel(),
                pose.getVinaScore(),
                context.run().getTargetName(),
                context.run().getUniProtId(),
                pocketView(
                        assignment.pocket(),
                        context.candidates().detailsById()
                )
        );
    }

    /** Package-private: reused by the differential-contact report. */
    Structure receptorStructure(
            PoseRunProjection run,
            Map<Path, Structure> cache
    ) throws IOException {
        Path path = receptorPdbqtPath(run);
        Structure cached = cache.get(path);
        if (cached == null) {
            PdbqtFile file = pdbqtReader.read(path);
            cached = PdbqtGaiaMapper.toStructure(file);
            cache.put(path, cached);
        }
        return cached;
    }

    /**
     * Resolves the receptor PDBQT of a run from the chemflow artifact
     * store: the {@code receptor_artifact_id} recorded in the run's
     * source metadata, looked up as {@code <root>/<id>.pdbqt} or one
     * directory level below (the store partitions artifacts into
     * per-import subdirectories).
     */
    private Path receptorPdbqtPath(PoseRunProjection run)
            throws IOException {
        String artifactId = run.getReceptorArtifactId();
        if (artifactId == null || artifactId.isBlank()) {
            artifactId = repository.findSiblingReceptorArtifactId(
                    run.getReceptorId()
            ).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Run " + run.getId() + " has no receptor_artifact_id"
            ));
        }
        String filename = artifactId + ".pdbqt";
        Path direct = chemflowArtifactRoot.resolve(filename);
        if (Files.exists(direct)) {
            return direct;
        }
        try (var partitions = Files.newDirectoryStream(
                chemflowArtifactRoot,
                Files::isDirectory
        )) {
            for (Path partition : partitions) {
                Path candidate = partition.resolve(filename);
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IOException(
                "Receptor PDBQT " + filename + " not found under "
                        + chemflowArtifactRoot
        );
    }

    /**
     * The pose as a gaia ligand: the labeled mode's model when the
     * label carries one, the first model otherwise. Package-private:
     * reused by the differential-contact report.
     */
    Ligand poseLigand(
            PoseProjection pose,
            Map<String, List<PdbqtModel>> modelCache
    ) throws IOException {
        List<PdbqtModel> models = modelCache.get(pose.getPoseFile());
        if (models == null) {
            models = pdbqtReader.read(Path.of(pose.getPoseFile()))
                    .models();
            modelCache.put(pose.getPoseFile(), models);
        }
        Integer mode = PoseLabel.parse(pose.getLigandLabel()).mode();
        int modelIndex = mode != null ? mode : 1;
        if (modelIndex > models.size()) {
            throw new IOException(
                    "Pose file " + pose.getPoseFile() + " has "
                            + models.size() + " model(s), label "
                            + pose.getLigandLabel() + " requests mode "
                            + modelIndex
            );
        }
        return PdbqtGaiaMapper.toLigand(
                models.get(modelIndex - 1),
                pose.getLigandLabel()
        );
    }

    private static List<double[]> toArrays(Ligand ligand) {
        List<double[]> arrays = new ArrayList<>();
        for (Chain chain : ligand.structure().getChains()) {
            for (Residue residue : chain.residues()) {
                for (var atom : residue.getAtoms()) {
                    if (!atom.isHeavyAtom()) {
                        continue;
                    }
                    Point3D position = atom.getPosition();
                    arrays.add(new double[]{
                            position.x(), position.y(), position.z()});
                }
            }
        }
        return arrays;
    }
}
