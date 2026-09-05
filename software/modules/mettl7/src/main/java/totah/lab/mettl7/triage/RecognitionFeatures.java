package totah.lab.mettl7.triage;

import java.util.Set;

public record RecognitionFeatures(
        Set<String> mettl7aContacts,
        Set<String> mettl7bContacts,
        boolean broadHydrophobicCanonicalRoute,
        boolean rearRoute,
        boolean context195To203,
        boolean context228To237) {
    public RecognitionFeatures {
        mettl7aContacts = Set.copyOf(mettl7aContacts == null ? Set.of() : mettl7aContacts);
        mettl7bContacts = Set.copyOf(mettl7bContacts == null ? Set.of() : mettl7bContacts);
    }
}
