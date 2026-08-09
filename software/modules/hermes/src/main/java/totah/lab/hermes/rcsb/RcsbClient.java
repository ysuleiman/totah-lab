package totah.lab.hermes.rcsb;

import java.util.Optional;
import java.nio.file.Path;
import java.util.List;

/** Retrieves experimental structure metadata from the RCSB Protein Data Bank. */
public interface RcsbClient {

    Optional<RcsbEntry> fetch(String pdbId)
            throws RcsbException, InterruptedException;

    /**
     * Fetches the structure-quality summary (method, resolution,
     * ligands, chain and assembly counts) for one entry. Empty when the
     * PDB ID has no RCSB entry.
     */
    Optional<RcsbEntrySummary> fetchSummary(String pdbId)
            throws RcsbException, InterruptedException;

    List<RcsbSearchHit> search(RcsbSearchCriteria criteria)
            throws RcsbException, InterruptedException;

    /**
     * Runs an attribute search and fetches the summary of every hit.
     * One HTTP request per hit, sequential; expect this to take a while
     * for large result sets.
     */
    List<RcsbEntrySummary> searchEntries(RcsbAttributeSearch criteria)
            throws RcsbException, InterruptedException;

    Path downloadCif(String pdbId, Path destination)
            throws RcsbException, InterruptedException;
}
