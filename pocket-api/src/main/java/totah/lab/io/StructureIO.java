package totah.lab.io;

import org.biojava.nbio.structure.Chain;
import org.biojava.nbio.structure.Group;
import org.biojava.nbio.structure.ResidueNumber;
import org.biojava.nbio.structure.io.PDBFileReader;
import org.biojava.nbio.structure.io.cif.CifStructureConverter;
import totah.lab.protein.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class StructureIO {

    private StructureIO() {
    }

    public static Structure load(Path structurePath) throws IOException {
        Objects.requireNonNull(structurePath, "structurePath is null");

        String name = structurePath.getFileName().toString().toLowerCase();
        org.biojava.nbio.structure.Structure bioStructure;

        if (name.endsWith(".pdb")) {
            bioStructure = new PDBFileReader().getStructure(structurePath.toFile());
        } else if (name.endsWith(".cif") || name.endsWith(".mmcif")) {
            bioStructure = CifStructureConverter.fromPath(structurePath);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported structure format: " + name);
        }

        List<Residue> residues = new ArrayList<>();

        for (Chain chain : bioStructure.getChains()) {
            for (Group group : chain.getAtomGroups()) {
                residues.add(buildResidue(group));
            }
        }

        return new Structure(residues);
    }

    private static Residue buildResidue(Group group) {
        ResidueNumber residueNumber = group.getResidueNumber();

        return Residue.builder()
                .chain(residueNumber.getChainName())
                .number(residueNumber.getSeqNum())
                .insertionCode(
                        residueNumber.getInsCode() != null
                                ? residueNumber.getInsCode()
                                : ' ')
                .name(group.getPDBName().trim())
                .atoms(buildAtoms(group))
                .build();
    }

    private static List<Atom> buildAtoms(Group group) {
        if (group == null || group.getAtoms() == null || group.getAtoms().isEmpty()) {
            return Collections.emptyList();
        }

        List<Atom> atoms = new ArrayList<>(group.getAtoms().size());

        for (org.biojava.nbio.structure.Atom bioAtom : group.getAtoms()) {
            org.biojava.nbio.structure.Element bioElement = bioAtom.getElement();
            bioAtom.getPDBserial();
            atoms.add(
                    Atom.builder().pdbSerial(bioAtom.getPDBserial())
                            .name(bioAtom.getName().trim())
                            .position(new Point3D(
                                    bioAtom.getX(),
                                    bioAtom.getY(),
                                    bioAtom.getZ()))
                            .charge(0.0)
                            .amberType(null)
                            .occupancy(bioAtom.getOccupancy())
                            .bFactor(bioAtom.getTempFactor())
                            .element(
                                    Element.builder()
                                            .atomicNumber(bioElement.getAtomicNumber())
                                            .atomicMass(bioElement.getAtomicMass())
                                            .symbol(bioElement.name().trim())
                                            .covalentRadius(bioElement.getCovalentRadius())
                                            .vdwRadius(bioElement.getVDWRadius())
                                            .build())
                            .build());
        }

        return atoms;
    }
}