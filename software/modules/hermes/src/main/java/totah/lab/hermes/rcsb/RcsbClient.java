package totah.lab.hermes.rcsb;

import java.util.Optional;
import java.nio.file.Path;
import java.util.List;

/** Retrieves experimental structure metadata from the RCSB Protein Data Bank. */
public interface RcsbClient {

    Optional<RcsbEntry> fetch(String pdbId)
            throws RcsbException, InterruptedException;

    List<RcsbSearchHit> search(RcsbSearchCriteria criteria)
            throws RcsbException, InterruptedException;

    Path downloadCif(String pdbId, Path destination)
            throws RcsbException, InterruptedException;
}
