package totah.lab.web.poseanalysis;

import org.springframework.stereotype.Service;
import totah.lab.athena.ligand.contact.DefaultContactAnalyzer;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.ligand.pose.CrossProteinPoseComparison;
import totah.lab.athena.ligand.pose.DefaultCrossProteinPoseComparator;
import totah.lab.athena.ligand.pose.DefaultPosePocketAssigner;
import totah.lab.athena.ligand.pose.PosePocketAssigner;
import totah.lab.athena.ligand.pose.PosePocketAssignment;
import totah.lab.athena.ligand.selectivity.DefaultMutationPoseComparator;
import totah.lab.athena.ligand.selectivity.MutationPoseComparator;
import totah.lab.athena.ligand.selectivity.MutationPoseComparison;
import totah.lab.athena.ligand.selectivity.PoseReferenceSimilarity;
import totah.lab.athena.ligand.selectivity.PoseReferenceSimilarityAnalyzer;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;
import totah.lab.web.poseanalysis.PoseAnalysisService.CandidatePockets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Batch-side mutation-pose report: for each DiffDock mutant directory
 * (a {@code target_protein.pdb} mutant receptor plus ranked pose SDFs)
 * the top-ranked mutant pose is compared against the wild-type 7A and
 * 7B reference poses persisted in the docking tables, and classified
 * as computationally closer to one or the other.
 *
 * <p>Loading reuses the poseanalysis seams for the WT side (receptor
 * artifact, pose file, candidate pockets, pocket assignment); the
 * mutant receptor is read with hermes' {@link PdbReader}
 * and the pose with {@link SdfLigandReader}. The same-frame comparison
 * assumes the mutant receptor is on the WT 7A frame; this is CHECKED
 * per mutant (per-residue CA displacement against the WT receptor) and
 * the measured displacement is printed as a caveat when the frames
 * differ, never silently assumed. Docking confidences are parsed from
 * the pose labels / SDF filenames and carried as data only — they
 * never feed any classification. All output speaks of computational
 * pose evidence; nothing here is a selectivity determinant.</p>
 */
@Service
public class MutationPoseReportService {

