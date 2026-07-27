package totah.lab.pipeline.cleanup;

import totah.lab.protein.Atom;
import totah.lab.protein.Residue;
import totah.lab.protein.ResidueClassificationEvidence;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Classifies structural residue groups without deciding workflow disposition.
 */
public final class ResidueClassifier {

    private static final Set<String> STANDARD_AMINO_ACIDS = Set.of(
            "ALA", "ARG", "ASN", "ASP", "CYS", "GLN", "GLU", "GLY", "HIS", "ILE",
            "LEU", "LYS", "MET", "PHE", "PRO", "SER", "THR", "TRP", "TYR", "VAL");

    private static final Set<String> WATER_NAMES = Set.of("HOH", "WAT", "DOD", "H2O");

    private static final Set<String> METAL_ELEMENTS = Set.of(
            "LI", "NA", "K", "RB", "CS",
            "BE", "MG", "CA", "SR", "BA",
            "SC", "TI", "V", "CR", "MN", "FE", "CO", "NI", "CU", "ZN",
            "Y", "ZR", "NB", "MO", "TC", "RU", "RH", "PD", "AG", "CD",
            "LU", "HF", "TA", "W", "RE", "OS", "IR", "PT", "AU", "HG",
            "AL", "GA", "IN", "SN", "TL", "PB", "BI");

    private final MetalIonPolicy metalIonPolicy;

    public ResidueClassifier() {
        this(new MetalIonPolicy());
    }

    ResidueClassifier(MetalIonPolicy metalIonPolicy) {
        this.metalIonPolicy = Objects.requireNonNull(metalIonPolicy, "metalIonPolicy is null");
    }

    public ResidueKind classify(Residue residue) {
        Objects.requireNonNull(residue, "residue is null");

        if (isMonoatomicMetalOrKnownIon(residue)) {
            return ResidueKind.ION_OR_METAL;
        }

        ResidueClassificationEvidence evidence = residue.getResidueClassificationEvidence();
        if (evidence != null && evidence.available()) {
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
        }

        String name = normalizeName(residue.getName());
        if (STANDARD_AMINO_ACIDS.contains(name)) {
            return ResidueKind.STANDARD_AMINO_ACID;
        }
        if (WATER_NAMES.contains(name)) {
            return ResidueKind.WATER;
        }
        return ResidueKind.UNKNOWN;
    }

    private boolean isMonoatomicMetalOrKnownIon(Residue residue) {
        if (residue.getAtoms().size() != 1) {
            return false;
        }
        Atom atom = residue.getAtoms().getFirst();
        if (atom.getElement() == null || atom.getElement().getSymbol() == null) {
            return false;
        }
        return METAL_ELEMENTS.contains(atom.getElement().getSymbol().toUpperCase(Locale.ROOT))
                || metalIonPolicy.isKnownIonResidue(residue);
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

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
