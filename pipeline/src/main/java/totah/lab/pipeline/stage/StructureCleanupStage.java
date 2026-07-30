package totah.lab.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.pipeline.cleanup.ClassifiedResidue;
import totah.lab.pipeline.cleanup.ResidueClassifier;
import totah.lab.pipeline.cleanup.ResidueDisposition;
import totah.lab.pipeline.cleanup.ResidueKind;
import totah.lab.pipeline.cleanup.ResidueRole;
import totah.lab.pipeline.cleanup.StructureCleanupResult;
import totah.lab.pipeline.report.StructureCleanupReport;
import totah.lab.protein.Residue;
import totah.lab.protein.ResidueClassificationEvidence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class StructureCleanupStage implements Stage {

    private static final Set<String> DEFAULT_SPECIAL_RESIDUES = Set.of("MSE", "TYS");

    private final ResidueClassifier residueClassifier = new ResidueClassifier();

    @Override
    @SuppressWarnings("unchecked")
    public void run(PipelineContext context) {
        Objects.requireNonNull(context, "context is null");
        List<Residue> incoming = (List<Residue>) context.require(ContextKeys.PROTEIN_RESIDUES);
        if (incoming.isEmpty()) {
            throw new IllegalStateException("No protein_residues in context. Run TargetLoadStage first.");
        }

        boolean removeWaters = parseBoolean(context.get(ContextKeys.REMOVE_WATERS), true);
        boolean keepMetals = parseBoolean(context.get(ContextKeys.KEEP_METALS), false);
        Set<String> allowedSpecialResidues = allowedSpecialResidues(context.get(ContextKeys.ALLOWED_SPECIAL_RESIDUES));

        List<Residue> kept = new ArrayList<>();
        List<String> removedWaters = new ArrayList<>();
        List<String> removedMetals = new ArrayList<>();
        List<String> keptSpecial = new ArrayList<>();
        List<Residue> extractedLigands = new ArrayList<>();
        List<ClassifiedResidue> classifiedReceptor = new ArrayList<>();
        List<ClassifiedResidue> classifiedLigands = new ArrayList<>();
        List<ClassifiedResidue> classifiedWaters = new ArrayList<>();
        List<ClassifiedResidue> classifiedMetals = new ArrayList<>();
        List<ClassifiedResidue> classifiedSpecial = new ArrayList<>();

        for (Residue residue : incoming) {
            String name = normalizeName(residue.getName());
            ResidueClassificationEvidence evidence = residue.getResidueClassificationEvidence();
            ResidueKind kind = residueClassifier.classify(residue);
            log.debug("Residue {} classified as {} using evidence {}",
                    residueLabel(residue), kind, evidence);

            switch (kind) {
                case WATER -> {
                    if (removeWaters) {
                        removedWaters.add(residueLabel(residue));
                        classifiedWaters.add(classified(
                                residue, ResidueRole.WATER, ResidueDisposition.REMOVE,
                                "water removed by cleanup policy"));
                    } else {
                        throw unsupported(residue, "water retention is not supported for docking prep");
                    }
                }
                case STANDARD_AMINO_ACID -> {
                    kept.add(residue);
                    classifiedReceptor.add(classified(
                            residue, ResidueRole.STANDARD_AMINO_ACID,
                            ResidueDisposition.KEEP_IN_RECEPTOR,
                            "standard protein residue"));
                }
                case MODIFIED_AMINO_ACID -> {
                    if (allowedSpecialResidues.contains(name)) {
                        kept.add(residue);
                        keptSpecial.add(residueLabel(residue));
                        ClassifiedResidue classified = classified(
                                residue, ResidueRole.MODIFIED_AMINO_ACID,
                                ResidueDisposition.KEEP_IN_RECEPTOR,
                                "modified protein residue enabled by special-residue policy");
                        classifiedReceptor.add(classified);
                        classifiedSpecial.add(classified);
                    } else {
                        extractedLigands.add(residue);
                        classifiedLigands.add(classified(
                                residue, ResidueRole.MODIFIED_AMINO_ACID,
                                ResidueDisposition.EXTRACT_AS_LIGAND,
                                "modified protein residue lacks enabled receptor support"));
                        log.warn("Modified protein residue {} has parent {}, "
                                        + "but is not enabled as a supported special residue",
                                residueLabel(residue), evidence.parentComponentId());
                    }
                }
                case ION_OR_METAL -> {
                    if (keepMetals) {
                        kept.add(residue);
                        keptSpecial.add(residueLabel(residue));
                        ClassifiedResidue classified = classified(
                                residue, ResidueRole.METAL_OR_ION,
                                ResidueDisposition.KEEP_IN_RECEPTOR,
                                "metal or ion retained by cleanup policy");
                        classifiedReceptor.add(classified);
                        classifiedSpecial.add(classified);
                    } else {
                        removedMetals.add(residueLabel(residue));
                        classifiedMetals.add(classified(
                                residue, ResidueRole.METAL_OR_ION,
                                ResidueDisposition.REMOVE,
                                "metal or ion removed by cleanup policy"));
                    }
                }
                case NON_POLYMER -> {
                    if (allowedSpecialResidues.contains(name)) {
                        kept.add(residue);
                        keptSpecial.add(residueLabel(residue));
                        ClassifiedResidue classified = classified(
                                residue, ResidueRole.LIGAND,
                                ResidueDisposition.KEEP_IN_RECEPTOR,
                                "non-polymer retained by special-residue policy");
                        classifiedReceptor.add(classified);
                        classifiedSpecial.add(classified);
                    } else {
                        extractedLigands.add(residue);
                        classifiedLigands.add(classified(
                                residue, ResidueRole.LIGAND,
                                ResidueDisposition.EXTRACT_AS_LIGAND,
                                "CCD identifies a non-polymer component"));
                    }
                }
                case UNKNOWN -> {
                    log.debug("Applying legacy name-based disposition to {}", residueLabel(residue));
                    if (allowedSpecialResidues.contains(name)) {
                        kept.add(residue);
                        keptSpecial.add(residueLabel(residue));
                        ClassifiedResidue classified = classified(
                                residue, ResidueRole.UNKNOWN,
                                ResidueDisposition.KEEP_IN_RECEPTOR,
                                "unknown component retained by special-residue policy");
                        classifiedReceptor.add(classified);
                        classifiedSpecial.add(classified);
                    } else {
                        extractedLigands.add(residue);
                        classifiedLigands.add(classified(
                                residue, ResidueRole.UNKNOWN,
                                ResidueDisposition.EXTRACT_AS_LIGAND,
                                "unknown multi-atom component extracted by fallback policy"));
                    }
                }
            }
        }

        if (kept.isEmpty()) {
            throw new IllegalStateException("Structure cleanup removed every residue; no receptor residues remain.");
        }

        context.put(ContextKeys.PROTEIN_RESIDUES, List.copyOf(kept));
        context.put(ContextKeys.EXTRACTED_LIGANDS, List.copyOf(extractedLigands));
        context.put(ContextKeys.STRUCTURE_CLEANUP_RESULT, new StructureCleanupResult(
                classifiedReceptor,
                classifiedLigands,
                classifiedWaters,
                classifiedMetals,
                classifiedSpecial));
        context.put(ContextKeys.STRUCTURE_CLEANUP_REPORT,
                new StructureCleanupReport(incoming.size(), kept.size(), removedWaters, removedMetals, keptSpecial));
    }

    private IllegalArgumentException unsupported(Residue residue, String reason) {
        return new IllegalArgumentException("Unsupported residue " + residueLabel(residue) + ": " + reason);
    }

    private ClassifiedResidue classified(
            Residue residue,
            ResidueRole role,
            ResidueDisposition disposition,
            String reason) {
        return new ClassifiedResidue(residue, role, disposition, reason);
    }

    private Set<String> allowedSpecialResidues(Object configured) {
        Set<String> result = new HashSet<>(DEFAULT_SPECIAL_RESIDUES);
        if (configured == null) return result;

        if (configured instanceof Collection<?> values) {
            for (Object value : values) {
                if (value != null) result.add(normalizeName(value.toString()));
            }
            return result;
        }

        String text = configured.toString();
        for (String value : text.split(",")) {
            if (!value.isBlank()) result.add(normalizeName(value));
        }
        return result;
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String residueLabel(Residue residue) {
        return residue.getName() + " " + residue.getChain() + ":" + residue.getNumber();
    }
}
