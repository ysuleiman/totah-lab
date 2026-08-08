package totah.lab.hephaestus.ligand.topology;

import org.biojava.nbio.structure.chem.ChemComp;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.hermes.ccd.BioJavaCcdComponentMapper;
import totah.lab.hermes.ccd.CcdComponent;
import totah.lab.hermes.ccd.CcdComponentAtom;
import totah.lab.hermes.ccd.CcdComponentBond;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds immutable ligand topology by matching deposited atoms to a CCD entry. */
public final class CcdLigandTopologyBuilder {

    public LigandTopology build(Residue residue, ChemComp chemComp) {
        return build(residue, new BioJavaCcdComponentMapper().map(chemComp));
    }

    public LigandTopology build(Residue residue, CcdComponent component) {
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(component, "component");
        if (residue.isEmpty()) {
            throw new IllegalArgumentException("Cannot build topology for an empty ligand.");
        }

        Map<String, CcdComponentAtom> ccdAtoms = indexCcdAtoms(component);
        Map<String, Integer> deposited = indexDepositedAtoms(residue);
        List<String> missingHeavy = new ArrayList<>();
        List<String> extraHeavy = new ArrayList<>();
        for (CcdComponentAtom atom : ccdAtoms.values()) {
            if (!deposited.containsKey(normalize(atom.atomId()))
                    && !isHydrogen(atom.element())) {
                missingHeavy.add(atom.atomId());
            }
        }
        for (Atom atom : residue.getAtoms()) {
            if (!ccdAtoms.containsKey(normalize(atom.getName())) && !atom.isHydrogen()) {
                extraHeavy.add(atom.getName());
            }
        }
        if (!missingHeavy.isEmpty() || !extraHeavy.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ligand does not match CCD " + component.componentId()
                            + "; missing heavy atoms=" + missingHeavy
                            + ", extra heavy atoms=" + extraHeavy);
        }

        List<LigandAtomProperties> properties = new ArrayList<>();
        List<CcdAtomCoordinates> coordinates = new ArrayList<>();
        for (int index = 0; index < residue.getAtomCount(); index++) {
            Atom depositedAtom = residue.getAtoms().get(index);
            CcdComponentAtom ccdAtom = ccdAtoms.get(normalize(depositedAtom.getName()));
            if (ccdAtom == null) {
                throw new IllegalArgumentException(
                        "Deposited atom is absent from CCD: " + depositedAtom.getName());
            }
            properties.add(new LigandAtomProperties(
                    ccdAtom.atomId(), ccdAtom.formalCharge(),
                    ccdAtom.aromatic(), ccdAtom.leavingAtom()));
            coordinates.add(new CcdAtomCoordinates(
                    index, ccdAtom.modelPosition(), ccdAtom.idealPosition()));
        }

        return new LigandTopology(
                component.componentId(), residue.getAtomCount(),
                buildBonds(component, deposited), properties,
                missingHydrogens(component, ccdAtoms, deposited), coordinates);
    }

    private Map<String, CcdComponentAtom> indexCcdAtoms(CcdComponent component) {
        Map<String, CcdComponentAtom> result = new LinkedHashMap<>();
        for (CcdComponentAtom atom : component.atoms()) {
            String key = normalize(atom.atomId());
            if (key.isEmpty() || result.putIfAbsent(key, atom) != null) {
                throw new IllegalArgumentException("CCD has a blank or duplicate atom id.");
            }
        }
        return result;
    }

    private Map<String, Integer> indexDepositedAtoms(Residue residue) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < residue.getAtomCount(); index++) {
            String key = normalize(residue.getAtoms().get(index).getName());
            if (key.isEmpty() || result.putIfAbsent(key, index) != null) {
                throw new IllegalArgumentException(
                        "Ligand has a blank or duplicate atom name: " + key);
            }
        }
        return result;
    }

    private List<ChemicalBond> buildBonds(
            CcdComponent component, Map<String, Integer> deposited) {
        List<ChemicalBond> result = new ArrayList<>();
        Set<Long> endpoints = new HashSet<>();
        for (CcdComponentBond bond : component.bonds()) {
            Integer first = deposited.get(normalize(bond.atomIdA()));
            Integer second = deposited.get(normalize(bond.atomIdB()));
            if (first == null || second == null) {
                continue;
            }
            int low = Math.min(first, second);
            int high = Math.max(first, second);
            long key = ((long) low << 32) | (high & 0xffffffffL);
            if (!endpoints.add(key)) {
                throw new IllegalArgumentException("CCD has duplicate bond endpoints.");
            }
            result.add(new ChemicalBond(first, second, bond.order(), bond.aromatic()));
        }
        return result;
    }

    private List<MissingLigandHydrogen> missingHydrogens(
            CcdComponent component,
            Map<String, CcdComponentAtom> ccdAtoms,
            Map<String, Integer> deposited) {
        List<MissingLigandHydrogen> result = new ArrayList<>();
        for (CcdComponentAtom atom : ccdAtoms.values()) {
            String atomId = normalize(atom.atomId());
            if (!isHydrogen(atom.element()) || deposited.containsKey(atomId)) {
                continue;
            }
            List<CcdComponentBond> bonds = component.bonds().stream()
                    .filter(bond -> atomId.equals(normalize(bond.atomIdA()))
                            || atomId.equals(normalize(bond.atomIdB())))
                    .toList();
            if (bonds.size() != 1) {
                throw new IllegalArgumentException(
                        "CCD hydrogen " + atom.atomId() + " must have exactly one bond.");
            }
            CcdComponentBond bond = bonds.getFirst();
            String parentId = atomId.equals(normalize(bond.atomIdA()))
                    ? normalize(bond.atomIdB()) : normalize(bond.atomIdA());
            Integer parentIndex = deposited.get(parentId);
            if (parentIndex == null) {
                throw new IllegalArgumentException(
                        "CCD hydrogen " + atom.atomId() + " has a missing parent.");
            }
            result.add(new MissingLigandHydrogen(
                    atom.atomId(), parentIndex, bond.order(), atom.formalCharge(),
                    atom.aromatic(), atom.leavingAtom(),
                    atom.modelPosition(), atom.idealPosition()));
        }
        return result;
    }

    private boolean isHydrogen(String symbol) {
        return "H".equalsIgnoreCase(symbol == null ? "" : symbol.trim());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
