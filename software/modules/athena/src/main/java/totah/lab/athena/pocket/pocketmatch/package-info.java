/**
 * PocketMatch-style pocket retrieval algorithm.
 *
 * <p>This package contains Athena's clean-room implementation of the
 * PocketMatch pocket-comparison methodology: pockets are represented
 * by ninety sorted distance lists (fifteen unordered amino-acid
 * chemistry-group pairs crossed with six unordered
 * representative-point-type pairs), and signatures are compared by an
 * incremental two-pointer matcher under a distance tolerance. It is
 * evaluated as an alternative Stage 1 retrieval representation and
 * benchmarking baseline; the production retrieval path is
 * unaffected.</p>
 *
 * <p>The implementation is original Java code derived from the
 * published algorithmic description. It does not copy or adapt code
 * from the reference implementation (which is GPLv3-licensed), and it
 * does not claim the method itself as original:</p>
 *
 * <pre>
 * Yeturu K, Chandra N.
 * PocketMatch: A new algorithm to compare binding sites in protein
 * structures. BMC Bioinformatics. 2008;9:543.
 * </pre>
 */
package totah.lab.athena.pocket.pocketmatch;
