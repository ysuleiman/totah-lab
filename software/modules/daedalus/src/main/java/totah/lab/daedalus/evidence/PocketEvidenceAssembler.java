package totah.lab.daedalus.evidence;

import totah.lab.athena.pocket.evidence.PocketEvidence;

/** Composition boundary from Hermes/Gaia source truth into Athena evidence. */
public interface PocketEvidenceAssembler {

    PocketEvidence assemble(PocketEvidenceAssemblyRequest request);
}
