package totah.lab.daedalus.evidence;

import totah.lab.athena.pocket.evidence.ComponentAtomDefinition;
import totah.lab.athena.pocket.evidence.ComponentBondDefinition;
import totah.lab.athena.pocket.evidence.ComponentChemistryEvidence;
import totah.lab.athena.pocket.evidence.EvidenceChannel;
import totah.lab.athena.pocket.evidence.IdealComponentCoordinate;
import totah.lab.hermes.ccd.CcdComponent;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Maps Hermes CCD source truth into Athena interpretation-ready evidence. */
public final class CcdComponentEvidenceMapper {

    public ComponentChemistryEvidence map(
            CcdComponent component,
            String ccdVersion,
            EvidenceChannel<Map<String, Double>> molecularDescriptors,
            EvidenceChannel<Map<String, String>> normalizedChemistryClasses,
            EvidenceChannel<List<Double>> featureVector) {
        Objects.requireNonNull(component, "component");
        List<ComponentAtomDefinition> atoms = component.atoms().stream()
                .map(atom -> new ComponentAtomDefinition(
                        atom.atomId(), atom.element(), atom.formalCharge(), atom.aromatic()))
                .toList();
        List<ComponentBondDefinition> bonds = component.bonds().stream()
                .map(bond -> new ComponentBondDefinition(
                        bond.atomIdA(), bond.atomIdB(), bond.order(), bond.aromatic()))
                .toList();
        List<IdealComponentCoordinate> idealCoordinates = component.atoms().stream()
                .filter(atom -> atom.idealPosition() != null)
                .map(atom -> new IdealComponentCoordinate(atom.atomId(), atom.idealPosition()))
                .toList();
        return new ComponentChemistryEvidence(
                component.componentId(), ccdVersion, atoms, bonds, idealCoordinates,
                molecularDescriptors, normalizedChemistryClasses, featureVector);
    }
}
