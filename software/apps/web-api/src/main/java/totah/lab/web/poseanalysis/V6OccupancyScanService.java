package totah.lab.web.poseanalysis;

import org.springframework.stereotype.Service;
import totah.lab.athena.ligand.contact.DefaultContactAnalyzer;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.ligand.pose.DefaultPosePocketAssigner;
import totah.lab.athena.ligand.pose.PosePocketAssigner;
import totah.lab.athena.ligand.pose.PosePocketAssignment;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;
import totah.lab.web.poseanalysis.PoseAnalysisService.CandidatePockets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Batch-side occupancy scan of every ranked DiffDock pose of one
 * output directory (for example the canonical METTL7B v6 run): each
 * rank's pose is assigned to a candidate pocket of the hash-matched DB
 * structure, and the ranks are then grouped by assigned pocket.
 *
 * <p>Everything loads through the provenance seams: the directory's
 * receptor must hash-match a pocket-bearing structure artifact, and
 * the frame provenance must allow sphere metrics. DiffDock confidence
 * is parsed from the pose filenames and printed as data only — it
 * never feeds the assignment. The homologous-site column is reported
 * twice, honestly: by assignment (the pose was assigned to the
 * homologous pocket) and geometrically (the pose centroid is within
 * {@value #HOMOLOGOUS_CENTROID_SURFACE_ANGSTROMS} A of the homologous
 * pocket's alpha-sphere cloud, surface distance). Computational pose
 * evidence only; nothing here is a binding claim.</p>
 */
@Service
public class V6OccupancyScanService {

    /**
     * Geometric homologous-site criterion: the pose centroid's
     * nearest-sphere-surface distance to the homologous pocket's
     * alpha-sphere cloud. Calibration-pending.
     */
    static final double HOMOLOGOUS_CENTROID_SURFACE_ANGSTROMS = 2.0;

    private final PoseAnalysisService poseAnalysis;
    private final PoseAnalysisRepository repository;

    private final PdbReader structureReader =
            new PdbReader();
    private final SdfLigandReader sdfReader = new SdfLigandReader();
    private final DefaultContactAnalyzer contactAnalyzer =
            new DefaultContactAnalyzer();
    private final PosePocketAssigner poseAssigner =
            new DefaultPosePocketAssigner();

    public V6OccupancyScanService(
            PoseAnalysisService poseAnalysis,
            PoseAnalysisRepository repository
    ) {
        this.poseAnalysis =
                Objects.requireNonNull(poseAnalysis, "poseAnalysis");
        this.repository =
                Objects.requireNonNull(repository, "repository");
    }

    /**
     * The occupancy scan of ranks 1..{@code ranks} in {@code
     * directory}. {@code homologousPocketId} is the DB id of the
     * homologous-site pocket. In the imported canonical-v6 FPOCKET
     * run this is the 197-sphere SAM superpocket, FPOCKET pocket 1 =
     * DB id 3. Pocket numbers are run-local and must not be carried
     * across FPOCKET reruns.
     *
     * @throws IllegalStateException when the directory, receptor, a
     *         rank SDF, the structure-artifact hash match or the frame
     *         provenance cannot be resolved
     */
    public String report(
            Path directory,
            int ranks,
            long homologousPocketId
    ) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(
                    "Occupancy scan directory does not exist: "
                            + directory
            );
        }
        Path receptorPath = directory.resolve("target_protein.pdb");
        if (!Files.isRegularFile(receptorPath)) {
            throw new IllegalStateException(
                    "No target_protein.pdb in " + directory
            );
        }

        Structure receptor;
        try {
            receptor = structureReader.read(receptorPath);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Receptor cannot be loaded from " + receptorPath
                            + ": " + exception.getMessage(),
                    exception
            );
        }

        StructureArtifactProjection match = matchStructure(
                directory, receptorPath);
        CandidatePockets candidates =
                poseAnalysis.candidatePocketsForArtifact(
                        match,
                        receptor,
                        receptorPath,
                        directory.getFileName() + "/target_protein.pdb"
                );
        CoordinateProvenance provenance = candidates.provenance();
        if (!provenance.sphereMetricsAvailable()) {
            throw new IllegalStateException(
                    "Occupancy scan refused: " + provenance.note()
            );
        }

        String homologousKey = String.valueOf(homologousPocketId);
        Pocket homologous = candidates.pockets().stream()
                .filter(pocket -> pocket.id().value()
                        .equals(homologousKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Homologous pocket id " + homologousPocketId
                                + " is not a candidate pocket of"
                                + " structure "
                                + match.getStructureId()
                ));
        List<AlphaSphere> homologousSpheres = homologous.alphaSphereSet()
                .map(AlphaSphereSet::spheres)
                .orElse(List.of());

        List<Row> rows = new ArrayList<>();
        for (int rank = 1; rank <= ranks; rank++) {
            rows.add(scanRank(
                    directory, rank, receptor, candidates,
                    homologous, homologousSpheres));
        }
        return render(
                directory, match, provenance, homologous,
                homologousSpheres, rows);
    }

    /** One scanned rank. */
    private record Row(
            int rank,
            Double confidence,
            PosePocketAssignment assignment,
            double centroidToHomologousAngstroms,
            double homologousSurfaceDistanceAngstroms
    ) {
    }

    private Row scanRank(
            Path directory,
            int rank,
            Structure receptor,
            CandidatePockets candidates,
            Pocket homologous,
            List<AlphaSphere> homologousSpheres
    ) {
        Path poseFile = MutationPoseReportService.resolveRankSdf(
                directory, "rank " + rank, rank);
        Ligand pose;
        try {
            pose = sdfReader.read(poseFile);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Rank " + rank + " pose cannot be loaded from "
                            + poseFile + ": " + exception.getMessage(),
                    exception
            );
        }
        Double confidence =
                MutationPoseReportService.confidenceFromSdfFilename(
                        poseFile.getFileName().toString());

        List<LigandContact> contacts =
                contactAnalyzer.analyze(receptor, pose);
        PosePocketAssignment assignment = poseAssigner.assign(
                receptor, candidates.pockets(), pose, contacts);

        Point3D centroid = heavyAtomCentroid(pose);
        return new Row(
                rank,
                confidence,
                assignment,
                centroid.distance(homologous.center()),
                nearestSurfaceDistance(centroid, homologousSpheres)
        );
    }

    private StructureArtifactProjection matchStructure(
            Path directory,
            Path receptorPath
    ) {
        StructureArtifactProjection match;
        try {
            match = poseAnalysis.findStructureArtifactByContent(
                    receptorPath).orElse(null);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot hash " + receptorPath + ": "
                            + exception.getMessage(),
                    exception
            );
        }
        if (match == null) {
            throw new IllegalStateException(
                    "No pocket-bearing DB structure artifact has the"
                            + " same content (sha256) as "
                            + receptorPath + "; pockets cannot be"
                            + " resolved by provenance and no"
                            + " accession-based fallback is allowed"
            );
        }
        return match;
    }

    private String render(
            Path directory,
            StructureArtifactProjection match,
            CoordinateProvenance provenance,
            Pocket homologous,
            List<AlphaSphere> homologousSpheres,
            List<Row> rows
    ) {
        StringBuilder report = new StringBuilder();
        report.append("V6 occupancy scan — computational pose evidence")
                .append(" (never binding claims)\n");
        report.append("Directory: ").append(directory).append('\n');
        report.append("Receptor: target_protein.pdb matched DB")
                .append(" structure ").append(match.getStructureId())
                .append(" (artifact ").append(match.getArtifactId())
                .append(", ").append(match.getSourceAccession())
                .append("); compatibility ")
                .append(provenance.compatibility())
                .append("; sphere metrics ")
                .append(provenance.sphereMetricsAvailable()
                        ? "AVAILABLE"
                        : "NOT_AVAILABLE")
                .append('\n');
        report.append("Homologous-site pocket: ")
                .append(homologous.name())
                .append(" (id ").append(homologous.id().value())
                .append(", ").append(homologousSpheres.size())
                .append(" spheres); geometric criterion: pose centroid"
                        + " within ")
                .append(String.format(Locale.ROOT, "%.1f",
                        HOMOLOGOUS_CENTROID_SURFACE_ANGSTROMS))
                .append(" A of its sphere cloud (surface distance)\n\n");

        report.append("rank | confidence | assigned pocket | score")
                .append(" | ambiguous | status | centroid->homolog")
                .append(" | contact coverage | sphere occupancy")
                .append(" | homolog-surface | IN_HOMOLOGOUS_SITE\n");
        for (Row row : rows) {
            PosePocketAssignment assignment = row.assignment();
            report.append(row.rank())
                    .append(" | ")
                    .append(row.confidence() == null
                            ? "n/a"
                            : String.format(Locale.ROOT, "%.4f",
                                    row.confidence()))
                    .append(" | ")
                    .append(assignment.pocket() == null
                            ? "-"
                            : assignment.pocket().name() + " (id "
                                    + assignment.pocket().id().value()
                                    + ")")
                    .append(" | ")
                    .append(assignment.assignmentScore() == null
                            ? "-"
                            : String.format(Locale.ROOT, "%.3f",
                                    assignment.assignmentScore()))
                    .append(" | ")
                    .append(assignment.ambiguous())
                    .append(" | ")
                    .append(assignment.status())
                    .append(" | ")
                    .append(String.format(Locale.ROOT, "%.2f",
                            row.centroidToHomologousAngstroms()))
                    .append(" | ")
                    .append(assignment.bestMetrics() == null
                            ? "-"
                            : String.format(Locale.ROOT, "%.3f",
                                    assignment.bestMetrics()
                                            .contactResidueCoverage()))
                    .append(" | ")
                    .append(sphereOccupancy(assignment))
                    .append(" | ")
                    .append(String.format(Locale.ROOT, "%.2f",
                            row.homologousSurfaceDistanceAngstroms()))
                    .append(" | ")
                    .append(inHomologous(row, homologous))
                    .append('\n');
        }

        appendGrouping(report, rows);
        appendVerdicts(report, rows, homologous);
        return report.toString();
    }

    private static String sphereOccupancy(
            PosePocketAssignment assignment
    ) {
        if (assignment.bestMetrics() == null
                || assignment.bestMetrics().spheres() == null) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.3f",
                assignment.bestMetrics().atomContainmentFraction());
    }

    private static String inHomologous(Row row, Pocket homologous) {
        boolean byAssignment = row.assignment().pocket() != null
                && row.assignment().pocket().id()
                        .equals(homologous.id());
        boolean byGeometry = row.homologousSurfaceDistanceAngstroms()
                <= HOMOLOGOUS_CENTROID_SURFACE_ANGSTROMS;
        if (byAssignment && byGeometry) {
            return "yes (assigned+geometry)";
        }
        if (byAssignment) {
            return "yes (assigned)";
        }
        if (byGeometry) {
            return "yes (geometry only)";
        }
        return "no";
    }

    private static void appendGrouping(
            StringBuilder report,
            List<Row> rows
    ) {
        Map<String, List<Row>> byPocket = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.assignment().pocket() == null) {
                continue;
            }
            byPocket.computeIfAbsent(
                    row.assignment().pocket().name() + " (id "
                            + row.assignment().pocket().id().value()
                            + ")",
                    key -> new ArrayList<>()
            ).add(row);
        }

        report.append("\nGroup by assigned pocket (fraction of ")
                .append(rows.size())
                .append(" ranks; confidence is DiffDock confidence,"
                        + " not affinity):\n");
        report.append("pocket | poses | fraction | best confidence")
                .append(" | median confidence\n");
        byPocket.entrySet().stream()
                .sorted(Comparator.comparingInt(
                        (Map.Entry<String, List<Row>> entry) ->
                                entry.getValue().size()).reversed())
                .forEach(entry -> {
                    List<Double> confidences = entry.getValue().stream()
                            .map(Row::confidence)
                            .filter(Objects::nonNull)
                            .sorted(Comparator.reverseOrder())
                            .toList();
                    report.append(entry.getKey())
                            .append(" | ")
                            .append(entry.getValue().size())
                            .append(" | ")
                            .append(String.format(Locale.ROOT, "%.3f",
                                    entry.getValue().size()
                                            / (double) rows.size()))
                            .append(" | ")
                            .append(confidences.isEmpty()
                                    ? "n/a"
                                    : String.format(Locale.ROOT, "%.4f",
                                            confidences.getFirst()))
                            .append(" | ")
                            .append(confidences.isEmpty()
                                    ? "n/a"
                                    : String.format(Locale.ROOT, "%.4f",
                                            median(confidences)))
                            .append('\n');
                });
    }

    private void appendVerdicts(
            StringBuilder report,
            List<Row> rows,
            Pocket homologous
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.assignment().pocket() != null) {
                counts.merge(row.assignment().pocket().name()
                        + " (id "
                        + row.assignment().pocket().id().value() + ")",
                        1L, Long::sum);
            }
        }
        String dominant = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("none");
        List<Integer> homologousRanks = rows.stream()
                .filter(row -> row.assignment().pocket() != null
                        && row.assignment().pocket().id()
                                .equals(homologous.id()))
                .map(Row::rank)
                .toList();
        List<Integer> geometricRanks = rows.stream()
                .filter(row -> row.homologousSurfaceDistanceAngstroms()
                        <= HOMOLOGOUS_CENTROID_SURFACE_ANGSTROMS)
                .map(Row::rank)
                .toList();

        report.append("\nVerdicts:\n");
        report.append("  dominant pocket: ").append(dominant)
                .append(" (").append(counts.getOrDefault(dominant, 0L))
                .append(" of ").append(rows.size()).append(" ranks)\n");
        report.append("  ranks assigned to the homologous pocket ")
                .append(homologous.id().value()).append(": ")
                .append(homologousRanks.isEmpty()
                        ? "none"
                        : homologousRanks.toString())
                .append('\n');
        report.append("  ranks geometrically at the homologous site")
                .append(" (centroid within ")
                .append(String.format(Locale.ROOT, "%.1f",
                        HOMOLOGOUS_CENTROID_SURFACE_ANGSTROMS))
                .append(" A surface): ")
                .append(geometricRanks.isEmpty()
                        ? "none"
                        : geometricRanks.toString())
                .append('\n');
    }

    private static double median(List<Double> sortedDescending) {
        List<Double> sorted = sortedDescending.stream()
                .sorted()
                .toList();
        int size = sorted.size();
        int middle = size / 2;
        return size % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private static Point3D heavyAtomCentroid(Ligand pose) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        int count = 0;
        for (Chain chain : pose.structure().getChains()) {
            for (Residue residue : chain.residues()) {
                for (var atom : residue.getAtoms()) {
                    if (atom.isHeavyAtom()) {
                        x += atom.getPosition().x();
                        y += atom.getPosition().y();
                        z += atom.getPosition().z();
                        count++;
                    }
                }
            }
        }
        if (count == 0) {
            throw new IllegalStateException(
                    "Pose contains no heavy atoms");
        }
        return new Point3D(x / count, y / count, z / count);
    }

    private static double nearestSurfaceDistance(
            Point3D point,
            List<AlphaSphere> spheres
    ) {
        double nearest = Double.MAX_VALUE;
        for (AlphaSphere sphere : spheres) {
            nearest = Math.min(nearest, Math.max(
                    0.0,
                    point.distance(sphere.center()) - sphere.radius()));
        }
        return nearest == Double.MAX_VALUE ? Double.NaN : nearest;
    }
}
