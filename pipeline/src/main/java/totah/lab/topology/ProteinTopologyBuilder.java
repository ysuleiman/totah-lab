package totah.lab.topology;

import org.biojava.nbio.structure.Chain;
import org.biojava.nbio.structure.Group;
import org.biojava.nbio.structure.ResidueNumber;
import totah.lab.protein.*;

import java.util.*;

public class ProteinTopologyBuilder {
    private final AmberResidueTemplateLibrary loader;
    public ProteinTopologyBuilder(AmberResidueTemplateLibrary loader){
        this.loader = loader;
    }

    /**
     * STAGE 1 ENTRY POINT:
     * Converts raw BioJava chain/group arrays into immutable base heavy-atom domain objects.
     */
    public List<Residue> build(org.biojava.nbio.structure.Structure structure) {
        List<Residue> residues = new ArrayList<>();

        for (Chain chain : structure.getChains()) {
            List<Group> groups = chain.getAtomGroups();
            for (Group group : groups) {
                Residue residue = buildResidue(group);
                residues.add(residue);
            }
        }
        return residues;
    }

    private Residue buildResidue(Group group) {
        ResidueNumber residueNumber = group.getResidueNumber();
        return Residue.builder()
                .chain(residueNumber.getChainName())
                .number(residueNumber.getSeqNum())
                .insertionCode(residueNumber.getInsCode() != null ? residueNumber.getInsCode() : ' ')
                .name(group.getPDBName().trim())
                .atoms(buildAtoms(group)) // Ingest basic heavy atoms matching initial templates
                .build();
    }

    private List<Atom> buildAtoms(Group group) {
        if (group == null || group.getAtoms() == null || group.getAtoms().isEmpty()) {
            return Collections.emptyList();
        }

        ResidueTemplate template = this.loader.getTemplate(group.getPDBName().trim());

        List<Atom> atoms = new ArrayList<>();
        for (org.biojava.nbio.structure.Atom bioAtom : group.getAtoms()) {
            String name = bioAtom.getName().trim();
            org.biojava.nbio.structure.Element bioElement = bioAtom.getElement();
            double charge = 0;
            String amberType = null;

            if (template != null) {
                AtomTemplate amberAtom = template.getAtom(name);
                if (amberAtom != null) {
                    charge = amberAtom.getCharge();
                    amberType = amberAtom.getAmberType();
                }
            }

            Point3D pos = new Point3D(bioAtom.getX(), bioAtom.getY(), bioAtom.getZ());

            atoms.add(Atom.builder()
                    .name(name)
                    .amberType(amberType)
                    .position(pos)
                    .charge(charge)
                    .occupancy(bioAtom.getOccupancy())
                    .bFactor(bioAtom.getTempFactor())
                    .element(Element.fromSymbol(bioElement.name()))
                    .build());
        }
        return atoms;
    }
}
