package totah.lab.web.poseanalysis;

import org.springframework.stereotype.Service;
import totah.lab.athena.pocket.compare.KabschRigidPointAligner;
import totah.lab.athena.pocket.compare.MultiHypothesisPocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignmentResult;
import totah.lab.athena.pocket.compare.residue.PocketResiduePointFactory;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.StructureSequences;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.web.poseanalysis.PoseAnalysisService.CandidatePockets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Batch-side homology scan of the 7B v6 pocket that dominates the
 * DiffDock occupancy (FPOCKET pocket 8, DB id 10) against every 7A
 * pocket, plus the SAM/cofactor overlap of the pocket.
 *
 * <p>All structure loading goes through the provenance seams: the 7B
 * receptor comes from the DiffDock directory (hash-matched to its DB
 * structure artifact), the 7A receptor from the reference run's
 * artifact; pocket similarity uses the same multi-hypothesis pocket
 * alignment + comparison machinery (NW sequence-seeded) the
 * cross-protein pose comparator uses. The SAM complex's frame is
 * established before use by a CA Kabsch fit against the canonical
 * receptor with the {@link PocketFrameValidator} thresholds — if the
 * fit fails, the SAM overlap is reported NOT_AVAILABLE, never assumed.
 * Computational geometry evidence only; no binding claims.</p>
 */
@Service
public class Pocket8HomologyService {

    /**
     * Homology verdict threshold on overall pocket similarity —
     * calibration-pending, consistent with the cross-protein pose
     * comparator's default.
     */
    static final double HOMOLOGY_SIMILARITY_THRESHOLD = 0.3;

    /** SAM contact cutoff (angstroms) within the complex. */
    static final double SAM_CONTACT_CUTOFF_ANGSTROMS = 4.5;

    private static final String SAM_RESIDUE_NAME = "SAM";

    private final PoseAnalysisService poseAnalysis;
    private final PoseAnalysisRepository repository;

    private final PdbReader structureReader =
            new PdbReader();

    public Pocket8HomologyService(
            PoseAnalysisService poseAnalysis,
            PoseAnalysisRepository repository
    ) {
        this.poseAnalysis =
                Objects.requireNonNull(poseAnalysis, "poseAnalysis");
        this.repository =
                Objects.requireNonNull(repository, "repository");
    }

    /** One row of the homology table. */
    private record HomologyRow(
            Pocket pocket,
            double overallSimilarity,
            double geometrySimilarity,
            double sizeSimilarity,
            double meanBidirectionalDistance
    ) {
        boolean homologous() {
            return overallSimilarity >= HOMOLOGY_SIMILARITY_THRESHOLD;
        }
    }

    /**
     * The full pocket-8 homology report.
     *
     * @throws IllegalStateException when any required input cannot be
     *         resolved — the message states exactly what is missing
     */
    public String report(
            Path diffdockDir,
            long referenceRunId,
            long queryPocketId,
            long controlPocketBId,
            long controlPocketAId,
            Path samComplexB,
            Path samComplexA
    ) {
        // 7B side: the canonical v6 receptor from the directory,
        // hash-matched to its DB structure.
        Structure receptorB = loadDirReceptor(diffdockDir);
        StructureArtifactProjection matchB = matchStructure(
                diffdockDir, diffdockDir.resolve("target_protein.pdb"));
        CandidatePockets candidatesB =
                poseAnalysis.candidatePocketsForArtifact(
                        matchB,
                        receptorB,
                        diffdockDir.resolve("target_protein.pdb"),
                        diffdockDir.getFileName() + "/target_protein.pdb"
                );
        Pocket queryPocket = pocketById(candidatesB, queryPocketId,
                "query");

        // 7A side: the reference run's receptor artifact through the
        // poseanalysis seams.
        PoseRunProjection runA = repository.findRun(referenceRunId)
                .orElseThrow(() -> new IllegalStateException(
                        "No docking run " + referenceRunId
                                + " (7A reference)"
                ));
        Structure receptorA;
        try {
            receptorA = poseAnalysis.receptorStructure(
                    runA, new HashMap<>());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "7A receptor of run " + referenceRunId
                            + " cannot be loaded: "
                            + exception.getMessage(),
                    exception
            );
        }
        CandidatePockets candidatesA = poseAnalysis.candidatePockets(
                runA, receptorA, new HashMap<>());

