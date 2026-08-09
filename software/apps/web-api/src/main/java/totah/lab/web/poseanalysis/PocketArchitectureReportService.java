package totah.lab.web.poseanalysis;

import org.springframework.stereotype.Service;
import totah.lab.athena.ligand.contact.DefaultContactAnalyzer;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.ligand.pose.AssignmentStatus;
import totah.lab.athena.ligand.pose.DefaultPosePocketAssigner;
import totah.lab.athena.ligand.pose.PosePocketAssigner;
import totah.lab.athena.ligand.pose.PosePocketAssignment;
import totah.lab.athena.pocket.architecture.PocketArchitectureAnalyzer;
import totah.lab.athena.pocket.architecture.PocketArchitectureReport;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;
import totah.lab.web.poseanalysis.PoseAnalysisService.CandidatePockets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Batch-side pocket-architecture comparison of two docking poses: each
 * pose's assigned FPOCKET pocket (the same assignment the
 * pocket-assignment endpoint computes) is described and compared by
 * athena's {@link PocketArchitectureAnalyzer} — alpha-sphere
 * architecture, backbone displacement, ligand space and wall geometry.
 *
 * <p>Either side can be a persisted docking pose (by id) or an
 * external DiffDock output directory (a {@code target_protein.pdb}
 * receptor plus {@code rankN_confidence-*.sdf} poses). The directory
 * side is bound to a DB structure purely by content: the receptor
 * file's SHA-256 must match a pocket-bearing structure artifact's file
 * hash; without a match the report fails loudly instead of guessing by
 * accession.</p>
 *
 * <p>Loading reuses the {@link PoseAnalysisService} seams (receptor
 * PDBQT artifact, pose file, candidate pockets) and the hermes
 * structure/SDF readers. The printed text is computational geometry
 * evidence about pocket shape and pose placement; nothing here is a
 * binding claim.</p>
 */
@Service
public class PocketArchitectureReportService {

    private final PoseAnalysisService poseAnalysis;
    private final PoseAnalysisRepository repository;

    private final DefaultContactAnalyzer contactAnalyzer =
            new DefaultContactAnalyzer();
    private final PosePocketAssigner poseAssigner =
            new DefaultPosePocketAssigner();
    private final PocketArchitectureAnalyzer architectureAnalyzer =
            new PocketArchitectureAnalyzer();
    private final PdbReader structureReader =
            new PdbReader();
    private final SdfLigandReader sdfReader = new SdfLigandReader();

    public PocketArchitectureReportService(
            PoseAnalysisService poseAnalysis,
            PoseAnalysisRepository repository
    ) {
        this.poseAnalysis =
                Objects.requireNonNull(poseAnalysis, "poseAnalysis");
        this.repository =
                Objects.requireNonNull(repository, "repository");
    }

    /**
     * One side of the comparison: a display name (after "Pose X:"),
     * the receptor, the pose ligand, its assigned pocket and the
     * coordinate provenance of the pocket spheres.
     */
    private record Side(
            String name,
            Structure receptor,
            Ligand ligand,
            Pocket pocket,
            CoordinateProvenance provenance
    ) {
    }

    /**
     * The report of two persisted poses (see {@link #report}).
     */
    public String report(long poseAId, long poseBId) {
        return report(poseAId, null, 1, poseBId, null, 1);
    }

