package totah.lab.daedalus.ligandprep;

import java.util.List;

/**
 * Deterministic sample of Meeko-prepared ligands with their source
 * SDFs. Factored behind an interface so tests never touch a database.
 */
public interface LigandPrepSampler {

    List<LigandPrepSample> sample(int count) throws Exception;
}