        List<HomologyRow> rows = new ArrayList<>();
        for (Pocket candidate : candidatesA.pockets()) {
            if (candidate.alphaSphereSet()
                    .map(AlphaSphereSet::spheres)
                    .map(List::size)
                    .orElse(0) < 3) {
                continue;
            }
            PocketAlignmentResult alignment =
                    alignPockets(
                            receptorA, candidate,
                            receptorB, queryPocket);
            rows.add(new HomologyRow(
                    candidate,
                    alignment.comparison().overallSimilarity(),
                    alignment.comparison().geometrySimilarity(),
                    alignment.comparison().sizeSimilarity(),
                    alignment.comparison().meanBidirectionalDistance()
            ));
        }
        rows.sort(Comparator.comparingDouble(
                HomologyRow::overallSimilarity).reversed()
                .thenComparing(row -> row.pocket().id().value()));

        Pocket controlB = pocketById(candidatesB, controlPocketBId,
                "control (7B pocket 3)");
        Pocket controlA = pocketById(candidatesA, controlPocketAId,
                "control (7A pocket 1)");
        PocketAlignmentResult control =
                alignPockets(
                        receptorB, controlB, receptorA, controlA);

        String samB = samOverlap(
                "7B", samComplexB, receptorB, queryPocket);
        String samA = samOverlap(
                "7A", samComplexA, receptorA, controlA);

