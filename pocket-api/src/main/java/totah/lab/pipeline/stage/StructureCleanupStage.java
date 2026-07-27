package totah.lab.pipeline.stage;

import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.protein.Atom;
import totah.lab.protein.Residue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class StructureCleanupStage implements Stage {

    private static final Set<String> STANDARD_AMINO_ACIDS = Set.of(
            "ALA", "ARG", "ASN", "ASP", "CYS", "GLN", "GLU", "GLY", "HIS", "ILE",
            "LEU", "LYS", "MET", "PHE", "PRO", "SER", "THR", "TRP", "TYR", "VAL");

    private static final Set<String> WATER_NAMES = Set.of("HOH", "WAT", "DOD", "H2O");

    private static final Set<String> DEFAULT_SPECIAL_RESIDUES = Set.of("MSE");

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

        for (Residue residue : incoming) {
            String name = normalizeName(residue.getName());

            if (STANDARD_AMINO_ACIDS.contains(name)) {
                kept.add(residue);
                continue;
            }

            if (WATER_NAMES.contains(name)) {
                if (removeWaters) {
                    removedWaters.add(residueLabel(residue));
                    continue;
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
                continue;
            }

            if (allowedSpecialResidues.contains(name)) {
                kept.add(residue);
                keptSpecial.add(residueLabel(residue));
                continue;
            }

            throw unsupported(residue, "no cleanup policy exists for residue '" + residue.getName() + "'");
        }

        if (kept.isEmpty()) {
            throw new IllegalStateException("Structure cleanup removed every residue; no receptor residues remain.");
        }

        context.put(ContextKeys.PROTEIN_RESIDUES, List.copyOf(kept));
        context.put(ContextKeys.STRUCTURE_CLEANUP_REPORT,
                new StructureCleanupReport(incoming.size(), kept.size(), removedWaters, removedMetals, keptSpecial));
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
