package totah.lab.mettl7.campaign.v2;

import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.sdf.SdfLigand;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Restores a docked pose onto its checksum-verified frozen SDF atom graph. */
public record Mettl7FrozenPoseLigand(
        Structure structure,
        FormalChargeAssignments formalCharges,
        Map<Integer, Integer> sdfToPdbqtIndex) {

    public static Mettl7FrozenPoseLigand reconstruct(SdfLigand frozen, PdbqtModel pose)
            throws IOException {
        List<Atom> sdfAtoms = frozen.ligand().structure().getChains().getFirst()
                .residues().getFirst().getAtoms();
        Map<Integer, Integer> indexMap = parseIndexMap(pose.remarks());
        Map<Integer, PdbqtAtom> poseBySerial = new LinkedHashMap<>();
        pose.atoms().stream().filter(atom -> !atom.autodockType().startsWith("G"))
                .forEach(atom -> poseBySerial.put(atom.serial(), atom));

        List<Atom> atoms = new ArrayList<>();
        Map<Integer, AtomReference> references = new LinkedHashMap<>();
        Map<AtomReference, Integer> charges = new LinkedHashMap<>();
        for (int sdfIndex = 0; sdfIndex < sdfAtoms.size(); sdfIndex++) {
            Integer poseSerial = indexMap.get(sdfIndex + 1);
            if (poseSerial == null) {
                if (sdfAtoms.get(sdfIndex).isHeavyAtom()) {
                    throw new IOException("Meeko index map omits heavy SDF atom " + (sdfIndex + 1));
                }
                continue;
            }
            PdbqtAtom posed = poseBySerial.get(poseSerial);
            if (posed == null) throw new IOException("Meeko index maps missing pose atom " + poseSerial);
            Atom source = sdfAtoms.get(sdfIndex);
            String posedElement = posed.element();
            boolean customTypeCompatible = posedElement.toUpperCase(java.util.Locale.ROOT)
                    .startsWith(source.getElement().symbol().toUpperCase(java.util.Locale.ROOT));
            if (!posedElement.equalsIgnoreCase(source.getElement().symbol()) && !customTypeCompatible) {
                throw new IOException("Element mismatch for " + frozen.title() + " SDF atom "
                        + (sdfIndex + 1) + ": " + source.getElement().symbol()
                        + " versus pose " + posed.element());
            }
            Atom restored = source.toBuilder().pdbSerial(posed.serial())
                    .position(posed.position()).charge(posed.partialCharge())
                    .autoDockType(posed.autodockType()).build();
            atoms.add(restored);
            AtomReference reference = new AtomReference("L", 1, ' ', restored.getName());
            references.put(sdfIndex, reference);
            charges.put(reference, frozen.formalCharges().get(sdfIndex));
        }
        if (atoms.size() != poseBySerial.size()) {
            throw new IOException("Meeko index map is not one-to-one with pose atoms");
        }
        List<Bond> bonds = frozen.bonds().stream()
                .filter(bond -> references.containsKey(bond.atomIndexA())
                        && references.containsKey(bond.atomIndexB()))
                .map(bond -> new Bond(references.get(bond.atomIndexA()),
                        references.get(bond.atomIndexB()), bond.order())).toList();
        Structure structure = new Structure(List.of(new Chain("L", List.of(
                new Residue("UNL", 1, atoms)))), bonds);
        return new Mettl7FrozenPoseLigand(structure,
                new FormalChargeAssignments(charges), indexMap);
    }

    static Map<Integer, Integer> parseIndexMap(List<String> remarks) throws IOException {
        List<Integer> tokens = new ArrayList<>();
        for (String remark : remarks) {
            if (!remark.startsWith("REMARK INDEX MAP ")) continue;
            String[] fields = remark.substring("REMARK INDEX MAP ".length()).trim().split("\\s+");
            for (String field : fields) tokens.add(Integer.parseInt(field));
        }
        if (tokens.isEmpty() || tokens.size() % 2 != 0) {
            throw new IOException("Missing or malformed Meeko REMARK INDEX MAP");
        }
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < tokens.size(); i += 2) {
            if (result.put(tokens.get(i), tokens.get(i + 1)) != null) {
                throw new IOException("Duplicate SDF atom in Meeko index map");
            }
        }
        return Map.copyOf(result);
    }
}
