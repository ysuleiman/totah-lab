package totah.lab.athena.pocket.evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** CCD source chemistry and separately versioned derived representations. */
public record ComponentChemistryEvidence(
        String componentId,
        String ccdVersion,
        List<ComponentAtomDefinition> atoms,
        List<ComponentBondDefinition> bonds,
        List<IdealComponentCoordinate> idealCoordinates,
        EvidenceChannel<Map<String, Double>> molecularDescriptors,
        EvidenceChannel<Map<String, String>> normalizedChemistryClasses,
        EvidenceChannel<List<Double>> featureVector
) {
    public ComponentChemistryEvidence {
        componentId = requireText(componentId, "componentId");
        ccdVersion = requireText(ccdVersion, "ccdVersion");
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
        bonds = List.copyOf(Objects.requireNonNull(bonds, "bonds"));
        idealCoordinates = List.copyOf(Objects.requireNonNull(
                idealCoordinates, "idealCoordinates"));
        Objects.requireNonNull(molecularDescriptors, "molecularDescriptors");
        Objects.requireNonNull(normalizedChemistryClasses,
                "normalizedChemistryClasses");
        Objects.requireNonNull(featureVector, "featureVector");
        EvidenceChannel.requireOrigin(molecularDescriptors,
                EvidenceOrigin.DERIVED, "molecularDescriptors");
        EvidenceChannel.requireOrigin(normalizedChemistryClasses,
                EvidenceOrigin.DERIVED, "normalizedChemistryClasses");
        EvidenceChannel.requireOrigin(featureVector,
                EvidenceOrigin.DERIVED, "featureVector");
        validateAtomReferences(atoms, bonds, idealCoordinates);
    }

    private static void validateAtomReferences(
            List<ComponentAtomDefinition> atoms,
            List<ComponentBondDefinition> bonds,
            List<IdealComponentCoordinate> idealCoordinates) {
        java.util.Set<String> atomIds = atoms.stream()
                .map(ComponentAtomDefinition::atomId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (ComponentBondDefinition bond : bonds) {
            if (!atomIds.contains(bond.atomIdA()) || !atomIds.contains(bond.atomIdB())) {
                throw new IllegalArgumentException("Bond references an undefined CCD atom");
            }
        }
        for (IdealComponentCoordinate coordinate : idealCoordinates) {
            if (!atomIds.contains(coordinate.atomId())) {
                throw new IllegalArgumentException(
                        "Ideal coordinate references an undefined CCD atom");
            }
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
