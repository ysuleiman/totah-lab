package totah.lab.hermes.component;

import totah.lab.hermes.file.mmcif.BoundComponentAtom;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic, curated classification of non-polymer components.
 *
 * <p>Rule order (first match wins), with component IDs matched
 * case-insensitively:
 * <ol>
 *   <li>{@link LigandClassification#SOLVENT} — water and heavy water</li>
 *   <li>{@link LigandClassification#METAL_ION} — common monoatomic ions</li>
 *   <li>{@link LigandClassification#BUFFER_ADDITIVE} — standard
 *       crystallization buffers/additives</li>
 *   <li>{@link LigandClassification#COFACTOR} — enzymatic cofactors and
 *       nucleotides (SAM/SAH, the methyltransferase cofactor/product pair,
 *       are explicitly kept here)</li>
 *   <li>{@link LigandClassification#POLYMER_MODIFICATION} — modified amino
 *       acids represented as HETATM records, not free ligands</li>
 *   <li>{@link LigandClassification#ORGANIC_LIGAND} — fallback for any
 *       unlisted component whose bound atoms show a multi-atom organic
 *       molecule (contains carbon and has more than 3 atoms)</li>
 *   <li>{@link LigandClassification#UNKNOWN} — anything else</li>
 * </ol>
 */
public final class LigandClassifier {

    private static final Set<String> SOLVENT = Set.of(
            "HOH", "WAT", "DOD");

    private static final Set<String> METAL_ION = Set.of(
            "ZN", "MG", "MN", "CA", "NA", "K", "CL", "FE", "FE2", "CU",
            "CU1", "CO", "NI", "CD", "HG", "I", "BR", "F", "LI", "RB",
            "CS", "SR", "BA", "YB", "SM", "GD", "PT", "AU", "IR", "OS",
            "RU", "MO", "W", "V");

    private static final Set<String> BUFFER_ADDITIVE = Set.of(
            "GOL", "EDO", "PEG", "PGE", "1PE", "PE4", "P33", "SO4", "PO4",
            "ACT", "ACE", "CIT", "MPD", "DMS", "TRS", "MES", "EPE", "TLA",
            "NH4", "SCN", "NO3", "BME", "PG0", "PGR", "PGO", "PG6");

    private static final Set<String> COFACTOR = Set.of(
            "SAM", "SAH", "SIN", "FAD", "FMN", "NAD", "NAP", "NDP", "NAI",
            "ATP", "ADP", "GTP", "GDP", "HEM", "HEC", "COA", "ACO", "PLP",
            "THB", "B12", "MTA", "UMP");

    private static final Set<String> POLYMER_MODIFICATION = Set.of(
            "MSE", "CME", "MLZ", "MLY", "M3L", "CSO", "NLE", "ALY", "SEP",
            "HCS", "HIC", "SAR", "CSD", "SER", "SMC", "TPO");

    /**
     * Classifies a component by ID, using the atoms of one occurrence as
     * evidence for the organic fallback.
     *
     * @param componentId the CCD component ID (case-insensitive)
     * @param sampleAtoms atoms of a representative occurrence; may be empty
     */
    public LigandClassification classify(
            String componentId,
            List<BoundComponentAtom> sampleAtoms) {

        Objects.requireNonNull(componentId, "componentId");
        String id = componentId.toUpperCase(Locale.ROOT);

        if (SOLVENT.contains(id)) {
            return LigandClassification.SOLVENT;
        }
        if (METAL_ION.contains(id)) {
            return LigandClassification.METAL_ION;
        }
        if (BUFFER_ADDITIVE.contains(id)) {
            return LigandClassification.BUFFER_ADDITIVE;
        }
        if (COFACTOR.contains(id)) {
            return LigandClassification.COFACTOR;
        }
        if (POLYMER_MODIFICATION.contains(id)) {
            return LigandClassification.POLYMER_MODIFICATION;
        }
        if (sampleAtoms != null && isMultiAtomOrganic(sampleAtoms)) {
            return LigandClassification.ORGANIC_LIGAND;
        }
        return LigandClassification.UNKNOWN;
    }

    private static boolean isMultiAtomOrganic(List<BoundComponentAtom> atoms) {
        return atoms.size() > 3
                && atoms.stream().anyMatch(atom -> "C".equalsIgnoreCase(atom.element()));
    }
}