        return render(
                diffdockDir, matchB, candidatesB, runA, candidatesA,
                queryPocket, rows, controlB, controlA, control,
                samB, samA);
    }

    private Structure loadDirReceptor(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(
                    "DiffDock directory does not exist: " + directory);
        }
        Path receptorPath = directory.resolve("target_protein.pdb");
        if (!Files.isRegularFile(receptorPath)) {
            throw new IllegalStateException(
                    "No target_protein.pdb in " + directory);
        }
        try {
            return structureReader.read(receptorPath);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Receptor cannot be loaded from " + receptorPath
                            + ": " + exception.getMessage(),
                    exception);
        }
    }

    private StructureArtifactProjection matchStructure(
            Path directory, Path receptorPath) {
        StructureArtifactProjection match;
        try {
            match = poseAnalysis.findStructureArtifactByContent(
                    receptorPath).orElse(null);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot hash " + receptorPath + ": "
                            + exception.getMessage(), exception);
        }
        if (match == null) {
            throw new IllegalStateException(
                    "No pocket-bearing DB structure artifact has the"
                            + " same content (sha256) as "
                            + receptorPath + "; refusing to resolve"
                            + " pockets by accession"
            );
        }
        return match;
    }

    private static Pocket pocketById(
            CandidatePockets candidates, long pocketId, String role) {
        String key = String.valueOf(pocketId);
        return candidates.pockets().stream()
                .filter(pocket -> pocket.id().value().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "The " + role + " pocket (DB id " + pocketId
                                + ") is not a candidate pocket"));
    }

    /**
     * The SAM/cofactor overlap of one pocket, or a NOT_AVAILABLE
     * explanation. The complex's frame is proven by a CA Kabsch fit
     * against the canonical receptor before any coordinate is used.
     */
    private String samOverlap(
            String side,
            Path samComplex,
            Structure receptor,
            Pocket pocket
    ) {
        String prefix = side + ": ";
        if (samComplex == null || !Files.isRegularFile(samComplex)) {
            return prefix + "SAM overlap NOT_AVAILABLE (no SAM complex"
                    + " file at " + samComplex + ")";
        }
        Structure complex;
        try {
            complex = structureReader.read(samComplex);
        } catch (IOException | RuntimeException exception) {
            return prefix + "SAM overlap NOT_AVAILABLE (complex "
                    + samComplex.getFileName() + " cannot be loaded: "
                    + exception.getMessage() + ")";
        }

        Map<String, Point3D> receptorCalphas = calphas(receptor);
        Map<String, Point3D> complexCalphas = calphas(complex);
        List<String> common = new ArrayList<>(
                receptorCalphas.keySet());
        common.retainAll(complexCalphas.keySet());
        common.sort(null);
        if (common.size() < PocketFrameValidator.MIN_MATCHED_CA_PAIRS) {
            return prefix + "SAM overlap NOT_AVAILABLE (only "
                    + common.size() + " matched CA pairs between the"
                    + " complex and the canonical receptor, < "
                    + PocketFrameValidator.MIN_MATCHED_CA_PAIRS + ")";
        }
        List<Point3D> complexPoints = common.stream()
                .map(complexCalphas::get).toList();
        List<Point3D> receptorPoints = common.stream()
                .map(receptorCalphas::get).toList();
        RigidTransform transform = new KabschRigidPointAligner()
                .align(complexPoints, receptorPoints);
        double rmsd = rmsd(
                transform.apply(complexPoints), receptorPoints);
        if (rmsd > PocketFrameValidator.MAX_VALIDATED_RMSD_ANGSTROMS) {
            return prefix + "SAM overlap NOT_AVAILABLE (the complex is"
                    + " in a different frame: CA fit RMSD "
                    + String.format(Locale.ROOT, "%.2f", rmsd)
                    + " A over " + common.size() + " pairs exceeds "
                    + PocketFrameValidator.MAX_VALIDATED_RMSD_ANGSTROMS
                    + " A)";
        }

        // SAM heavy atoms, moved into the canonical frame (a no-op
        // when the complex already shares it).
        List<Point3D> samAtoms = new ArrayList<>();
        for (Chain chain : complex.getChains()) {
            for (Residue residue : chain.residues()) {
                if (!SAM_RESIDUE_NAME.equalsIgnoreCase(
                        residue.getName())) {
                    continue;
                }
                for (Atom atom : residue.getAtoms()) {
                    if (atom.isHeavyAtom()) {
                        samAtoms.add(transform.apply(
                                atom.getPosition()));
                    }
                }
            }
        }
        if (samAtoms.isEmpty()) {
            return prefix + "SAM overlap NOT_AVAILABLE (no SAM residue"
                    + " in " + samComplex.getFileName() + ")";
        }

        // SAM-contacting protein residues at 4.5 A, computed inside
        // the complex's own frame (identical result after the rigid
        // transform).
        java.util.Set<ResidueId> samContacts = new TreeSet<>(
                Comparator.comparing(ResidueId::chainId)
                        .thenComparingInt(ResidueId::residueNumber));
        for (Chain chain : complex.getChains()) {
            for (Residue residue : chain.residues()) {
                if (SAM_RESIDUE_NAME.equalsIgnoreCase(
                        residue.getName())) {
                    continue;
                }
                boolean contacts = residue.getAtoms().stream()
                        .filter(Atom::isHeavyAtom)
                        .anyMatch(atom -> samAtoms.stream()
                                .anyMatch(sam -> atom.getPosition()
                                        .distance(sam)
                                        <= SAM_CONTACT_CUTOFF_ANGSTROMS));
                if (contacts) {
                    samContacts.add(new ResidueId(
                            chain.id(),
                            residue.getNumber(),
                            residue.getInsertionCode()));
                }
            }
        }

        double nearest = Double.NaN;
        List<AlphaSphere> spheres = pocket.alphaSphereSet()
                .map(AlphaSphereSet::spheres)
                .orElse(List.of());
        for (AlphaSphere sphere : spheres) {
            for (Point3D sam : samAtoms) {
                double surface = Math.max(0.0,
                        sphere.center().distance(sam)
                                - sphere.radius());
                if (Double.isNaN(nearest) || surface < nearest) {
                    nearest = surface;
                }
            }
        }

        List<Integer> overlap = new ArrayList<>();
        for (ResidueId residue : pocket.residues()) {
            if (samContacts.contains(residue)) {
                overlap.add(residue.residueNumber());
            }
        }

        StringBuilder result = new StringBuilder(prefix);
        result.append("complex ").append(samComplex.getFileName())
                .append(" — CA fit RMSD ")
                .append(String.format(Locale.ROOT, "%.2f", rmsd))
                .append(" A over ").append(common.size())
                .append(" pairs (frame validated); ")
                .append(pocket.name())
                .append(" sphere cloud to nearest SAM heavy atom: ")
                .append(Double.isNaN(nearest)
                        ? "n/a (no spheres)"
                        : String.format(Locale.ROOT, "%.2f A", nearest))
                .append("; SAM-contact residues (")
                .append(String.format(Locale.ROOT, "%.1f",
                        SAM_CONTACT_CUTOFF_ANGSTROMS))
                .append(" A): ").append(samContacts.isEmpty()
                        ? "none"
                        : samContacts.stream()
                                .map(ResidueId::residueNumber)
                                .sorted()
                                .toList())
                .append("; pocket residues contacting SAM: ")
                .append(overlap.isEmpty() ? "none" : overlap.toString())
                .append(" -> overlaps SAM region: ")
                .append(overlap.isEmpty() ? "no" : "yes");
        return result.toString();
    }

    private String render(
            Path diffdockDir,
            StructureArtifactProjection matchB,
            CandidatePockets candidatesB,
            PoseRunProjection runA,
            CandidatePockets candidatesA,
            Pocket queryPocket,
            List<HomologyRow> rows,
            Pocket controlB,
            Pocket controlA,
            PocketAlignmentResult control,
            String samB,
            String samA
    ) {
        StringBuilder report = new StringBuilder();
        report.append("Pocket 8 homology scan — computational")
                .append(" pocket-similarity evidence (never binding")
                .append(" claims)\n");
        report.append("Query: 7B ").append(queryPocket.name())
                .append(" (id ").append(queryPocket.id().value())
                .append(") on structure ").append(matchB.getStructureId())
                .append(" (").append(matchB.getSourceAccession())
                .append("); 7B receptor: ")
                .append(diffdockDir)
                .append("/target_protein.pdb — compatibility ")
                .append(candidatesB.provenance().compatibility())
                .append("; sphere metrics ")
                .append(candidatesB.provenance().sphereMetricsAvailable()
                        ? "AVAILABLE"
                        : "NOT_AVAILABLE")
                .append('\n');
        report.append("Reference: 7A run ").append(runA.getId())
                .append(" (").append(runA.getTargetName())
                .append(", receptor artifact ")
                .append(runA.getReceptorArtifactId())
                .append(") — compatibility ")
                .append(candidatesA.provenance().compatibility())
                .append("; sphere metrics ")
                .append(candidatesA.provenance().sphereMetricsAvailable()
                        ? "AVAILABLE"
                        : "NOT_AVAILABLE")
                .append('\n');
        report.append('\n');

        report.append(queryPocket.name())
                .append(" vs every 7A FPOCKET pocket (homologous =")
                .append(" overall similarity >= ")
                .append(String.format(Locale.ROOT, "%.2f",
                        HOMOLOGY_SIMILARITY_THRESHOLD))
                .append(", calibration-pending):\n");
        report.append("7A pocket | overall | geometry | size | mean")
                .append(" bidirectional | homologous\n");
        for (HomologyRow row : rows) {
            report.append(row.pocket().name())
                    .append(" (id ").append(row.pocket().id().value())
                    .append(") | ")
                    .append(String.format(Locale.ROOT, "%.3f",
                            row.overallSimilarity()))
                    .append(" | ")
                    .append(String.format(Locale.ROOT, "%.3f",
                            row.geometrySimilarity()))
                    .append(" | ")
                    .append(String.format(Locale.ROOT, "%.3f",
                            row.sizeSimilarity()))
                    .append(" | ")
                    .append(String.format(Locale.ROOT, "%.2f A",
                            row.meanBidirectionalDistance()))
                    .append(" | ")
                    .append(row.homologous() ? "yes" : "no")
                    .append('\n');
        }

        report.append('\n');
        if (rows.isEmpty()) {
            report.append("Verdict: no 7A FPOCKET pockets with alpha")
                    .append(" spheres to compare against\n");
        } else {
            HomologyRow best = rows.getFirst();
            report.append("Verdict: ");
            if (best.homologous()) {
                report.append(queryPocket.name())
                        .append(" has a 7A homolog: ")
                        .append(best.pocket().name())
                        .append(" (id ").append(best.pocket().id().value())
                        .append(", overall similarity ")
                        .append(String.format(Locale.ROOT, "%.3f",
                                best.overallSimilarity()))
                        .append(")");
            } else {
                report.append(queryPocket.name())
                        .append(" has NO clear 7A homolog (best: ")
                        .append(best.pocket().name())
                        .append(" id ").append(best.pocket().id().value())
                        .append(", overall similarity ")
                        .append(String.format(Locale.ROOT, "%.3f",
                                best.overallSimilarity()))
                        .append(" < ")
                        .append(String.format(Locale.ROOT, "%.2f",
                                HOMOLOGY_SIMILARITY_THRESHOLD))
                        .append(") — the pocket is effectively absent"
                                + " in 7A");
            }
            report.append('\n');
        }

        report.append("Positive control: 7B ").append(controlB.name())
                .append(" (id ").append(controlB.id().value())
                .append(") vs 7A ").append(controlA.name())
                .append(" (id ").append(controlA.id().value())
                .append("): overall ")
                .append(String.format(Locale.ROOT, "%.3f",
                        control.comparison().overallSimilarity()))
                .append(" — homologous ")
                .append(control.comparison().overallSimilarity()
                        >= HOMOLOGY_SIMILARITY_THRESHOLD
                        ? "yes (as expected)"
                        : "NO — unexpected, investigate before trusting"
                        + " this table")
                .append('\n');
        report.append('\n');

        report.append("SAM/cofactor overlap:\n");
        report.append("  ").append(samB).append('\n');
        report.append("  ").append(samA).append('\n');
        return report.toString();
    }

    /**
     * Pocket alignment: alpha-sphere point clouds, residue points and
     * a Needleman-Wunsch sequence alignment feed the multi-hypothesis
     * aligner — the same composition the architecture facade uses
     * internally (its helper is package-private in athena).
     */
    private static PocketAlignmentResult alignPockets(
            Structure receptorA,
            Pocket pocketA,
            Structure receptorB,
            Pocket pocketB
    ) {
        PocketResiduePointFactory residuePoints =
                new PocketResiduePointFactory();
        return new MultiHypothesisPocketAligner().align(
                PocketPointCloud.from(receptorA, pocketA),
                PocketPointCloud.from(receptorB, pocketB),
                residuePoints.create(receptorA, pocketA),
                residuePoints.create(receptorB, pocketB),
                new NeedlemanWunschSequenceAligner().align(
                        StructureSequences.sequenceResidues(receptorA),
                        StructureSequences.sequenceResidues(receptorB)
                )
        );
    }

    private static Map<String, Point3D> calphas(Structure structure) {
        Map<String, Point3D> positions = new HashMap<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                residue.findAtom("CA").ifPresent(atom ->
                        positions.putIfAbsent(
                                chain.id() + ":" + residue.getNumber(),
                                atom.getPosition()));
            }
        }
        return positions;
    }

    private static double rmsd(
            List<Point3D> moved, List<Point3D> fixed) {
        double sum = 0.0;
        for (int index = 0; index < fixed.size(); index++) {
            sum += fixed.get(index).distanceSquared(moved.get(index));
        }
        return Math.sqrt(sum / fixed.size());
    }
}
