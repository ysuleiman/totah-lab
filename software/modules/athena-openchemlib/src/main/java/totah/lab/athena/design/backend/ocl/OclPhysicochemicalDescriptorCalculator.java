package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.prediction.MolecularPropertyHelper;
import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.ligand.screening.PhysicochemicalGate;

/** Toolkit adapter for the descriptor vector consumed by Athena's generic hard gate. */
public final class OclPhysicochemicalDescriptorCalculator {
    private final OclGraphMapper mapper = new OclGraphMapper();

    public PhysicochemicalGate.Descriptors calculate(MolecularGraph graph)
            throws MolecularBackendException {
        try {
            var molecule = mapper.toOcl(graph).molecule();
            molecule.ensureHelperArrays(Molecule.cHelperRings);
            int heavy = (int) property(molecule, MolecularPropertyHelper.MOLECULAR_PROPERTY_NON_H_ATOMS);
            long sp3Carbon = graph.atoms().stream().filter(a -> a.element().equals("C") && !a.aromatic())
                    .filter(a -> graph.bonds().stream().filter(b -> b.firstAtomId().equals(a.id())
                            || b.secondAtomId().equals(a.id())).allMatch(b -> b.order() == MolecularGraph.BondOrder.SINGLE))
                    .count();
            long carbon = graph.atoms().stream().filter(a -> a.element().equals("C")).count();
            return new PhysicochemicalGate.Descriptors(
                    property(molecule, MolecularPropertyHelper.MOLECULAR_PROPERTY_MOLWEIGHT),
                    graph.atoms().stream().mapToInt(MolecularGraph.Atom::formalCharge).sum(),
                    (int) property(molecule, MolecularPropertyHelper.MOLECULAR_PROPERTY_HDONORS),
                    (int) property(molecule, MolecularPropertyHelper.MOLECULAR_PROPERTY_HACCEPTORS),
                    (int) property(molecule, MolecularPropertyHelper.MOLECULAR_PROPERTY_ROTATABLEBONDS),
                    property(molecule, MolecularPropertyHelper.MOLECULAR_PROPERTY_TPSA),
                    property(molecule, MolecularPropertyHelper.MOLECULAR_PROPERTY_CLOGP),
                    (int) property(molecule, MolecularPropertyHelper.MOLECULAR_PROPERTY_AROMRINGCOUNT),
                    heavy, carbon == 0 ? 0.0 : (double) sp3Carbon / carbon);
        } catch (RuntimeException exception) {
            throw new MolecularBackendException("OCL descriptor calculation failed", exception);
        }
    }

    private static double property(com.actelion.research.chem.StereoMolecule molecule, int type) {
        return MolecularPropertyHelper.calculateProperty(molecule, type);
    }
}
