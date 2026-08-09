package totah.lab.athena.ligand.pose;

import java.util.List;
import java.util.Objects;

/**
 * Pocket-occupancy report of one ligand across a docking run: one
 * {@link PocketOccupancyEntry} per pocket that at least one predicted
 * pose was assigned to, sorted by pose count descending then pocket id,
 * plus the count of poses that could not be assigned to any pocket.
 *
 * <p>This is a <b>pose frequency</b> report: it counts how often
 * predicted poses occupy each pocket. It is not a thermodynamic
 * probability and must not be read as binding propensity.
 */
public record LigandPocketOccupancy(
        List<PocketOccupancyEntry> entries,
        int notAssignedCount
) {

    public LigandPocketOccupancy {
        entries = List.copyOf(
                Objects.requireNonNull(entries, "entries")
        );

        if (notAssignedCount < 0) {
            throw new IllegalArgumentException(
                    "notAssignedCount must be non-negative"
            );
        }
    }
}
