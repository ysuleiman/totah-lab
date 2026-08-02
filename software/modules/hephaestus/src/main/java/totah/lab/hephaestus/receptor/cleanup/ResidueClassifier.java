package totah.lab.hephaestus.receptor.cleanup;




import totah.lab.gaia.classification.ResidueClassificationEvidence;
import totah.lab.gaia.classification.ResidueClassification;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;

import java.util.List;
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

        List<ResidueClassificationEvidence> evidenceList =
                residue.getClassificationEvidence();

        for (ResidueClassificationEvidence evidence : evidenceList) {
            ResidueClassification classification = evidence.classification();
            if (classification == ResidueClassification.WATER) {
                return ResidueKind.WATER;
            }
            if (classification == ResidueClassification.STANDARD_AMINO_ACID) {
                return ResidueKind.STANDARD_AMINO_ACID;
            }
            if (classification == ResidueClassification.MODIFIED_AMINO_ACID) {
                return ResidueKind.MODIFIED_AMINO_ACID;
            }
            if (classification == ResidueClassification.LIGAND
                    || classification == ResidueClassification.COFACTOR
                    || classification == ResidueClassification.HETERO) {
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
        if (atom.getElement() == null || atom.getElement().symbol() == null) {
            return false;
        }
        return METAL_ELEMENTS.contains(atom.getElement().symbol().toUpperCase(Locale.ROOT))
                || metalIonPolicy.isKnownIonResidue(residue);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
