package totah.lab.athena.pocket.component;

import java.util.List;

/** Complete grouping result; incidental cavities remain auditable. */
public record ExperimentalBindingSiteGrouping(
        List<ExperimentalBindingSiteGroup> sites,
        List<PocketPairComparison> pairComparisons,
        List<Long> incidentalPocketIds) {
    public ExperimentalBindingSiteGrouping {
        sites = List.copyOf(sites);
        pairComparisons = List.copyOf(pairComparisons);
        incidentalPocketIds = List.copyOf(incidentalPocketIds);
    }
}
