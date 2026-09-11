package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.forcefield.mmff.ForceFieldMMFF94;
import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.MolecularGraph;

/** Single package-private owner of OpenChemLib MMFF construction and energy evaluation. */
final class OclMmff94Support {
    private OclMmff94Support() { }

    static String resolve(String requested) throws MolecularBackendException {
        return switch (requested) {
            case "MMFF94" -> ForceFieldMMFF94.MMFF94;
            case "MMFF94S" -> ForceFieldMMFF94.MMFF94S;
            case "MMFF94SPLUS" -> ForceFieldMMFF94.MMFF94SPLUS;
            default -> throw new MolecularBackendException("unsupported force field: " + requested);
        };
    }

    static ForceFieldMMFF94 create(OclGraphMapper mapper, MolecularGraph graph,
                                   String forceField) throws MolecularBackendException {
        return create(mapper.toOcl(graph), forceField);
    }

    static ForceFieldMMFF94 create(OclGraphMapper.Mapping mapping, String forceField) {
        ForceFieldMMFF94.initialize(forceField);
        return new ForceFieldMMFF94(mapping.molecule(), forceField);
    }
}
