package totah.lab.athena.pocket.pocketmatch;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Amino-acid chemistry groups used by the PocketMatch pocket representation.
 *
 * <p>This grouping is deliberately independent from
 * {@code totah.lab.athena.pocket.compare.residue.ResidueChemistry}: it
 * reproduces the five-group classification published with the PocketMatch
 * method rather than the classification used by the production residue
 * correspondence pipeline.</p>
 *
 * <p>Exact residue membership:</p>
 *
 * <ul>
 *     <li>{@link #ALIPHATIC_SPECIAL}: ALA, VAL, ILE, LEU, GLY, PRO</li>
 *     <li>{@link #POSITIVE}: LYS, ARG, HIS</li>
 *     <li>{@link #ACIDIC_AMIDE}: ASP, GLU, GLN, ASN</li>
 *     <li>{@link #AROMATIC}: TYR, PHE, TRP</li>
 *     <li>{@link #CYSTEINE_POLAR}: CYS, SER, THR</li>
 * </ul>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 *
 * <p>The method itself is not original to this codebase.</p>
 */
public enum PocketMatchResidueGroup {

    ALIPHATIC_SPECIAL(Set.of("ALA", "VAL", "ILE", "LEU", "GLY", "PRO")),
    POSITIVE(Set.of("LYS", "ARG", "HIS")),
    ACIDIC_AMIDE(Set.of("ASP", "GLU", "GLN", "ASN")),
    AROMATIC(Set.of("TYR", "PHE", "TRP")),
    CYSTEINE_POLAR(Set.of("CYS", "SER", "THR"));

    private static final Map<String, PocketMatchResidueGroup> BY_RESIDUE_NAME =
            indexByResidueName();

    private final Set<String> residueNames;

    PocketMatchResidueGroup(Set<String> residueNames) {
        this.residueNames = residueNames;
    }

    /**
     * Returns the three-letter residue names belonging to this group.
     */
    public Set<String> residueNames() {
        return residueNames;
    }

    /**
     * Classifies a three-letter residue name into its PocketMatch group.
     * Names are matched case-insensitively. Unknown or non-standard
     * residues yield {@link Optional#empty()}.
     */
    public static Optional<PocketMatchResidueGroup> classify(
            String residueName
    ) {
        if (residueName == null || residueName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                BY_RESIDUE_NAME.get(residueName.trim().toUpperCase())
        );
    }

    private static Map<String, PocketMatchResidueGroup> indexByResidueName() {
        Map<String, PocketMatchResidueGroup> index = new HashMap<>();
        for (PocketMatchResidueGroup group : values()) {
            for (String residueName : group.residueNames) {
                index.put(residueName, group);
            }
        }
        return Map.copyOf(index);
    }
}