    private static final Pattern STANDARD_LABEL =
            Pattern.compile("(?:^|[^A-Za-z0-9])([A-Z]\\d+[A-Z])"
                    + "(?:[^A-Za-z0-9]|$)");
    private static final Pattern LOWERCASE_LABEL =
            Pattern.compile("(?:^|[^A-Za-z0-9])([a-z])([a-z])(\\d+)"
                    + "(?:[^A-Za-z0-9]|$)");
    private static final Pattern SPEC_PATTERN =
            Pattern.compile("[A-Z]\\d+[A-Z]");
    private static final Pattern SDF_CONFIDENCE =
            Pattern.compile("confidence(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LABEL_CONFIDENCE =
            Pattern.compile("conf(-?\\d+(?:\\.\\d+)?)");

    private final PoseAnalysisService poseAnalysis;
    private final PoseAnalysisRepository repository;

    private final DefaultContactAnalyzer contactAnalyzer =
            new DefaultContactAnalyzer();
    private final PosePocketAssigner poseAssigner =
            new DefaultPosePocketAssigner();
    private final DefaultCrossProteinPoseComparator crossComparator =
            new DefaultCrossProteinPoseComparator();
    private final MutationPoseComparator sameFrameComparator =
            new DefaultMutationPoseComparator();
    private final PoseReferenceSimilarityAnalyzer similarityAnalyzer =
            new PoseReferenceSimilarityAnalyzer();
    private final PdbReader structureReader =
            new PdbReader();
    private final SdfLigandReader sdfReader = new SdfLigandReader();

    public MutationPoseReportService(
            PoseAnalysisService poseAnalysis,
            PoseAnalysisRepository repository
    ) {
        this.poseAnalysis =
                Objects.requireNonNull(poseAnalysis, "poseAnalysis");
        this.repository =
                Objects.requireNonNull(repository, "repository");
    }

    /** One wild-type reference side (7A or 7B), fully loaded. */
    private record WtSide(
            String name,
            PoseProjection pose,
            PoseRunProjection run,
            Structure receptor,
            Ligand ligand,
            List<LigandContact> contacts,
            CandidatePockets candidates,
            PosePocketAssignment assignment,
            Double confidence
    ) {
    }

    /** One loaded mutant DiffDock directory. */
    private record MutantInput(
            String label,
            Path directory,
            Path poseFile,
            Structure receptor,
            Ligand pose,
            Double confidence,
            List<LigandContact> contacts,
            PosePocketAssignment assignment
    ) {
    }

    /**
     * One mutant-dirs entry: the DiffDock result directory plus an
     * optional explicit mutation label ({@code /path/dir=F43L} or, for
     * a composite, {@code /path/dir=F39L+L40M+V41A+R42V+F43L}). Entries
     * without {@code =<label>} fall back to parsing the label from the
     * directory name.
     */
    public record MutantDirEntry(Path directory, String labelOverride) {

        public MutantDirEntry {
            Objects.requireNonNull(directory, "directory");
        }

        public static MutantDirEntry parse(String entry) {
            if (entry == null || entry.isBlank()) {
                throw new IllegalStateException(
                        "Empty mutant-dirs entry"
                );
            }
            String trimmed = entry.trim();
            int separator = trimmed.lastIndexOf('=');
            if (separator < 0) {
                return new MutantDirEntry(Path.of(trimmed), null);
            }
            Path directory = Path.of(trimmed.substring(0, separator));
            String label = trimmed.substring(separator + 1).trim();
            if (label.isEmpty()) {
                throw new IllegalStateException(
                        "Empty mutation label in mutant-dirs entry: "
                                + entry
                );
            }
            // Validates the spec(s) eagerly, so a malformed label fails
            // before any file is read.
            labelSpecs(label);
            return new MutantDirEntry(directory, label);
        }
    }

    /**
     * The full mutation-pose report for the given mutant directories.
     *
     * @throws IllegalStateException when a WT pose, a mutant directory,
     *         a rank SDF or a receptor cannot be resolved — the message
     *         states exactly what is missing
     */
    public String report(
            List<MutantDirEntry> mutantDirs,
            long poseAId,
            long poseBId,
            int rank,
            List<String> expectedMutations
    ) {
        if (mutantDirs == null || mutantDirs.isEmpty()) {
            throw new IllegalStateException(
                    "At least one mutant directory is required"
            );
        }

        Map<Path, Structure> receptorCache = new HashMap<>();
        Map<String, List<PdbqtModel>> modelCache = new HashMap<>();
        Map<String, CandidatePockets> pocketCache = new HashMap<>();

        WtSide sideA = loadWtSide(
                "7A", poseAId, receptorCache, modelCache, pocketCache);
        WtSide sideB = loadWtSide(
                "7B", poseBId, receptorCache, modelCache, pocketCache);

        List<MutantInput> mutants = new ArrayList<>();
        for (MutantDirEntry entry : mutantDirs) {
            mutants.add(loadMutant(entry, rank, sideA));
        }

        StringBuilder report = new StringBuilder();
        report.append("Mutation pose report — computational pose")
                .append(" evidence (never selectivity determinants)\n");
        report.append("Reference WT 7A: pose ").append(sideA.pose().getId())
                .append(" \"").append(sideA.pose().getLigandLabel())
                .append("\" (run ").append(sideA.run().getId())
                .append(", DiffDock confidence ")
                .append(formatConfidence(sideA.confidence()))
                .append(")\n");
        report.append("Reference WT 7B: pose ").append(sideB.pose().getId())
                .append(" \"").append(sideB.pose().getLigandLabel())
                .append("\" (run ").append(sideB.run().getId())
                .append(", DiffDock confidence ")
                .append(formatConfidence(sideB.confidence()))
                .append(")\n");
        appendProvenance(report, "WT 7A",
                sideA.candidates().provenance());
        appendProvenance(report, "WT 7B",
                sideB.candidates().provenance());
        report.append('\n');

        appendAvailability(report, expectedMutations, mutants);

        List<MutationPoseComparison> vsAComparisons = new ArrayList<>();
        for (MutantInput mutant : mutants) {
            vsAComparisons.add(appendMutantSection(
                    report, sideA, sideB, mutant));
        }

        if (mutants.size() >= 2) {
            appendPairwiseSection(report, sideA, mutants);
        }

        return report.toString();
    }

    private WtSide loadWtSide(
            String name,
            long poseId,
            Map<Path, Structure> receptorCache,
            Map<String, List<PdbqtModel>> modelCache,
            Map<String, CandidatePockets> pocketCache
    ) {
        PoseProjection pose = repository.findPose(poseId)
                .orElseThrow(() -> new IllegalStateException(
                        "No docking pose " + poseId + " (WT " + name
                                + " reference)"
                ));
        PoseRunProjection run = poseAnalysis.runForPose(poseId);
        Structure receptor;
        Ligand ligand;
        try {
            receptor = poseAnalysis.receptorStructure(run, receptorCache);
            ligand = poseAnalysis.poseLigand(pose, modelCache);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "WT " + name + " reference (pose " + poseId
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
        return new WtSide(
                name,
                pose,
                run,
                receptor,
                ligand,
                contacts,
                candidates,
                assignment,
                confidenceFromPoseLabel(pose.getLigandLabel())
        );
    }

    private MutantInput loadMutant(
            MutantDirEntry entry,
            int rank,
            WtSide sideA
    ) {
        Path directory = entry.directory();
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(
                    "Mutant directory does not exist: " + directory
            );
        }
        String label = entry.labelOverride() != null
                ? entry.labelOverride()
                : mutationLabelFromDir(
                        directory.getFileName().toString());
        // Composite or single: validate the spec list up front.
        labelSpecs(label);

        Path receptorPath = directory.resolve("target_protein.pdb");
        if (!Files.isRegularFile(receptorPath)) {
            throw new IllegalStateException(
                    "Mutant " + label + ": no target_protein.pdb in "
                            + directory
            );
        }
        Path poseFile = resolveRankSdf(directory, label, rank);

        Structure receptor;
        Ligand pose;
        try {
            receptor = structureReader.read(receptorPath);
            pose = sdfReader.read(poseFile);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Mutant " + label + " cannot be loaded from "
                            + directory + ": " + exception.getMessage(),
                    exception
            );
        }
        validateMutantResidue(label, receptor, sideA);

        List<LigandContact> contacts =
                contactAnalyzer.analyze(receptor, pose);
        PosePocketAssignment assignment = poseAssigner.assign(
                receptor,
                sideA.candidates().pockets(),
                pose,
                contacts
        );
        return new MutantInput(
                label,
                directory,
                poseFile,
                receptor,
                pose,
                confidenceFromSdfFilename(
                        poseFile.getFileName().toString()),
                contacts,
                assignment
        );
    }

