package totah.lab.web.poseanalysis;

import org.springframework.stereotype.Service;
import totah.lab.athena.ligand.contact.DefaultContactAnalyzer;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.ligand.pose.DefaultPosePocketAssigner;
import totah.lab.athena.ligand.pose.PosePocketAssigner;
import totah.lab.athena.ligand.pose.PosePocketAssignment;
import totah.lab.athena.ligand.selectivity.AlignedLigandContact;
import totah.lab.athena.ligand.selectivity.ContactStringRenderer;
import totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzer;
import totah.lab.athena.ligand.selectivity.DifferentialContactType;
import totah.lab.athena.ligand.selectivity.LigandContactAlignment;
import totah.lab.athena.ligand.selectivity.LigandContactAlignmentAnalyzer;
import totah.lab.athena.ligand.selectivity.MutationCandidate;
import totah.lab.athena.ligand.selectivity.MutationCandidateRanker;
import totah.lab.athena.ligand.selectivity.SubstitutionChemistry;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.web.poseanalysis.PoseAnalysisService.CandidatePockets;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Batch-side differential-contact report for one ligand docked against
 * two receptors: the aligned predicted-contact table of the two poses,
 * the residue-number mapping behind the compact contact strings, and
 * ranked single-substitution mutation candidates.
 *
 * <p>All loading reuses the {@link PoseAnalysisService} seams (receptor
 * PDBQT artifact to gaia structure, pose file to gaia ligand, candidate
 * pockets of a run's structure); contacts come from a dedicated
 * {@link DefaultContactAnalyzer} with athena's default cutoffs (never
 * the 500 A SAM shell), the assigned pocket of each pose from the same
 * {@link DefaultPosePocketAssigner} the pocket-assignment endpoint
 * uses, and the alignment/ranking from athena's
 * {@code ligand.selectivity} package. The report is plain text for
 * stdout; it speaks of predicted contact differences and mutation
 * candidates, never of selectivity determinants.
 */
@Service
public class DifferentialContactReportService {

    private final PoseAnalysisService poseAnalysis;
    private final PoseAnalysisRepository repository;

    private final DefaultContactAnalyzer contactAnalyzer =
            new DefaultContactAnalyzer();
    private final PosePocketAssigner poseAssigner =
            new DefaultPosePocketAssigner();
    private final LigandContactAlignmentAnalyzer alignmentAnalyzer =
            new DefaultLigandContactAlignmentAnalyzer();
    private final MutationCandidateRanker candidateRanker =
            new MutationCandidateRanker();
    private final ContactStringRenderer stringRenderer =
            new ContactStringRenderer();

    public DifferentialContactReportService(
            PoseAnalysisService poseAnalysis,
            PoseAnalysisRepository repository
    ) {
        this.poseAnalysis =
                Objects.requireNonNull(poseAnalysis, "poseAnalysis");
        this.repository =
                Objects.requireNonNull(repository, "repository");
    }

    /**
     * The A–E differential-contact report of {@code ligandId} docked
     * against receptors {@code receptorAId} and {@code receptorBId}.
     * The pose per side is {@code poseAId}/{@code poseBId} when given,
     * otherwise the top-ranked pose of the ligand's best-scoring run
     * for that receptor.
     *
     * @throws IllegalStateException when the ligand, a receptor, a pose
     *         or a required artifact cannot be resolved — the message
     *         states exactly what is missing
     */
    public String report(
            String ligandId,
            long receptorAId,
            long receptorBId,
            Long poseAId,
            Long poseBId
    ) {
        if (ligandId == null || ligandId.isBlank()) {
            throw new IllegalStateException(
                    "A ligand id is required for the differential"
                            + " contact report"
            );
        }

        Map<Path, Structure> receptorCache = new HashMap<>();
        Map<String, List<PdbqtModel>> modelCache = new HashMap<>();
        Map<String, CandidatePockets> pocketCache = new HashMap<>();

        PoseSide sideA = loadSide(
                "A",
                ligandId,
                receptorAId,
                poseAId,
                receptorCache,
                modelCache,
                pocketCache
        );
        PoseSide sideB = loadSide(
                "B",
                ligandId,
                receptorBId,
                poseBId,
                receptorCache,
                modelCache,
                pocketCache
        );

        LigandContactAlignment alignment = alignmentAnalyzer.align(
                sideA.receptor(),
                sideA.ligand(),
                sideA.contacts(),
                sideB.receptor(),
                sideB.ligand(),
                sideB.contacts(),
                sideA.assignedPocket(),
                sideB.assignedPocket()
        );
        List<MutationCandidate> candidates =
                candidateRanker.rank(alignment);

        return render(ligandId, sideA, sideB, alignment, candidates);
    }

