package totah.lab.proteus.protein.relaxation;

import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Structure;

import java.util.Set;

/**
 * Relaxes a variant structure, moving only the given atoms. Interface only;
 * the hephaestus-backed implementation is a later phase.
 */
public interface VariantRelaxationService {

    Structure relax(Structure structure, Set<AtomReference> movableAtoms);
}