    private MutationPoseComparison appendMutantSection(
            StringBuilder report,
            WtSide sideA,
            WtSide sideB,
            MutantInput mutant
    ) {
        MutationPoseComparison vsA = sameFrameComparator.compareSameFrame(
                mutant.label(),
                sideA.receptor(),
                sideA.ligand(),
                sideA.contacts(),
                mutant.receptor(),
                mutant.pose(),
                mutant.contacts(),
                sideA.assignment(),
                mutant.assignment(),
                sideA.confidence(),
                mutant.confidence()
        );

        CrossProteinPoseComparison crossB = crossComparator.compare(
                mutant.label() + " pose",
                mutant.receptor(),
                mutant.assignment(),
                mutant.pose(),
                mutant.contacts(),
                "WT 7B pose",
                sideB.receptor(),
                sideB.assignment(),
                sideB.ligand(),
                sideB.contacts()
        );

        report.append("=== ").append(mutant.label())
                .append(" — ").append(mutant.directory()).append(" ===\n");
        report.append("Mutant receptor: target_protein.pdb (")
                .append(mutationDescription(mutant.label()))
                .append("; ").append(frameCheck(mutant, sideA))
                .append(")\n");
        report.append("Pose: ")
                .append(mutant.poseFile().getFileName())
                .append(" (DiffDock confidence ")
                .append(formatConfidence(mutant.confidence()))
                .append(" — a docking confidence, not an affinity;")
                .append(" never used for classification)\n");

        report.append("vs WT 7A (same frame): RMSD ")
                .append(formatDistance(vsA.alignedLigandRmsd()))
                .append("; centroid shift ")
                .append(formatDistance(vsA.alignedLigandCentroidShift()))
                .append("; rotation ")
                .append(vsA.ligandRotationAngle() == null
                        ? "n/a"
                        : String.format(Locale.ROOT, "%.2f deg",
                                vsA.ligandRotationAngle()))
                .append("; contact Jaccard ")
                .append(String.format(Locale.ROOT, "%.3f",
                        vsA.contactSetJaccard()))
                .append('\n');
        appendContactSets(report, vsA);

        report.append("Pocket: WT 7A ")
                .append(assignmentDescription(sideA.assignment()))
                .append(" -> mutant ")
                .append(assignmentDescription(mutant.assignment()))
                .append('\n');

        report.append("vs WT 7B (aligned through the pocket transform): ")
                .append(crossDescription(crossB))
                .append('\n');

        PoseReferenceSimilarity similarity = null;
        if (crossB.alignedLigandCentroidDistance() != null) {
            similarity = similarityAnalyzer.summarize(
                    vsA,
                    wrappedCrossComparison(mutant, sideB, crossB)
            );
            report.append("Classification: computationally the pose is ")
                    .append(similarity.classification())
                    .append(" — ").append(similarity.reason())
                    .append('\n');
        } else {
            report.append("Classification: unavailable — the WT 7B")
                    .append(" comparison could not run: ")
                    .append(crossB.reason())
                    .append('\n');
        }

        report.append("Confidence (DiffDock): WT 7A ")
                .append(formatConfidence(sideA.confidence()))
                .append(" -> mutant ")
                .append(formatConfidence(mutant.confidence()))
                .append('\n');
        report.append("Frame sanity: WT pose centroid ")
                .append(poseToPocketCenter(
                        sideA.ligand(), sideA.assignment()))
                .append(" from its assigned pocket center; mutant pose")
                .append(" centroid ")
                .append(poseToPocketCenter(
                        mutant.pose(), mutant.assignment()))
                .append(".\n\n");

        return vsA;
    }