    /**
     * One side of the comparison: the chosen pose, its run, the
     * receptor structure, the pose ligand, its contacts and the pocket
     * the pose was assigned to ({@code null} when NOT_ASSIGNED).
     */
    private record PoseSide(
            PoseRunProjection run,
            PoseProjection pose,
            Structure receptor,
            Ligand ligand,
            List<LigandContact> contacts,
            Pocket assignedPocket,
            CoordinateProvenance provenance
    ) {
    }

    private PoseSide loadSide(
            String side,
            String ligandId,
            long receptorId,
            Long poseIdOverride,
            Map<Path, Structure> receptorCache,
            Map<String, List<PdbqtModel>> modelCache,
            Map<String, CandidatePockets> pocketCache
    ) {
        PoseProjection pose;
        PoseRunProjection run;

        if (poseIdOverride != null) {
            pose = repository.findPose(poseIdOverride)
                    .orElseThrow(() -> new IllegalStateException(
                            "No docking pose " + poseIdOverride
                                    + " (receptor " + side + " override)"
                    ));
            run = poseAnalysis.runForPose(poseIdOverride);
            if (run.getReceptorId() == null
                    || run.getReceptorId() != receptorId) {
                throw new IllegalStateException(
                        "Pose " + poseIdOverride + " (receptor " + side
                                + " override) belongs to receptor "
                                + run.getReceptorId() + ", not receptor "
                                + receptorId
                );
            }
        } else {
            run = bestRun(side, ligandId, receptorId);
            List<PoseProjection> poses =
                    repository.findPoses(run.getId(), ligandId);
            if (poses.isEmpty()) {
                throw new IllegalStateException(
                        "Run " + run.getId() + " has no poses of ligand "
                                + ligandId + " (receptor " + side + ")"
                );
            }
            pose = poses.getFirst();
        }

        Structure receptor;
        Ligand ligand;
        try {
            receptor = poseAnalysis.receptorStructure(run, receptorCache);
            ligand = poseAnalysis.poseLigand(pose, modelCache);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Receptor " + side + " (run " + run.getId()
                            + ", pose " + pose.getId()
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
                receptor,
                candidates.pockets(),
                ligand,
                contacts
        );

        return new PoseSide(
                run,
                pose,
                receptor,
                ligand,
                contacts,
                assignment.pocket(),
                candidates.provenance()
        );
    }

    /**
     * The ligand's best-scoring run for one receptor (lowest Vina
     * best score; runs without a score rank last).
     */
    private PoseRunProjection bestRun(
            String side,
            String ligandId,
            long receptorId
    ) {
        List<PoseRunProjection> runs =
                repository.findRunsForLigand(receptorId, ligandId);
        if (runs.isEmpty()) {
            throw new IllegalStateException(
                    "No docking runs of ligand " + ligandId
                            + " for receptor " + receptorId
                            + " (receptor " + side + ")"
            );
        }
        return runs.stream()
                .min(Comparator.comparing(
                        PoseRunProjection::getBestScore,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .orElseThrow();
    }

    private String render(
            String ligandId,
            PoseSide sideA,
            PoseSide sideB,
            LigandContactAlignment alignment,
            List<MutationCandidate> candidates
    ) {
        StringBuilder report = new StringBuilder();

        report.append("Differential contact report — predicted contact")
                .append(" differences and mutation candidates\n");
        report.append("Ligand: ").append(ligandId).append('\n');
        appendSide(report, "A", sideA);
        appendSide(report, "B", sideB);
        appendProvenance(report, "A", sideA);
        appendProvenance(report, "B", sideB);
        report.append('\n');

        report.append("A/B. Contact maps (compact one-letter strings in")
                .append(" alignment order; the residue numbers beneath")
                .append(" anchor every letter)\n");
        report.append(stringRenderer.render(alignment)).append('\n');
        report.append('\n');

        report.append("C. Aligned differential-contact table\n");
        appendAlignedTable(report, alignment);
        report.append('\n');

        report.append("D. Residue-number mapping behind the compact")
                .append(" strings\n");
        appendNumberMapping(report, alignment);
        report.append('\n');

        report.append("E. Ranked single-substitution mutation")
                .append(" candidates\n");
        appendCandidates(report, candidates);

        return report.toString();
    }

    /**
     * Frame provenance of one side. The differential contact table
     * itself is frame-independent (each pose is compared against its
     * own receptor); the provenance matters for the pocket-assignment
     * sphere metrics behind the assigned pockets.
     */
    private static void appendProvenance(
            StringBuilder report,
            String side,
            PoseSide poseSide
    ) {
        CoordinateProvenance provenance = poseSide.provenance();
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

    private static void appendSide(
            StringBuilder report,
            String side,
            PoseSide poseSide
    ) {
        report.append("Receptor ").append(side).append(": ")
                .append(poseSide.run().getTargetName())
                .append(" (receptor ").append(poseSide.run().getReceptorId())
                .append("), run ").append(poseSide.run().getId())
                .append(", pose ").append(poseSide.pose().getId())
                .append(" \"").append(poseSide.pose().getLigandLabel())
                .append("\", Vina ")
                .append(String.format(
                        java.util.Locale.ROOT,
                        "%.1f",
                        poseSide.pose().getVinaScore()
                ))
                .append('\n');
        report.append("  assigned pocket: ")
                .append(pocketDescription(poseSide.assignedPocket()))
                .append('\n');
    }

    private static String pocketDescription(Pocket pocket) {
        if (pocket == null) {
            return "none (pose not assigned to a pocket)";
        }
        return pocket.name() + " (id " + pocket.id().value() + ")";
    }

    private static void appendAlignedTable(
            StringBuilder report,
            LigandContactAlignment alignment
    ) {
        report.append("pos | A residue | B residue | A contact")
                .append(" | B contact | A pocket wall | B pocket wall")
                .append(" | chemistry | differential type\n");

        List<AlignedLigandContact> rows = alignment.contacts();
        if (rows.isEmpty()) {
            report.append("(no differential-contact positions: identical")
                    .append(" residues with no contacts everywhere)\n");
            return;
        }

        for (AlignedLigandContact row : rows) {
            report.append(row.alignmentPosition())
                    .append(" | ")
                    .append(residueCell(row.residueA(), row.residueAId()))
                    .append(" | ")
                    .append(residueCell(row.residueB(), row.residueBId()))
                    .append(" | ")
                    .append(contactCell(
                            row.contactA(),
                            row.contactTypeA(),
                            row.minDistanceA()
                    ))
                    .append(" | ")
                    .append(contactCell(
                            row.contactB(),
                            row.contactTypeB(),
                            row.minDistanceB()
                    ))
                    .append(" | ")
                    .append(pocketCell(row.pocketMemberA()))
                    .append(" | ")
                    .append(pocketCell(row.pocketMemberB()))
                    .append(" | ")
                    .append(chemistryCell(row))
                    .append(" | ")
                    .append(row.differentialType())
                    .append('\n');
        }
    }

    private static String residueCell(
            String name,
            totah.lab.gaia.structure.ResidueId id
    ) {
        if (name == null) {
            return "-";
        }
        return name + " " + (id == null ? "?" : id.residueNumber());
    }

    private static String contactCell(
            boolean contact,
            totah.lab.athena.ligand.contact.ContactType type,
            Double minDistance
    ) {
        if (!contact || type == null) {
            return "-";
        }
        return type + " " + String.format(
                java.util.Locale.ROOT,
                "%.1f",
                minDistance == null ? 0.0 : minDistance
        );
    }

    private static String pocketCell(Boolean pocketMember) {
        if (pocketMember == null) {
            return "n/a";
        }
        return pocketMember ? "wall" : "-";
    }

    private static String chemistryCell(AlignedLigandContact row) {
        if (row.differentialType() == DifferentialContactType.UNMAPPED
                || row.residueA() == null || row.residueB() == null) {
            return "-";
        }
        SubstitutionChemistry chemistry =
                SubstitutionChemistry.between(
                        row.residueA(),
                        row.residueB()
                );
        if (chemistry.identical()) {
            return "identical";
        }
        List<String> features = new ArrayList<>();
        if (chemistry.aromaticGainLoss()) {
            features.add("aromatic gain/loss");
        }
        if (chemistry.chargeGainLoss()) {
            features.add("charge gain/loss");
        }
        if (chemistry.polarHydrophobicSwap()) {
            features.add("polar-hydrophobic swap");
        }
        if (chemistry.prolineGlycineSpecial()) {
            features.add("Pro/Gly special");
        }
        if (chemistry.conservative()) {
            features.add("conservative");
        }
        return chemistry.substitutionClass()
                + (features.isEmpty()
                ? ""
                : " (" + String.join(", ", features) + ")");
    }

    /**
     * The pairwise residue-number mapping of every alignment position
     * that appears in the compact contact strings (a contact on at
     * least one side), so each letter can be traced to both residue
     * numbers.
     */
    private static void appendNumberMapping(
            StringBuilder report,
            LigandContactAlignment alignment
    ) {
        Map<Integer, AlignedLigandContact> rowsByPosition =
                new HashMap<>();
        for (AlignedLigandContact row : alignment.contacts()) {
            rowsByPosition.put(row.alignmentPosition(), row);
        }

        report.append("pos | A number | A residue | B number")
                .append(" | B residue\n");

        boolean any = false;
        List<AlignedResiduePair> pairs =
                alignment.sequenceAlignment().pairs();
        for (int index = 0; index < pairs.size(); index++) {
            AlignedResiduePair pair = pairs.get(index);
            AlignedLigandContact row = rowsByPosition.get(index + 1);
            boolean contactA = row != null && row.contactA();
            boolean contactB = row != null && row.contactB();
            if (!contactA && !contactB) {
                continue;
            }
            any = true;
            report.append(index + 1)
                    .append(" | ")
                    .append(pair.queryResidueNumber())
                    .append(" | ")
                    .append(pair.queryResidueName())
                    .append(" | ")
                    .append(pair.candidateResidueNumber())
                    .append(" | ")
                    .append(pair.candidateResidueName())
                    .append('\n');
        }
        if (!any) {
            report.append("(no contacts on either side)\n");
        }
    }

    private static void appendCandidates(
            StringBuilder report,
            List<MutationCandidate> candidates
    ) {
        if (candidates.isEmpty()) {
            report.append("(no mutation candidates: no divergent")
                    .append(" position with a contact on either")
                    .append(" side)\n");
            return;
        }

        report.append("tier | direction | label | pos | source contact")
                .append(" | other contact | min distance | pocket")
                .append(" | chemistry\n");
        for (MutationCandidate candidate : candidates) {
            report.append(candidate.tier())
                    .append(" | ")
                    .append(candidate.direction()
                            == MutationCandidate.MutationDirection.A_TO_B
                            ? "A->B"
                            : "B->A")
                    .append(" | ")
                    .append(candidate.label())
                    .append(" | ")
                    .append(candidate.alignmentPosition())
                    .append(" | ")
                    .append(candidate.contactOnSource() ? "yes" : "-")
                    .append(" | ")
                    .append(candidate.contactOnOther() ? "yes" : "-")
                    .append(" | ")
                    .append(candidate.minDistance() == null
                            ? "-"
                            : String.format(
                                    java.util.Locale.ROOT,
                                    "%.1f",
                                    candidate.minDistance()
                            ))
                    .append(" | ")
                    .append(pocketCell(candidate.pocketMember()))
                    .append(" | ")
                    .append(candidateChemistry(candidate))
                    .append('\n');
        }
    }

    private static String candidateChemistry(MutationCandidate candidate) {
        SubstitutionChemistry chemistry = candidate.chemistry();
        List<String> features = new ArrayList<>();
        if (chemistry.aromaticGainLoss()) {
            features.add("aromatic gain/loss");
        }
        if (chemistry.chargeGainLoss()) {
            features.add("charge gain/loss");
        }
        if (chemistry.polarHydrophobicSwap()) {
            features.add("polar-hydrophobic swap");
        }
        if (chemistry.prolineGlycineSpecial()) {
            features.add("Pro/Gly special");
        }
        if (chemistry.conservative()) {
            features.add("conservative");
        }
        return chemistry.substitutionClass()
                + (features.isEmpty()
                ? ""
                : " (" + String.join(", ", features) + ")");
    }
}
