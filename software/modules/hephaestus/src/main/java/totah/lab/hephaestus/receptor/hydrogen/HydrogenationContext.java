package totah.lab.hephaestus.receptor.hydrogen;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable per-run state shared by receptor hydrogenation components.
 *
 * The referenced clash checker and insertion service maintain the mutable
 * spatial state as atoms are added during one hydrogenation run.
 */
public final class HydrogenationContext {

    private final ProtonationConfig config;
    private final String chainId;
    private final List<Residue> chainResidues;
    private final SpatialClashChecker clashChecker;
    private final HydrogenInsertion insertion;
    private final HydrogenPositionCalculator positionCalculator;
    private final HydrogenAtomFactory atomFactory;
    private final Set<String> disulfideResidueKeys;
    private final List<Atom> metalAtoms;
    private final Map<String, String> amberTemplates;

    public HydrogenationContext(
            ProtonationConfig config,
            String chainId,
            List<Residue> chainResidues,
            SpatialClashChecker clashChecker,
            HydrogenPositionCalculator positionCalculator,
            HydrogenAtomFactory atomFactory,
            Set<String> disulfideResidueKeys,
            List<Atom> metalAtoms,
            Map<String, String> amberTemplates) {

        this.config = Objects.requireNonNull(
                config,
                "config");

        this.chainId = Objects.requireNonNull(
                chainId,
                "chainId").trim();

        if (this.chainId.isEmpty()) {
            throw new IllegalArgumentException(
                    "chainId must not be blank.");
        }

        Objects.requireNonNull(
                chainResidues,
                "chainResidues");

        if (chainResidues.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "chainResidues must not contain null elements.");
        }

        this.chainResidues = List.copyOf(chainResidues);

        this.clashChecker = Objects.requireNonNull(
                clashChecker,
                "clashChecker");

        this.positionCalculator = Objects.requireNonNull(
                positionCalculator,
                "positionCalculator");

        this.atomFactory = Objects.requireNonNull(
                atomFactory,
                "atomFactory");

        this.insertion = new HydrogenInsertion(
                clashChecker,
                config.clashCutoff());

        this.disulfideResidueKeys =
                disulfideResidueKeys == null
                        ? Set.of()
                        : Set.copyOf(disulfideResidueKeys);

        this.metalAtoms = metalAtoms == null
                ? List.of()
                : List.copyOf(metalAtoms);

        this.amberTemplates = amberTemplates == null
                ? Map.of()
                : normalizeTemplates(amberTemplates);
    }

    public ProtonationConfig config() {
        return config;
    }

    public String chainId() {
        return chainId;
    }

    public List<Residue> chainResidues() {
        return chainResidues;
    }

    public SpatialClashChecker clashChecker() {
        return clashChecker;
    }

    public HydrogenInsertion insertion() {
        return insertion;
    }

    public HydrogenPositionCalculator positionCalculator() {
        return positionCalculator;
    }

    public HydrogenAtomFactory atomFactory() {
        return atomFactory;
    }

    public Set<String> disulfideResidueKeys() {
        return disulfideResidueKeys;
    }

    public List<Atom> metalAtoms() {
        return metalAtoms;
    }

    public Map<String, String> amberTemplates() {
        return amberTemplates;
    }

    public boolean isDisulfideCysteine(
            String chainId,
            Residue residue) {

        return disulfideResidueKeys.contains(
                residueKey(chainId, residue));
    }

    public String amberTemplateName(
            String chainId,
            Residue residue) {

        return amberTemplates.get(
                residueKey(chainId, residue));
    }

    public String baseTemplateName(
            String chainId,
            Residue residue) {

        String template =
                amberTemplateName(chainId, residue);

        if (template == null || template.isBlank()) {
            return null;
        }

        String normalized =
                template.trim().toUpperCase(Locale.ROOT);

        if (isTerminalTemplate(normalized)) {
            return normalized.substring(1);
        }

        return normalized;
    }

    public boolean usesNTerminalTemplate(
            String chainId,
            Residue residue) {

        return usesTerminalTemplate(
                chainId,
                residue,
                'N');
    }

    public boolean usesCTerminalTemplate(
            String chainId,
            Residue residue) {

        return usesTerminalTemplate(
                chainId,
                residue,
                'C');
    }

    public boolean isNearMetal(Point3D position) {
        Objects.requireNonNull(position, "position");

        double cutoff =
                config.metalCoordinationCutoff();

        for (Atom metal : metalAtoms) {
            if (metal == null || metal.getPosition() == null) {
                continue;
            }

            if (position.distance(metal.getPosition()) <= cutoff) {
                return true;
            }
        }

        return false;
    }

    public boolean tryAdd(
            List<Atom> atoms,
            Atom candidate) {

        return insertion.tryAdd(
                atoms,
                candidate,
                null);
    }

    public boolean tryAdd(
            List<Atom> atoms,
            Atom candidate,
            Atom bondedParent) {

        return insertion.tryAdd(
                atoms,
                candidate,
                bondedParent);
    }

    public String residueKey(
            String chainId,
            Residue residue) {

        Objects.requireNonNull(chainId, "chainId");
        Objects.requireNonNull(residue, "residue");

        if (chainId.isBlank()) {
            throw new IllegalArgumentException(
                    "chainId must not be blank.");
        }

        String normalizedChainId = chainId.trim();

        if (!this.chainId.equals(normalizedChainId)) {
            throw new IllegalArgumentException(
                    "Residue belongs to chain "
                            + normalizedChainId
                            + " but this context is for chain "
                            + this.chainId
                            + ".");
        }

        return normalizedChainId
                + ":"
                + residue.getNumber()
                + insertionSuffix(residue);
    }

    private boolean usesTerminalTemplate(
            String chainId,
            Residue residue,
            char expectedPrefix) {

        String template =
                amberTemplateName(chainId, residue);

        return template != null
                && template.length() == 4
                && Character.toUpperCase(template.charAt(0))
                == Character.toUpperCase(expectedPrefix);
    }

    private boolean isTerminalTemplate(String template) {
        if (template.length() != 4) {
            return false;
        }

        char prefix =
                Character.toUpperCase(template.charAt(0));

        return prefix == 'N' || prefix == 'C';
    }

    private String insertionSuffix(Residue residue) {
        Character insertionCode =
                residue.getInsertionCode();

        return insertionCode == null
                || Character.isWhitespace(insertionCode)
                ? ""
                : insertionCode.toString();
    }

    private static Map<String, String> normalizeTemplates(
            Map<String, String> templates) {

        java.util.LinkedHashMap<String, String> normalized =
                new java.util.LinkedHashMap<>();

        for (Map.Entry<String, String> entry
                : templates.entrySet()) {

            String key = Objects.requireNonNull(
                    entry.getKey(),
                    "amber template key");

            String value = Objects.requireNonNull(
                    entry.getValue(),
                    "amber template value");

            if (key.isBlank()) {
                throw new IllegalArgumentException(
                        "Amber template key must not be blank.");
            }

            if (value.isBlank()) {
                throw new IllegalArgumentException(
                        "Amber template value must not be blank.");
            }

            normalized.put(
                    key.trim(),
                    value.trim().toUpperCase(Locale.ROOT));
        }

        return Map.copyOf(normalized);
    }
}