    private void appendPairwiseSection(
            StringBuilder report,
            WtSide sideA,
            List<MutantInput> mutants
    ) {
        for (int index = 0; index + 1 < mutants.size(); index++) {
            MutantInput reference = mutants.get(index);
            MutantInput other = mutants.get(index + 1);
            String label = reference.label() + "-vs-" + other.label();
            MutationPoseComparison comparison =
                    sameFrameComparator.compareSameFrame(
                            label,
                            reference.receptor(),
                            reference.pose(),
                            reference.contacts(),
                            other.receptor(),
                            other.pose(),
                            other.contacts(),
                            reference.assignment(),
                            other.assignment(),
                            reference.confidence(),
                            other.confidence()
                    );
            report.append("=== ").append(label)
                    .append(" (both poses in the 7A frame) ===\n");
            report.append("RMSD ")
                    .append(formatDistance(
                            comparison.alignedLigandRmsd()))
                    .append("; centroid shift ")
                    .append(formatDistance(
                            comparison.alignedLigandCentroidShift()))
                    .append("; contact Jaccard ")
                    .append(String.format(Locale.ROOT, "%.3f",
                            comparison.contactSetJaccard()))
                    .append('\n');
            appendContactSets(report, comparison);
            report.append("Confidence (DiffDock): ")
                    .append(reference.label()).append(' ')
                    .append(formatConfidence(reference.confidence()))
                    .append(", ").append(other.label()).append(' ')
                    .append(formatConfidence(other.confidence()))
                    .append("\n\n");
        }
    }

    /** Frame provenance of one WT reference side. */
    private static void appendProvenance(
            StringBuilder report,
            String side,
            CoordinateProvenance provenance
    ) {
        report.append("Provenance ").append(side).append(": receptor")
                .append(" artifact ")
                .append(provenance.receptorArtifact() == null
                        ? "unknown"
                        : provenance.receptorArtifact().artifactId())
                .append("; pocket structure artifact ")
                .append(provenance.pocketStructureArtifact() == null
                        ? "unknown"
                        : provenance.pocketStructureArtifact()
                        .artifactId())
                .append("; compatibility ")
                .append(provenance.compatibility())
                .append("; sphere metrics ")
                .append(provenance.sphereMetricsAvailable()
                        ? "AVAILABLE"
                        : "NOT_AVAILABLE")
                .append(" — ").append(provenance.note())
                .append('\n');
    }

