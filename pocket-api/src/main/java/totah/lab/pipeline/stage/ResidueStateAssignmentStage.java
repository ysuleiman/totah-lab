package totah.lab.pipeline.stage;

import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Residue;
import totah.lab.protein.hydrogenation.DisulfideDetector;
import totah.lab.protein.hydrogenation.ProtonationConfig;
import totah.lab.topology.AmberResidueTemplateLibrary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ResidueStateAssignmentStage implements Stage {

    private static final Set<String> STANDARD_AMINO_ACIDS = Set.of(
            "ALA", "ARG", "ASN", "ASP", "CYS", "GLN", "GLU", "GLY", "HIS", "ILE",
            "LEU", "LYS", "MET", "PHE", "PRO", "SER", "THR", "TRP", "TYR", "VAL");

    private static final Set<String> HISTIDINE_STATES = Set.of("HID", "HIE", "HIP");

    @Override
    @SuppressWarnings("unchecked")
    public void run(PipelineContext context) {
        Objects.requireNonNull(context, "context is null");
        List<Residue> incoming = (List<Residue>) context.require(ContextKeys.PROTEIN_RESIDUES);
        if (incoming.isEmpty()) {
            throw new IllegalStateException("No protein_residues in context. Run AlphaFoldFilterStage first.");
        }
        rejectUnsupportedHisAuto(context);

        ProtonationConfig config = ProtonationConfig.fromContext(context);
        double disulfideCutoff = parseDouble(context.get(ContextKeys.DISULFIDE_CUTOFF),
                ProtonationConfig.DEFAULT_SS_CUTOFF);
        boolean detectDisulfides = parseBoolean(context.get(ContextKeys.DETECT_DISULFIDES), true);
        Set<String> disulfideKeys = new HashSet<>();
        if (detectDisulfides) {
            for (Residue residue : DisulfideDetector.findDisulfideBonds(incoming, disulfideCutoff)) {
                disulfideKeys.add(residueKey(residue));
            }
        }

        Map<String, String> overrides = protonationOverrides(context.get(ContextKeys.RESIDUE_PROTONATION_OVERRIDES));
        AmberResidueTemplateLibrary amber = AmberResidueTemplateLibrary.getInstance();

        List<Residue> prepared = new ArrayList<>(incoming.size());
        Map<String, ResidueState> states = new LinkedHashMap<>();
        List<String> convertedResidues = new ArrayList<>();
        List<String> disulfideResidues = new ArrayList<>();
        List<String> assignedTemplates = new ArrayList<>();

        for (int i = 0; i < incoming.size(); i++) {
            Residue original = incoming.get(i);
            boolean nTerm = isNTerminus(incoming, i);
            boolean cTerm = isCTerminus(incoming, i);
            if (nTerm && cTerm) {
                throw new IllegalArgumentException("Residue " + residueLabel(original)
                        + " is both N- and C-terminal; combined terminal templates are not supported.");
            }

            Residue normalized = normalizeResidue(original);
            if (!normalized.getName().equals(original.getName())) {
                convertedResidues.add(residueLabel(original) + " -> " + normalized.getName());
            }

            String key = residueKey(normalized);
            String baseTemplate = baseTemplateName(normalized, key, disulfideKeys.contains(key),
                    config, overrides);
            String amberTemplate = terminalTemplateName(baseTemplate, nTerm, cTerm);
            if (amber.getTemplate(amberTemplate) == null) {
                throw new IllegalArgumentException("No Amber template '" + amberTemplate + "' for "
                        + residueLabel(normalized));
            }

            boolean disulfide = baseTemplate.equals("CYX");
            if (disulfide) {
                disulfideResidues.add(residueLabel(normalized));
            }

            prepared.add(normalized);
            String note = stateNote(original.getName(), normalized.getName(), baseTemplate, amberTemplate);
            states.put(key, new ResidueState(key, original.getName(), normalized.getName(),
                    amberTemplate, nTerm, cTerm, disulfide, note));
            assignedTemplates.add(residueLabel(normalized) + " -> " + amberTemplate);
        }

        context.put(ContextKeys.PROTEIN_RESIDUES, List.copyOf(prepared));
        context.put(ContextKeys.RESIDUE_STATES, Map.copyOf(states));
        context.put(ContextKeys.RESIDUE_STATE_REPORT,
                new ResidueStateAssignmentReport(incoming.size(), prepared.size(),
                        convertedResidues, disulfideResidues, assignedTemplates));
        if (!disulfideResidues.isEmpty()) {
            Set<Residue> disulfideResidueSet = new HashSet<>();
            for (Residue residue : prepared) {
                if (disulfideKeys.contains(residueKey(residue))) {
                    disulfideResidueSet.add(residue);
                }
            }
            context.put(ContextKeys.DISULFIDE_BONDS, disulfideResidueSet);
        }
    }

    private String baseTemplateName(Residue residue, String key, boolean disulfide,
                                    ProtonationConfig config, Map<String, String> overrides) {
        String name = normalizeName(residue.getName());
        String override = overrides.get(key);
        if (override != null) {
            validateOverrideCompatible(residue, override);
            return override;
        }

        if ("HIS".equals(name)) {
            return normalizeHisState(config.hisState().name());
        }
        if ("CYS".equals(name)) {
            if (disulfide) return "CYX";
            if (config.ph() > (ProtonationConfig.PKA_CYS + 1.0)) return "CYM";
            return "CYS";
        }
        if ("ASP".equals(name) && config.ph() < (ProtonationConfig.PKA_ASP - 1.0)) {
            return "ASH";
        }
        if ("GLU".equals(name) && config.ph() < (ProtonationConfig.PKA_GLU - 1.0)) {
            return "GLH";
        }
        if ("LYS".equals(name) && config.ph() > (ProtonationConfig.PKA_LYS + 1.0)) {
            return "LYN";
        }
        if (STANDARD_AMINO_ACIDS.contains(name)) {
            return name;
        }
        throw new IllegalArgumentException("Unsupported prepared residue " + residueLabel(residue)
                + "; no Amber state assignment policy exists.");
    }

    private void validateOverrideCompatible(Residue residue, String override) {
        String residueName = normalizeName(residue.getName());
        String value = normalizeName(override);
        boolean compatible = switch (residueName) {
            case "HIS" -> HISTIDINE_STATES.contains(value);
            case "ASP" -> value.equals("ASP") || value.equals("ASH");
            case "GLU" -> value.equals("GLU") || value.equals("GLH");
            case "CYS" -> value.equals("CYS") || value.equals("CYM") || value.equals("CYX");
            case "LYS" -> value.equals("LYS") || value.equals("LYN");
            default -> value.equals(residueName);
        };
        if (!compatible) {
            throw new IllegalArgumentException("Override '" + override + "' is not compatible with "
                    + residueLabel(residue));
        }
    }

    private String normalizeHisState(String value) {
        String state = normalizeName(value);
        if ("AUTO".equals(state)) {
            throw new IllegalArgumentException("hisProtonationState=AUTO is not supported without an external pKa/H-bond solver.");
        }
        if (!HISTIDINE_STATES.contains(state)) {
            throw new IllegalArgumentException("Unsupported histidine state: " + value);
        }
        return state;
    }

    private String terminalTemplateName(String baseTemplate, boolean nTerm, boolean cTerm) {
        if (nTerm) return "N" + baseTemplate;
        if (cTerm) return "C" + baseTemplate;
        return baseTemplate;
    }

    private Residue normalizeResidue(Residue residue) {
        if (!"MSE".equals(normalizeName(residue.getName()))) {
            return residue;
        }
        List<Atom> atoms = new ArrayList<>(residue.getAtoms().size());
        for (Atom atom : residue.getAtoms()) {
            if ("SE".equals(atom.getName())) {
                atoms.add(atom.toBuilder()
                        .name("SD")
                        .element(sulfur())
                        .build());
            } else {
                atoms.add(atom);
            }
        }
        return residue.toBuilder()
                .name("MET")
                .atoms(atoms)
                .build();
    }

    private Element sulfur() {
        return Element.builder()
                .symbol("S")
                .atomicNumber(16)
                .atomicMass(32.06)
                .covalentRadius(1.05)
                .vdwRadius(1.80)
                .build();
    }

    private boolean isNTerminus(List<Residue> residues, int index) {
        Residue residue = residues.get(index);
        Residue prev = index > 0 ? residues.get(index - 1) : null;
        return prev == null || !Objects.equals(prev.getChain(), residue.getChain())
                || residue.getNumber() != prev.getNumber() + 1;
    }

    private boolean isCTerminus(List<Residue> residues, int index) {
        Residue residue = residues.get(index);
        Residue next = index < residues.size() - 1 ? residues.get(index + 1) : null;
        return next == null || !Objects.equals(next.getChain(), residue.getChain())
                || next.getNumber() != residue.getNumber() + 1;
    }

    private Map<String, String> protonationOverrides(Object configured) {
        Map<String, String> result = new LinkedHashMap<>();
        if (configured == null) return result;

        if (configured instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(entry.getKey().toString().trim(), normalizeName(entry.getValue().toString()));
                }
            }
            return result;
        }

        if (configured instanceof Collection<?> values) {
            for (Object value : values) {
                parseOverrideEntry(result, value);
            }
            return result;
        }

        for (String part : configured.toString().split(",")) {
            parseOverrideEntry(result, part);
        }
        return result;
    }

    private void parseOverrideEntry(Map<String, String> result, Object value) {
        if (value == null) return;
        String text = value.toString().trim();
        if (text.isEmpty()) return;
        String[] pieces = text.split("=");
        if (pieces.length != 2 || pieces[0].isBlank() || pieces[1].isBlank()) {
            throw new IllegalArgumentException("Residue protonation override '" + text
                    + "' must have format A:123=HIE");
        }
        result.put(pieces[0].trim(), normalizeName(pieces[1]));
    }

    private String stateNote(String originalName, String preparedName, String baseTemplate, String amberTemplate) {
        if (!originalName.equals(preparedName)) return originalName + " converted to " + preparedName;
        if (!preparedName.equals(baseTemplate)) return preparedName + " assigned " + baseTemplate;
        if (!baseTemplate.equals(amberTemplate)) return preparedName + " assigned terminal template";
        return preparedName + " assigned internal template";
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private void rejectUnsupportedHisAuto(PipelineContext context) {
        Object value = context.get(ContextKeys.HIS_PROTONATION_STATE);
        if (value != null && "AUTO".equals(normalizeName(value.toString()))) {
            throw new IllegalArgumentException("hisProtonationState=AUTO is not supported without an external pKa/H-bond solver.");
        }
    }

    private double parseDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(value.toString());
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String residueKey(Residue residue) {
        return residue.getChain() + ":" + residue.getNumber();
    }

    private String residueLabel(Residue residue) {
        String insertion = residue.getInsertionCode() == null || residue.getInsertionCode() == ' '
                ? ""
                : residue.getInsertionCode().toString();
        return residue.getName() + " " + residue.getChain() + ":" + residue.getNumber() + insertion;
    }
}