    /**
     * The pocket-architecture report of two poses. Each side is either
     * a persisted pose id or a DiffDock output directory (non-null dir
     * wins): a header naming both sides and their assigned pockets,
     * the coordinate provenance of both pocket sphere sets, then the
     * full athena rendering.
     *
     * @throws IllegalStateException when a pose, directory, receptor,
     *         rank SDF, structure-artifact hash match or assigned
     *         pocket cannot be resolved — the message states exactly
     *         what is missing
     */
    public String report(
            Long poseAId,
            Path poseADir,
            int rankA,
            Long poseBId,
            Path poseBDir,
            int rankB
    ) {
        Map<Path, Structure> receptorCache = new HashMap<>();
        Map<String, List<PdbqtModel>> modelCache = new HashMap<>();
        Map<String, CandidatePockets> pocketCache = new HashMap<>();

        Side sideA = poseADir != null
                ? loadDirSide("A", poseADir, rankA)
                : loadSide("A", poseAId, receptorCache, modelCache,
                        pocketCache);
        Side sideB = poseBDir != null
                ? loadDirSide("B", poseBDir, rankB)
                : loadSide("B", poseBId, receptorCache, modelCache,
                        pocketCache);

        StringBuilder report = new StringBuilder();
        report.append("Pocket architecture report — computational")
                .append(" geometry evidence (never binding claims)\n");
        report.append("Pose A: ").append(sideA.name()).append('\n');
        report.append("Pose B: ").append(sideB.name()).append('\n');
        appendProvenance(report, "A", sideA);
        appendProvenance(report, "B", sideB);
        report.append('\n');

        // The whole report is sphere geometry: never produce it from
        // an unvalidated or mixed frame.
        for (Side side : new Side[]{sideA, sideB}) {
            if (!side.provenance().sphereMetricsAvailable()) {
                throw new IllegalStateException(
                        "Pocket architecture report refused: "
                                + side.name() + " — "
                                + side.provenance().note()
                );
            }
        }

        PocketArchitectureReport analysis;
        try {
            analysis = architectureAnalyzer.analyze(
                    sideA.receptor(),
                    sideA.pocket(),
                    sideA.ligand(),
                    sideB.receptor(),
                    sideB.pocket(),
                    sideB.ligand()
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IllegalStateException(
                    "Pocket architecture analysis failed for "
                            + sideA.name() + " and " + sideB.name()
                            + ": " + e.getMessage(),
                    e
            );
        }

        report.append(analysis.render());
        return report.toString();
    }

    private Side loadSide(
            String side,
            long poseId,
            Map<Path, Structure> receptorCache,
            Map<String, List<PdbqtModel>> modelCache,
            Map<String, CandidatePockets> pocketCache
    ) {
        PoseProjection pose = repository.findPose(poseId)
                .orElseThrow(() -> new IllegalStateException(
                        "No docking pose " + poseId + " (side " + side
                                + " of the pocket architecture report)"
                ));
        PoseRunProjection run = poseAnalysis.runForPose(poseId);
        Structure receptor;
        Ligand ligand;
        try {
            receptor = poseAnalysis.receptorStructure(run, receptorCache);
            ligand = poseAnalysis.poseLigand(pose, modelCache);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Side " + side + " (pose " + poseId
                            + ") cannot be loaded: "
                            + exception.getMessage(),
                    exception
            );
        }

        List<LigandContact> contacts =
                contactAnalyzer.analyze(receptor, ligand);
        CandidatePockets candidates =
                poseAnalysis.candidatePockets(run, receptor, pocketCache);
        PosePocketAssignment assignment = poseAssigner.assign(
                receptor, candidates.pockets(), ligand, contacts);
        Pocket pocket = requirePocket(
                "Pose " + poseId, assignment, candidates.provenance());

        return new Side(
                poseId + " \"" + pose.getLigandLabel() + "\" (run "
                        + run.getId() + ", " + run.getTargetName()
                        + ", receptor " + run.getReceptorId()
                        + "), assigned " + pocket.name()
                        + " (id " + pocket.id().value() + ")",
                receptor,
                ligand,
                pocket,
                candidates.provenance()
        );
    }

    /**
     * A side loaded from an external DiffDock output directory: the
     * receptor is {@code target_protein.pdb}, the pose is the
     * requested rank's SDF, and the pocket rows come from the DB
     * structure whose artifact file has identical content — provenance
     * by hash, never by accession.
     */
    private Side loadDirSide(String side, Path directory, int rank) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(
                    "Side " + side + ": directory does not exist: "
                            + directory
            );
        }
        Path receptorPath = directory.resolve("target_protein.pdb");
        if (!Files.isRegularFile(receptorPath)) {
            throw new IllegalStateException(
                    "Side " + side + ": no target_protein.pdb in "
                            + directory
            );
        }
        Path poseFile = MutationPoseReportService.resolveRankSdf(
                directory, "side " + side, rank);

