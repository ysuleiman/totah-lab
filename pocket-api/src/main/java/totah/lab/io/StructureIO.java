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

        for (org.biojava.nbio.structure.Atom bioAtom : representativeAtoms(group)) {
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

    private static List<org.biojava.nbio.structure.Atom> representativeAtoms(Group group) {
        Map<String, AtomCandidate> candidates = new LinkedHashMap<>();
        int order = 0;
        order = collectCandidates(candidates, group.getAtoms(), order);
        if (group.hasAltLoc()) {
            for (Group altLocGroup : group.getAltLocs()) {
                order = collectCandidates(candidates, altLocGroup.getAtoms(), order);
            }
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingInt(AtomCandidate::order))
                .map(AtomCandidate::atom)
                .toList();
    }

    private static int collectCandidates(Map<String, AtomCandidate> candidates,
                                         List<org.biojava.nbio.structure.Atom> atoms,
                                         int order) {
        if (atoms == null) {
            return order;
        }
        for (org.biojava.nbio.structure.Atom atom : atoms) {
            String atomName = atom.getName() == null ? "" : atom.getName().trim();
            AtomCandidate candidate = new AtomCandidate(atom, order++);
            AtomCandidate previous = candidates.get(atomName);
            if (previous == null || isBetterAltLoc(candidate.atom(), previous.atom())) {
                candidates.put(atomName, previous == null
                        ? candidate
                        : new AtomCandidate(candidate.atom(), previous.order()));
            }
        }
        return order;
    }

    private static boolean isBetterAltLoc(org.biojava.nbio.structure.Atom candidate,
                                          org.biojava.nbio.structure.Atom current) {
        int occupancy = Float.compare(candidate.getOccupancy(), current.getOccupancy());
        if (occupancy != 0) {
            return occupancy > 0;
        }
        int altLocRank = Integer.compare(altLocRank(candidate.getAltLoc()), altLocRank(current.getAltLoc()));
        return altLocRank > 0;
    }

    private static int altLocRank(Character altLoc) {
        if (altLoc != null && Character.toUpperCase(altLoc) == 'A') {
            return 2;
        }
        if (altLoc == null || altLoc == ' ' || altLoc == '\0') {
            return 1;
        }
        return 0;
    }

    private record AtomCandidate(org.biojava.nbio.structure.Atom atom, int order) {
    }
}
