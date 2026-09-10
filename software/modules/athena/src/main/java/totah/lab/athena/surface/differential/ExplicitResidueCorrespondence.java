package totah.lab.athena.surface.differential;

import totah.lab.gaia.structure.ResidueId;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Caller-supplied, one-to-one directional query-to-subject correspondence. */
public record ExplicitResidueCorrespondence(Map<ResidueId, ResidueId> queryToSubject) {
    public ExplicitResidueCorrespondence {
        Objects.requireNonNull(queryToSubject, "queryToSubject");
        LinkedHashMap<ResidueId, ResidueId> copy = new LinkedHashMap<>();
        for (Map.Entry<ResidueId, ResidueId> entry : queryToSubject.entrySet()) {
            ResidueId previous = copy.put(
                    Objects.requireNonNull(entry.getKey(), "query residue"),
                    Objects.requireNonNull(entry.getValue(), "subject residue"));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate query residue");
            }
        }
        if (copy.values().stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException("correspondence must be one-to-one");
        }
        queryToSubject = Collections.unmodifiableMap(new LinkedHashMap<>(copy));
    }

    public Optional<ResidueId> subjectOf(ResidueId query) {
        return Optional.ofNullable(queryToSubject.get(query));
    }
}