        Structure receptor;
        Ligand pose;
        try {
            receptor = structureReader.read(receptorPath);
            pose = sdfReader.read(poseFile);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Side " + side + " cannot be loaded from "
                            + directory + ": " + exception.getMessage(),
                    exception
            );
        }

        StructureArtifactProjection match;
        try {
            match = poseAnalysis.findStructureArtifactByContent(
                    receptorPath).orElse(null);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Side " + side + ": cannot hash " + receptorPath
                            + ": " + exception.getMessage(),
                    exception
            );
        }
        if (match == null) {
            throw new IllegalStateException(
                    "Side " + side + ": no pocket-bearing DB structure"
                            + " artifact has the same content (sha256) as "
                            + receptorPath.getFileName()
                            + "; pockets cannot be resolved by"
                            + " provenance and no accession-based"
                            + " fallback is allowed"
            );
        }

        CandidatePockets candidates =
                poseAnalysis.candidatePocketsForArtifact(
                        match,
                        receptor,
                        receptorPath,
                        directory.getFileName() + "/target_protein.pdb"
                );
        List<LigandContact> contacts =
                contactAnalyzer.analyze(receptor, pose);
        PosePocketAssignment assignment = poseAssigner.assign(
                receptor, candidates.pockets(), pose, contacts);
        if (assignment.status() != AssignmentStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Side " + side + " (directory " + directory
                            + "): the pose is " + assignment.status()
                            + " (" + assignment.reason() + "); the"
                            + " pocket architecture analysis needs an"
                            + " ASSIGNED FPOCKET pocket"
            );
        }
        Pocket pocket = requirePocket(
                "Side " + side + " (directory " + directory + ")",
                assignment,
                candidates.provenance());

        return new Side(
                "directory " + directory + " (rank " + rank
                        + ", pose file "
                        + poseFile.getFileName()
                        + "), matched DB structure "
                        + match.getStructureId() + " (artifact "
                        + match.getArtifactId() + ", "
                        + match.getSourceAccession()
                        + "), assigned " + pocket.name()
                        + " (id " + pocket.id().value() + ")",
                receptor,
                pose,
                pocket,
                candidates.provenance()
        );
    }

    private static Pocket requirePocket(
            String sideDescription,
            PosePocketAssignment assignment,
            CoordinateProvenance provenance
    ) {
        Pocket pocket = assignment.pocket();
        if (pocket == null) {
            throw new IllegalStateException(
                    sideDescription + " is not assigned to any pocket"
                            + " (" + assignment.status() + ": "
                            + assignment.reason() + "); the pocket"
                            + " architecture analysis needs an assigned"
                            + " FPOCKET pocket"
            );
        }
        if (pocket.source() != PocketSource.FPOCKET) {
            throw new IllegalStateException(
                    sideDescription + " is assigned to a "
                            + pocket.source() + " pocket; the pocket"
                            + " architecture analysis needs an FPOCKET"
                            + " pocket"
            );
        }
        if (provenance.sphereMetricsAvailable()) {
            int sphereCount = pocket.alphaSphereSet()
                    .map(AlphaSphereSet::spheres)
                    .map(List::size)
                    .orElse(0);
            if (sphereCount < 3) {
                throw new IllegalStateException(
                        "The assigned pocket of " + sideDescription
                                + " (" + pocket.name() + ") has "
                                + sphereCount
                                + " alpha spheres; the pocket architecture"
                                + " analysis needs at least 3"
                );
            }
        }
        // When the frame is not validated the sphere count check is
        // skipped: report() then refuses with the frame reason, which
        // is the actionable message.
        return pocket;
    }

    private static void appendProvenance(
            StringBuilder report,
            String side,
            Side poseSide
    ) {
        CoordinateProvenance provenance = poseSide.provenance();
        report.append("Provenance ").append(side).append(": ")
                .append(describe(provenance.receptorArtifact(),
                        "docked receptor"))
                .append("; ")
                .append(describe(provenance.pocketStructureArtifact(),
                        "pocket structure"))
                .append("; compatibility ")
                .append(provenance.compatibility())
                .append("; sphere metrics ")
                .append(provenance.sphereMetricsAvailable()
                        ? "AVAILABLE"
                        : "NOT_AVAILABLE")
                .append(" — ").append(provenance.note())
                .append('\n');
    }

    private static String describe(
            StructureArtifactRef ref,
            String role
    ) {
        if (ref == null) {
            return role + " artifact unknown";
        }
        StringBuilder description = new StringBuilder(role)
                .append(" artifact ").append(ref.artifactId());
        if (ref.accession() != null) {
            description.append(" (").append(ref.accession());
            if (ref.modelVersion() != null) {
                description.append(" model ")
                        .append(ref.modelVersion());
            }
            description.append(')');
        }
        if (ref.sha256() != null) {
            description.append(" sha256 ")
                    .append(ref.sha256(), 0, 12)
                    .append("…");
        }
        return description.toString();
    }
}
