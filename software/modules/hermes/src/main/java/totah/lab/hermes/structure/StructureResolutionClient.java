package totah.lab.hermes.structure;

import java.util.Optional;

/**
 * Resolves the best available three-dimensional structure for a protein.
 *
 * <p>The supplied identifier is expected to be a UniProt accession. Implementations
 * may inspect multiple structure providers and apply a defined preference policy.</p>
 */
public interface StructureResolutionClient {

    /**
     * Resolves the preferred available structure for a UniProt accession.
     *
     * @param uniProtAccession UniProt accession, such as {@code Q6UX53}
     * @return the preferred structure, or empty when no supported structure exists
     * @throws StructureResolutionException when resolution fails
     * @throws InterruptedException when the calling thread is interrupted
     */
    Optional<ResolvedProteinStructure> resolve(String uniProtAccession)
            throws StructureResolutionException, InterruptedException;

    /**
     * Resolves all known supported structures for a UniProt accession.
     *
     * @param uniProtAccession UniProt accession
     * @return resolution result containing all discovered structures
     * @throws StructureResolutionException when resolution fails
     * @throws InterruptedException when the calling thread is interrupted
     */
    ProteinStructureResolution resolveAll(String uniProtAccession)
            throws StructureResolutionException, InterruptedException;
}