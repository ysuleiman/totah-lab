package totah.lab.hephaestus.receptor.operation;


import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.amber.AmberResidueTemplateLibrary;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.receptor.ReceptorPreparationOperation;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.cleanup.MetalIonPolicy;
import totah.lab.hephaestus.receptor.disulfide.DisulfideDetector;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import totah.lab.hephaestus.receptor.residue.ResidueStateAssignmentReport;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ResidueStateAssignmentOperation
        implements ReceptorPreparationOperation {

    public static final String RESIDUE_STATES_ATTRIBUTE =
            "residue-states";

    public static final String RESIDUE_STATE_REPORT_ATTRIBUTE =
            "residue-state-report";

    public static final String DISULFIDE_RESIDUES_ATTRIBUTE =
            "disulfide-residues";

    private static final Set<String> STANDARD_AMINO_ACIDS = Set.of(
            "ALA", "ARG", "ASN", "ASP", "CYS",
            "GLN", "GLU", "GLY", "HIS", "ILE",
            "LEU", "LYS", "MET", "PHE", "PRO",
            "SER", "THR", "TRP", "TYR", "VAL");

    private static final Set<String> HISTIDINE_STATES =
            Set.of("HID", "HIE", "HIP");

    private final AmberResidueTemplateLibrary amberTemplates;

    private final MetalIonPolicy metalIonPolicy =
            new MetalIonPolicy();

    public ResidueStateAssignmentOperation() {
        this(AmberResidueTemplateLibrary.getInstance());
    }

    public ResidueStateAssignmentOperation(
            AmberResidueTemplateLibrary amberTemplates) {

        this.amberTemplates = Objects.requireNonNull(
                amberTemplates,
                "amberTemplates");
    }

    @Override
    public OperationResult<PreparedProtein> apply(
            PreparedProtein preparedProtein,
            ReceptorPreparationOptions options) {

        Objects.requireNonNull(
                preparedProtein,
                "preparedProtein");

        Objects.requireNonNull(options, "options");

        ProtonationConfig config =
                Objects.requireNonNull(
                        options.protonationConfig(),
                        "options.protonationConfig");

        rejectUnsupportedHisAuto(config);

        Protein protein = preparedProtein.protein();
        Structure structure = protein.structure();

        if (structure.getResidueCount() == 0) {
            throw new IllegalStateException(
                    "Protein structure contains no residues. "
                            + "Run AlphaFold filtering first.");
        }

        /*
         * The new Gaia Residue does not store its chain ID.
         * Build an identity-based lookup while flattening residues for
         * the existing disulfide detector.
         */
        List<Residue> allResidues = new ArrayList<>();
        Map<Residue, String> chainByResidue =
                new IdentityHashMap<>();

        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                allResidues.add(residue);
                chainByResidue.put(residue, chain.id());
            }
        }

        Set<String> disulfideKeys = detectDisulfides(
                allResidues,
                chainByResidue,
                options);

        Map<String, String> overrides =
                options.residueProtonationOverrides();

        List<Chain> preparedChains = new ArrayList<>();

        Map<String, ResidueState> states =
                new LinkedHashMap<>();

        List<String> convertedResidues = new ArrayList<>();
        List<String> disulfideResidues = new ArrayList<>();
        List<String> assignedTemplates = new ArrayList<>();

        for (Chain chain : structure.getChains()) {
            List<Residue> preparedResidues =
                    prepareChain(
                            chain,
                            config,
                            overrides,
                            disulfideKeys,
                            states,
                            convertedResidues,
                            disulfideResidues,
                            assignedTemplates);

            preparedChains.add(
                    new Chain(
                            chain.id(),
                            preparedResidues));
        }

        Structure preparedStructure =
                new Structure(preparedChains);

        Protein preparedGaiaProtein = copyWithStructure(
                protein,
                preparedStructure);

        ResidueStateAssignmentReport report =
                new ResidueStateAssignmentReport(
                        structure.getResidueCount(),
                        preparedStructure.getResidueCount(),
                        convertedResidues,
                        disulfideResidues,
                        assignedTemplates);

        PreparedProtein result = preparedProtein
                .withProtein(preparedGaiaProtein)
                .withAttribute(RESIDUE_STATES_ATTRIBUTE, states)
                .withAttribute(RESIDUE_STATE_REPORT_ATTRIBUTE, report)
                .withAttribute(DISULFIDE_RESIDUES_ATTRIBUTE, disulfideKeys);

        return OperationResult.success(
                result);
    }

    private List<Residue> prepareChain(
            Chain chain,
            ProtonationConfig config,
            Map<String, String> overrides,
            Set<String> disulfideKeys,
            Map<String, ResidueState> states,
            List<String> convertedResidues,
            List<String> disulfideResidues,
            List<String> assignedTemplates) {

        List<Residue> incoming = chain.residues();
        List<Residue> prepared =
                new ArrayList<>(incoming.size());

        for (int index = 0; index < incoming.size(); index++) {
            Residue original = incoming.get(index);

            // Monoatomic metal ions carry no residue state; they flow
            // through to fixed-ion charge assignment (MetalIonPolicy).
            if (metalIonPolicy.isKnownIonResidue(original)) {
                prepared.add(original);
                continue;
            }

            boolean nTerminus =
                    isNTerminus(incoming, index);

            boolean cTerminus =
                    isCTerminus(incoming, index);

            // A single-residue chain is both index 0 and the last
            // index; no combined N+C terminal template exists, so
            // treat the residue as N-terminal only.
            if (nTerminus && cTerminus) {
                cTerminus = false;
            }

            Residue normalized =
                    normalizeResidue(original);

            if (!normalized.getName().equals(original.getName())) {
                convertedResidues.add(
                        residueLabel(chain.id(), original)
                                + " -> "
                                + normalized.getName());
            }

            String key =
                    residueKey(chain.id(), normalized);

            String baseTemplate =
                    baseTemplateName(
                            chain.id(),
                            normalized,
                            key,
                            disulfideKeys.contains(key),
                            config,
                            overrides);

            String amberTemplate =
                    terminalTemplateName(
                            baseTemplate,
                            nTerminus,
                            cTerminus);

            if (amberTemplates.getTemplate(amberTemplate) == null) {
                throw new IllegalArgumentException(
                        "No Amber template '"
                                + amberTemplate
                                + "' for "
                                + residueLabel(
                                chain.id(),
                                normalized));
            }

            boolean disulfide =
                    "CYX".equals(baseTemplate);

            if (disulfide) {
                disulfideResidues.add(
                        residueLabel(
                                chain.id(),
                                normalized));
            }

            prepared.add(normalized);

            states.put(
                    key,
                    new ResidueState(
                            chain.id(),
                            normalized.getNumber(),
                            normalized.getInsertionCode(),
                            original.getName(),
                            normalized.getName(),
                            amberTemplate,
                            nTerminus,
                            cTerminus,
                            disulfide,
                            stateNote(
                                    original.getName(),
                                    normalized.getName(),
                                    baseTemplate,
                                    amberTemplate)));

            assignedTemplates.add(
                    residueLabel(chain.id(), normalized)
                            + " -> "
                            + amberTemplate);
        }

        return List.copyOf(prepared);
    }

    private Set<String> detectDisulfides(
            List<Residue> residues,
            Map<Residue, String> chainByResidue,
            ReceptorPreparationOptions options) {

        ProtonationConfig config = options.protonationConfig();

        if (!config.detectDisulfides()) {
            return Set.of();
        }

        Set<String> keys = new HashSet<>();

        for (Residue residue :
                DisulfideDetector.findDisulfideBonds(
                        residues,
                        config.disulfideCutoff())) {

            String chainId =
                    chainByResidue.get(residue);

            if (chainId == null) {
                throw new IllegalStateException(
                        "Disulfide detector returned an unknown residue.");
            }

            keys.add(residueKey(chainId, residue));
        }

        return Set.copyOf(keys);
    }

    private String baseTemplateName(
            String chainId,
            Residue residue,
            String key,
            boolean disulfide,
            ProtonationConfig config,
            Map<String, String> overrides) {

        String name = normalizeName(residue.getName());
        String override = overrides.get(key);

        if (override != null) {
            validateOverrideCompatible(
                    chainId,
                    residue,
                    override);

            return normalizeName(override);
        }

        if ("HIS".equals(name)) {
            return normalizeHisState(
                    config.histidineState().name());
        }

        if ("CYS".equals(name)) {
            if (disulfide) {
                return "CYX";
            }

            if (config.ph()
                    > ProtonationConfig.PKA_CYS + 1.0) {
                return "CYM";
            }

            return "CYS";
        }

        if ("ASP".equals(name)
                && config.ph()
                < ProtonationConfig.PKA_ASP - 1.0) {
            return "ASH";
        }

        if ("GLU".equals(name)
                && config.ph()
                < ProtonationConfig.PKA_GLU - 1.0) {
            return "GLH";
        }

        if ("LYS".equals(name)
                && config.ph()
                > ProtonationConfig.PKA_LYS + 1.0) {
            return "LYN";
        }

        if (STANDARD_AMINO_ACIDS.contains(name)
                || "TYS".equals(name)) {
            return name;
        }

        throw new IllegalArgumentException(
                "Unsupported prepared residue "
                        + residueLabel(chainId, residue)
                        + "; no Amber state-assignment policy exists.");
    }

    private void validateOverrideCompatible(
            String chainId,
            Residue residue,
            String override) {

        String residueName =
                normalizeName(residue.getName());

        String value =
                normalizeName(override);

        boolean compatible = switch (residueName) {
            case "HIS" -> HISTIDINE_STATES.contains(value);
            case "ASP" -> value.equals("ASP")
                    || value.equals("ASH");
            case "GLU" -> value.equals("GLU")
                    || value.equals("GLH");
            case "CYS" -> value.equals("CYS")
                    || value.equals("CYM")
                    || value.equals("CYX");
            case "LYS" -> value.equals("LYS")
                    || value.equals("LYN");
            default -> value.equals(residueName);
        };

        if (!compatible) {
            throw new IllegalArgumentException(
                    "Override '"
                            + override
                            + "' is not compatible with "
                            + residueLabel(chainId, residue));
        }
    }

    private String normalizeHisState(String value) {
        String state = normalizeName(value);

        if ("AUTO".equals(state)) {
            throw new IllegalArgumentException(
                    "hisProtonationState=AUTO is not supported "
                            + "without an external pKa/H-bond solver.");
        }

        if (!HISTIDINE_STATES.contains(state)) {
            throw new IllegalArgumentException(
                    "Unsupported histidine state: " + value);
        }

        return state;
    }

    private String terminalTemplateName(
            String baseTemplate,
            boolean nTerminus,
            boolean cTerminus) {

        if (nTerminus) {
            return "N" + baseTemplate;
        }

        if (cTerminus) {
            return "C" + baseTemplate;
        }

        return baseTemplate;
    }

    private Residue normalizeResidue(Residue residue) {
        if (!"MSE".equals(
                normalizeName(residue.getName()))) {
            return residue;
        }

        List<Atom> atoms =
                new ArrayList<>(residue.getAtoms().size());

        for (Atom atom : residue.getAtoms()) {
            if ("SE".equals(normalizeName(atom.getName()))) {
                atoms.add(
                        atom.toBuilder()
                                .name("SD")
                                .element(Element.S)
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

    private boolean isNTerminus(
            List<Residue> residues,
            int index) {

        if (index == 0) {
            return true;
        }

        return !isSequenceAdjacent(
                residues.get(index - 1),
                residues.get(index));
    }

    private boolean isCTerminus(
            List<Residue> residues,
            int index) {

        if (index == residues.size() - 1) {
            return true;
        }

        return !isSequenceAdjacent(
                residues.get(index),
                residues.get(index + 1));
    }

    private boolean isSequenceAdjacent(
            Residue previous,
            Residue current) {

        return current.getNumber() == previous.getNumber()
                || current.getNumber()
                == previous.getNumber() + 1;
    }

    private void rejectUnsupportedHisAuto(
            ProtonationConfig config) {

        if ("AUTO".equals(
                normalizeName(config.histidineState().name()))) {

            throw new IllegalArgumentException(
                    "hisProtonationState=AUTO is not supported "
                            + "without an external pKa/H-bond solver.");
        }
    }

    private String stateNote(
            String originalName,
            String preparedName,
            String baseTemplate,
            String amberTemplate) {

        if (!originalName.equals(preparedName)) {
            return originalName
                    + " converted to "
                    + preparedName;
        }

        if (!preparedName.equals(baseTemplate)) {
            return preparedName
                    + " assigned "
                    + baseTemplate;
        }

        if (!baseTemplate.equals(amberTemplate)) {
            return preparedName
                    + " assigned terminal template";
        }

        return preparedName
                + " assigned internal template";
    }

    private String normalizeName(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private String residueKey(
            String chainId,
            Residue residue) {

        return chainId
                + ":"
                + residue.getNumber()
                + insertionSuffix(residue);
    }

    private String insertionSuffix(Residue residue) {
        Character insertionCode =
                residue.getInsertionCode();

        return insertionCode == null
                || Character.isWhitespace(insertionCode)
                ? ""
                : insertionCode.toString();
    }

    private String residueLabel(
            String chainId,
            Residue residue) {

        return residue.getName()
                + " "
                + residueKey(chainId, residue);
    }

    private Protein copyWithStructure(
            Protein protein,
            Structure structure) {

        return new Protein(
                protein.id(),
                protein.uniProtId().orElse(null),
                protein.name(),
                protein.gene().orElse(null),
                protein.organism().orElse(null),
                protein.function().orElse(null),
                structure);
    }
}