    private static void appendAvailability(
            StringBuilder report,
            List<String> expectedMutations,
            List<MutantInput> mutants
    ) {
        report.append("Mutant data availability:\n");
        LinkedHashSet<String> expected = new LinkedHashSet<>(
                expectedMutations == null
                        ? List.of()
                        : expectedMutations);
        for (MutantInput mutant : mutants) {
            expected.remove(mutant.label());
            report.append("  ").append(mutant.label())
                    .append(" — analyzed (").append(mutant.directory())
                    .append(")\n");
        }
        for (String missing : expected) {
            report.append("  ").append(missing)
                    .append(" — MISSING: no local DiffDock data;")
                    .append(" not analyzed\n");
        }
        report.append('\n');
    }

    private static void appendContactSets(
            StringBuilder report,
            MutationPoseComparison comparison
    ) {
        report.append("  contacts retained: ")
                .append(residueList(comparison.retainedContacts()))
                .append("; gained: ")
                .append(residueList(comparison.gainedContacts()))
                .append("; lost: ")
                .append(residueList(comparison.lostContacts()))
                .append('\n');
    }

    /**
     * Frame check: per-residue CA displacement between the mutant
     * receptor and the WT 7A receptor over all shared residues except
     * the mutated positions. Identical frames give exactly zero; a
     * nonzero displacement is reported as a caveat — the same-frame
     * numbers below then carry a systematic frame offset.
     */
    private String frameCheck(MutantInput mutant, WtSide sideA) {
        Set<Integer> positions = mutationPositions(mutant.label());
        Map<Integer, Point3D> wtCalphas = new HashMap<>();
        for (Chain chain : sideA.receptor().getChains()) {
            for (Residue residue : chain.residues()) {
                residue.findAtom("CA").ifPresent(atom ->
                        wtCalphas.putIfAbsent(
                                residue.getNumber(), atom.getPosition()));
            }
        }
        List<Double> displacements = new ArrayList<>();
        double maximum = 0.0;
        int maximumResidue = -1;
        for (Chain chain : mutant.receptor().getChains()) {
            for (Residue residue : chain.residues()) {
                if (positions.contains(residue.getNumber())) {
                    continue;
                }
                Point3D wt = wtCalphas.get(residue.getNumber());
                if (wt == null) {
                    continue;
                }
                var ca = residue.findAtom("CA");
                if (ca.isEmpty()) {
                    continue;
                }
                double displacement = wt.distance(ca.get().getPosition());
                displacements.add(displacement);
                if (displacement > maximum) {
                    maximum = displacement;
                    maximumResidue = residue.getNumber();
                }
            }
        }
        if (displacements.isEmpty()) {
            return "frame check unavailable (no shared CA atoms)";
        }
        displacements.sort(Double::compareTo);
        double median = displacements.get(displacements.size() / 2);
        String summary = String.format(
                Locale.ROOT,
                "frame check: %d shared CA atoms, median displacement"
                        + " %.3f A, max %.3f A (residue %d)",
                displacements.size(),
                median,
                maximum,
                maximumResidue
        );
        if (maximum < 1.0e-3) {
            return summary + " — identical to the WT 7A frame";
        }
        return summary + " — NOT identical to the WT 7A frame;"
                + " same-frame numbers below carry this systematic"
                + " offset (caveat)";
    }

    /**
     * Wraps the cross-protein comparison's aligned metrics into a
     * {@link MutationPoseComparison} for the reference-similarity
     * analyzer, which consumes only the shifts, RMSD and contact
     * Jaccard from the B side (the contact set detail lives on the
     * same-frame A comparison and in the cross comparison itself).
     */
    private static MutationPoseComparison wrappedCrossComparison(
            MutantInput mutant,
            WtSide sideB,
            CrossProteinPoseComparison crossB
    ) {
        return new MutationPoseComparison(
                mutant.label(),
                sideB.ligand().id(),
                mutant.pose().id(),
                crossB.alignedLigandCentroidDistance(),
                crossB.alignedLigandRmsd(),
                null,
                crossB.contactResidueSimilarity(),
                List.of(),
                List.of(),
                List.of(),
                mutant.assignment(),
                sideB.assignment(),
                crossB.relationship(),
                mutant.confidence(),
                sideB.confidence()
        );
    }

