package totah.lab.athena.pocket.pocketmatch;

/**
 * Compares two PocketMatch signatures and reports matched-distance
 * counts with symmetric and directional normalized scores.
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for provenance. Full citation:</p>
 *
 * <pre>
 * Yeturu K, Chandra N.
 * PocketMatch: A new algorithm to compare binding sites in protein
 * structures. BMC Bioinformatics. 2008;9:543.
 * </pre>
 *
 * <p>The method itself is not original to this codebase.</p>
 */
public interface PocketMatchComparator {

    PocketMatchComparison compare(
            PocketMatchSignature first,
            PocketMatchSignature second);
}
