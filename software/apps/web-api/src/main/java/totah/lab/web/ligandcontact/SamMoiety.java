package totah.lab.web.ligandcontact;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Chemical moieties of the SAM/SAH ligand, used to describe which part
 * of the ligand a protein residue faces.
 *
 * <p>Atom names follow the CCD naming of SAM (and SAH, which simply
 * lacks the sulfonium methyl carbon CE):</p>
 *
 * <ul>
 *     <li>{@link #ADENINE}: N1 C2 N3 C4 C5 C6 N6 N7 C8 N9</li>
 *     <li>{@link #RIBOSE}: C1' C2' O2' C3' O3' C4' O4' C5'</li>
 *     <li>{@link #SULFONIUM}: SD CE (SAH contributes SD only)</li>
 *     <li>{@link #METHIONINE}: N CA C O OXT CB CG</li>
 * </ul>
 */
public enum SamMoiety {

    ADENINE(Set.of(
            "N1", "C2", "N3", "C4", "C5", "C6", "N6", "N7", "C8", "N9"
    )),
    RIBOSE(Set.of(
            "C1'", "C2'", "O2'", "C3'", "O3'", "C4'", "O4'", "C5'"
    )),
    SULFONIUM(Set.of("SD", "CE")),
    METHIONINE(Set.of("N", "CA", "C", "O", "OXT", "CB", "CG"));

    private static final Map<String, SamMoiety> BY_ATOM_NAME =
            indexByAtomName();

    private final Set<String> atomNames;

    SamMoiety(Set<String> atomNames) {
        this.atomNames = atomNames;
    }

    public Set<String> atomNames() {
        return atomNames;
    }

    /**
     * Classifies a ligand atom name into its moiety. Primed ribose
     * names are matched exactly (e.g. {@code C5'}); unknown names
     * yield {@link Optional#empty()}.
     */
    public static Optional<SamMoiety> classify(String atomName) {
        if (atomName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                BY_ATOM_NAME.get(atomName.trim())
        );
    }

    private static Map<String, SamMoiety> indexByAtomName() {
        Map<String, SamMoiety> index = new HashMap<>();
        for (SamMoiety moiety : values()) {
            for (String atomName : moiety.atomNames) {
                index.put(atomName, moiety);
            }
        }
        return Map.copyOf(index);
    }
}