    /**
     * The mutation label from a DiffDock directory name: the standard
     * compact form ({@code diffdock_METTL7A-F43L} &rarr; {@code F43L})
     * or the lowercase reversed form ({@code diffdock_7a_lm40} &rarr;
     * {@code L40M}: first letter wild type, second letter mutant).
     */
    static String mutationLabelFromDir(String directoryName) {
        Matcher standard = STANDARD_LABEL.matcher(directoryName);
        if (standard.find()) {
            return standard.group(1);
        }
        Matcher lowercase = LOWERCASE_LABEL.matcher(directoryName);
        if (lowercase.find()) {
            return lowercase.group(1).toUpperCase(Locale.ROOT)
                    + lowercase.group(3)
                    + lowercase.group(2).toUpperCase(Locale.ROOT);
        }
        throw new IllegalStateException(
                "Cannot parse a mutation label from the directory name"
                        + " (expected ...-F43L or ..._lm40 style): "
                        + directoryName
        );
    }

    static Double confidenceFromPoseLabel(String ligandLabel) {
        Matcher matcher = LABEL_CONFIDENCE.matcher(
                ligandLabel == null ? "" : ligandLabel);
        return matcher.find()
                ? Double.parseDouble(matcher.group(1))
                : null;
    }

    static Double confidenceFromSdfFilename(String filename) {
        Matcher matcher = SDF_CONFIDENCE.matcher(
                filename == null ? "" : filename);
        return matcher.find()
                ? Double.parseDouble(matcher.group(1))
                : null;
    }

