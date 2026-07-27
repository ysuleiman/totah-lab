package totah.lab.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.pipeline.cleanup.ResidueKind;
import totah.lab.protein.Atom;
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

    private static final Set<String> STANDARD_AMINO_ACIDS = Set.of(
            "ALA", "ARG", "ASN", "ASP", "CYS", "GLN", "GLU", "GLY", "HIS", "ILE",
            "LEU", "LYS", "MET", "PHE", "PRO", "SER", "THR", "TRP", "TYR", "VAL");

    private static final Set<String> WATER_NAMES = Set.of("HOH", "WAT", "DOD", "H2O");

    private static final Set<String> DEFAULT_SPECIAL_RESIDUES = Set.of("MSE", "TYS");

    private static final Set<String> METAL_ELEMENTS = Set.of(
            "LI", "NA", "K", "RB", "CS",
            "BE", "MG", "CA", "SR", "BA",
            "SC", "TI", "V", "CR", "MN", "FE", "CO", "NI", "CU", "ZN",
            "Y", "ZR", "NB", "MO", "TC", "RU", "RH", "PD", "AG", "CD",
            "LU", "HF", "TA", "W", "RE", "OS", "IR", "PT", "AU", "HG",
            "AL", "GA", "IN", "SN", "TL", "PB", "BI");
    private final MetalIonPolicy metalIonPolicy = new MetalIonPolicy();

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

        for (Residue residue : incoming) {
            String name = normalizeName(residue.getName());
            ResidueClassificationEvidence evidence = residue.getResidueClassificationEvidence();
            ResidueKind kind = classifyResidue(residue, evidence);
            log.debug("Residue {} classified as {} using evidence {}",
                    residueLabel(residue), kind, evidence);

            switch (kind) {
                case WATER -> {
                    if (removeWaters) {
                        removedWaters.add(residueLabel(residue));
                    } else {
                        throw unsupported(residue, "water retention is not supported for docking prep");
                    }
                }
                case STANDARD_AMINO_ACID -> kept.add(residue);
                case MODIFIED_AMINO_ACID -> {
                    if (allowedSpecialResidues.contains(name)) {
                        kept.add(residue);
                        keptSpecial.add(residueLabel(residue));
                    } else {
                        extractedLigands.add(residue);
                        log.warn("Modified protein residue {} has parent {}, "
                                        + "but is not enabled as a supported special residue",
                                residueLabel(residue), evidence.parentComponentId());
                    }
                }
                case ION_OR_METAL -> {
                    if (keepMetals) {
                        kept.add(residue);
                        keptSpecial.add(residueLabel(residue));
                    } else {
                        removedMetals.add(residueLabel(residue));
                    }
                }
                case NON_POLYMER -> {
                    if (allowedSpecialResidues.contains(name)) {
                        kept.add(residue);
                        keptSpecial.add(residueLabel(residue));
                    } else {
                        extractedLigands.add(residue);
                    }
                }
                case UNKNOWN -> applyLegacyFallback(
                        residue,
                        name,
                        removeWaters,
                        keepMetals,
                        allowedSpecialResidues,
                        kept,
                        removedWaters,
                        removedMetals,
                        keptSpecial,
                        extractedLigands);
            }
        }

        if (kept.isEmpty()) {
            throw new IllegalStateException("Structure cleanup removed every residue; no receptor residues remain.");
        }

        context.put(ContextKeys.PROTEIN_RESIDUES, List.copyOf(kept));
        context.put(ContextKeys.EXTRACTED_LIGANDS, List.copyOf(extractedLigands));
        context.put(ContextKeys.STRUCTURE_CLEANUP_REPORT,
                new StructureCleanupReport(incoming.size(), kept.size(), removedWaters, removedMetals, keptSpecial));
    }

    private ResidueKind classifyResidue(
            Residue residue,
            ResidueClassificationEvidence evidence) {
        if (isMonoatomicMetalOrKnownIon(residue)) {
            return ResidueKind.ION_OR_METAL;
        }

        if (evidence == null || !evidence.available()) {
            return ResidueKind.UNKNOWN;
        }

        if (evidence.water()) {
            return ResidueKind.WATER;
        }

        if (isStandardProteinResidue(evidence)) {
            return ResidueKind.STANDARD_AMINO_ACID;
        }

        if (isModifiedProteinResidue(evidence)) {
            return ResidueKind.MODIFIED_AMINO_ACID;
        }

        if (isExplicitNonPolymer(evidence)) {
            return ResidueKind.NON_POLYMER;
        }

        return ResidueKind.UNKNOWN;
    }

    private boolean isExplicitNonPolymer(ResidueClassificationEvidence evidence) {
        if ("nonPolymer".equals(evidence.residueType())) {
            return true;
        }
        return !evidence.polymeric()
                && "peptideLike".equals(evidence.residueType())
                && "otherPolymer".equals(evidence.polymerType());
    }

    private boolean isStandardProteinResidue(ResidueClassificationEvidence evidence) {
        return evidence.standard()
                && evidence.polymeric()
                && isProteinPolymerType(evidence.polymerType());
    }

    private boolean isModifiedProteinResidue(ResidueClassificationEvidence evidence) {
        return evidence.polymeric()
                && isProteinPolymerType(evidence.polymerType())
                && hasParentComponent(evidence.parentComponentId());
    }

    private boolean isProteinPolymerType(String polymerType) {
        if (polymerType == null) {
            return false;
        }

        return switch (polymerType.trim().toLowerCase(Locale.ROOT)) {
            case "peptide", "dpeptide" -> true;
            default -> false;
        };
    }

    private boolean hasParentComponent(String parentComponentId) {
        if (parentComponentId == null) {
            return false;
        }

        String normalized = parentComponentId.trim();
        return !normalized.isEmpty()
                && !normalized.equals("?")
                && !normalized.equals(".");
    }

    private void applyLegacyFallback(
            Residue residue,
            String name,
            boolean removeWaters,
            boolean keepMetals,
            Set<String> allowedSpecialResidues,
            List<Residue> kept,
            List<String> removedWaters,
            List<String> removedMetals,
            List<String> keptSpecial,
            List<Residue> extractedLigands) {
        log.debug("Applying legacy name-based classification to {}", residueLabel(residue));

        if (STANDARD_AMINO_ACIDS.contains(name)) {
            kept.add(residue);
            return;
        }

        if (WATER_NAMES.contains(name)) {
            if (removeWaters) {
                removedWaters.add(residueLabel(residue));
                return;
            }

            throw unsupported(residue, "water retention is not supported for docking prep");
        }

        if (isMonoatomicMetalOrKnownIon(residue)) {
            if (keepMetals) {
                kept.add(residue);
                keptSpecial.add(residueLabel(residue));
            } else {
                removedMetals.add(residueLabel(residue));
            }
            return;
        }

        if (allowedSpecialResidues.contains(name)) {
            kept.add(residue);
            keptSpecial.add(residueLabel(residue));
            return;
        }

        extractedLigands.add(residue);
    }

    private IllegalArgumentException unsupported(Residue residue, String reason) {
        return new IllegalArgumentException("Unsupported residue " + residueLabel(residue) + ": " + reason);
    }

    private boolean isMonoatomicMetalOrKnownIon(Residue residue) {
        if (residue.getAtoms().size() != 1) return false;
        Atom atom = residue.getAtoms().getFirst();
        if (atom.getElement() == null || atom.getElement().getSymbol() == null) return false;
        return METAL_ELEMENTS.contains(atom.getElement().getSymbol().toUpperCase(Locale.ROOT))
                || metalIonPolicy.isKnownIonResidue(residue);
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
