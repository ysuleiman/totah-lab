package totah.lab.athena.pocket.pocketmatch;

import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

/**
 * Builds PocketMatch signatures from pocket residues and their actual
 * atom coordinates.
 *
 * <p>Signature construction is deliberately separated from signature
 * comparison ({@link PocketMatchComparator}) so signatures can be
 * persisted and reused across comparisons.</p>
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
public interface PocketMatchSignatureFactory {

    PocketMatchSignature describe(
            Structure structure,
            Pocket pocket);
}