    /**
     * The {@code rankN_confidence-*.sdf} file of a DiffDock output
     * directory. Package-private: shared with the pocket-architecture
     * report's directory side.
     */
    static Path resolveRankSdf(
            Path directory,
            String label,
            int rank
    ) {
        String prefix = "rank" + rank + "_confidence";
        try (var stream = Files.list(directory)) {
            List<Path> matches = stream
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(prefix)
                                && name.endsWith(".sdf");
                    })
                    .toList();
            if (matches.size() == 1) {
                return matches.getFirst();
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    label + ": cannot list " + directory
                            + ": " + exception.getMessage(),
                    exception
            );
        }
        throw new IllegalStateException(
                label + ": no rank " + rank + " pose SDF"
                        + " (expected " + prefix + "*.sdf) in "
                        + directory
        );
    }

    /**
     * Validates the parsed label against the structures: every mutated
     * position of the (possibly composite) label must carry the mutant
     * residue in the mutant receptor and the wild-type residue in the
     * WT 7A receptor.
     */
    private static void validateMutantResidue(
            String label,
            Structure mutantReceptor,
            WtSide sideA
    ) {
        for (String spec : labelSpecs(label)) {
            int position = specPosition(spec);
            String mutantName = MutationPreparationService.threeLetter(
                    spec.substring(spec.length() - 1));
            String wildTypeName = MutationPreparationService.threeLetter(
                    spec.substring(0, 1));

            String actualMutant = residueNameAt(mutantReceptor, position);
            if (!mutantName.equals(actualMutant)) {
                throw new IllegalStateException(
                        "Mutant " + label + ": target_protein.pdb has "
                                + (actualMutant == null
                                        ? "no residue"
                                        : actualMutant)
                                + " at A:" + position + ", expected "
                                + mutantName + " (" + spec + ")"
                );
            }
            String actualWildType =
                    residueNameAt(sideA.receptor(), position);
            if (!wildTypeName.equals(actualWildType)) {
                throw new IllegalStateException(
                        "Mutant " + label + ": the WT 7A receptor has "
                                + (actualWildType == null
                                        ? "no residue"
                                        : actualWildType)
                                + " at A:" + position + ", expected "
                                + wildTypeName + " (" + spec + ")"
                );
            }
        }
    }

    /**
     * The single-mutation specs of a label: one compact spec
     * ({@code F43L}) or a {@code +}-separated composite
     * ({@code F39L+L40M+V41A+R42V+F43L}).
     */
    static List<String> labelSpecs(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalStateException(
                    "A mutation label must not be blank"
            );
        }
        List<String> specs = new ArrayList<>();
        for (String token : label.split("\\+")) {
            String spec = token.trim().toUpperCase(Locale.ROOT);
            if (!SPEC_PATTERN.matcher(spec).matches()) {
                throw new IllegalStateException(
                        "Invalid mutation spec in label " + label
                                + ": " + token
                );
            }
            specs.add(spec);
        }
        return List.copyOf(specs);
    }

    /** All mutated positions of a (possibly composite) label. */
    static Set<Integer> mutationPositions(String label) {
        Set<Integer> positions = new LinkedHashSet<>();
        for (String spec : labelSpecs(label)) {
            positions.add(specPosition(spec));
        }
        return Set.copyOf(positions);
    }

    /**
     * Human-readable receptor description of a label, e.g.
     * {@code LEU at A:43} or
     * {@code LEU at A:39, MET at A:40, ... (5 substitutions)}.
     */
    static String mutationDescription(String label) {
        List<String> specs = labelSpecs(label);
        List<String> parts = new ArrayList<>();
        for (String spec : specs) {
            parts.add(MutationPreparationService.threeLetter(
                    spec.substring(spec.length() - 1))
                    + " at A:" + specPosition(spec));
        }
        String description = String.join(", ", parts);
        if (specs.size() > 1) {
            description += " (" + specs.size() + " substitutions)";
        }
        return description;
    }

    private static int specPosition(String spec) {
        return Integer.parseInt(
                spec.substring(1, spec.length() - 1));
    }

    private static String residueNameAt(Structure structure, int number) {
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                if (residue.getNumber() == number) {
                    return residue.getName();
                }
            }
        }
        return null;
    }

    private static String assignmentDescription(
            PosePocketAssignment assignment
    ) {
        Pocket pocket = assignment.pocket();
        return (pocket == null ? "none" : pocket.name())
                + " (" + assignment.status() + ")";
    }

    private static String crossDescription(
            CrossProteinPoseComparison cross
    ) {
        if (cross.alignedLigandCentroidDistance() == null) {
            return "could not run — " + cross.reason();
        }
        return String.format(
                Locale.ROOT,
                "aligned centroid shift %.3f A; aligned RMSD %s;"
                        + " shared aligned contact residues %d;"
                        + " contact similarity %.3f; relationship %s"
                        + " — %s",
                cross.alignedLigandCentroidDistance(),
                cross.alignedLigandRmsd() == null
                        ? "n/a"
                        : String.format(Locale.ROOT, "%.3f A",
                                cross.alignedLigandRmsd()),
                cross.sharedAlignedContactResidues(),
                cross.contactResidueSimilarity(),
                cross.relationship(),
                cross.reason()
        );
    }

    private static String poseToPocketCenter(
            Ligand pose,
            PosePocketAssignment assignment
    ) {
        if (assignment.pocket() == null) {
            return "n/a (no assigned pocket)";
        }
        List<Point3D> positions = new ArrayList<>();
        for (Chain chain : pose.structure().getChains()) {
            for (Residue residue : chain.residues()) {
                for (var atom : residue.getAtoms()) {
                    if (atom.isHeavyAtom()) {
                        positions.add(atom.getPosition());
                    }
                }
            }
        }
        if (positions.isEmpty()) {
            return "n/a";
        }
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Point3D position : positions) {
            x += position.x();
            y += position.y();
            z += position.z();
        }
        Point3D centroid = new Point3D(
                x / positions.size(),
                y / positions.size(),
                z / positions.size()
        );
        return String.format(
                Locale.ROOT,
                "%.2f A",
                centroid.distance(assignment.pocket().center())
        );
    }

    private static String residueList(
            List<ResidueId> residues
    ) {
        if (residues.isEmpty()) {
            return "-";
        }
        List<String> formatted = new ArrayList<>();
        for (ResidueId residue : residues) {
            formatted.add(residue.chainId() + ":"
                    + residue.residueNumber()
                    + (residue.insertionCode() == null
                            ? ""
                            : residue.insertionCode().toString()));
        }
        return String.join(", ", formatted);
    }

    private static String formatDistance(Double distance) {
        return distance == null
                ? "n/a"
                : String.format(Locale.ROOT, "%.3f A", distance);
    }

    private static String formatConfidence(Double confidence) {
        return confidence == null
                ? "n/a"
                : String.format(Locale.ROOT, "%.3f", confidence);
    }
}
