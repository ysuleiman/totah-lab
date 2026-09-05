package totah.lab.athena.interaction;

import totah.lab.gaia.structure.ResidueId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable residue x interaction-type matrix view of a set of
 * interactions. Rows are the participating residues in order of first
 * appearance (structure traversal order for profiler output); columns
 * are the {@link InteractionType} values in enum order. Each cell holds
 * the contact count and the best (minimum) distance. {@link #toCsv()}
 * renders the count matrix as CSV text for campaign export; no file I/O
 * is performed.
 */
public final class ContactMatrix {

    /**
     * One matrix cell.
     *
     * @param count number of interactions of the column's type on the
     *              row's residue
     * @param minDistanceAngstroms smallest distance among those
     *                             interactions; {@code null} when
     *                             {@code count == 0}
     */
    public record Cell(int count, Double minDistanceAngstroms) {

        public Cell {
            if (count < 0) {
                throw new IllegalArgumentException("count must be >= 0");
            }
            if (count == 0 && minDistanceAngstroms != null) {
                throw new IllegalArgumentException(
                        "an empty cell cannot carry a distance");
            }
            if (count > 0 && minDistanceAngstroms == null) {
                throw new IllegalArgumentException(
                        "a non-empty cell requires a distance");
            }
        }
    }

    private static final Cell EMPTY = new Cell(0, null);

    private final List<ResidueId> rows;
    private final Map<ResidueId, Map<InteractionType, Cell>> cells;

    private ContactMatrix(
            List<ResidueId> rows,
            Map<ResidueId, Map<InteractionType, Cell>> cells) {

        this.rows = rows;
        this.cells = cells;
    }

    /** Builds the matrix over the profile's refined interactions. */
    public static ContactMatrix of(InteractionProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return of(profile.interactions());
    }

    /** Builds the matrix over the given interactions. */
    public static ContactMatrix of(List<Interaction> interactions) {
        Objects.requireNonNull(interactions, "interactions");
        Map<ResidueId, Map<InteractionType, Cell>> cells =
                new LinkedHashMap<>();
        for (Interaction interaction : interactions) {
            Map<InteractionType, Cell> row = cells.computeIfAbsent(
                    interaction.residue(), residue -> new LinkedHashMap<>());
            Cell current = row.get(interaction.type());
            if (current == null) {
                row.put(interaction.type(), new Cell(1,
                        interaction.distanceAngstroms()));
            } else {
                row.put(interaction.type(), new Cell(
                        current.count() + 1,
                        Math.min(current.minDistanceAngstroms(),
                                interaction.distanceAngstroms())));
            }
        }
        Map<ResidueId, Map<InteractionType, Cell>> immutable =
                new LinkedHashMap<>();
        cells.forEach((residue, row) ->
                immutable.put(residue, Map.copyOf(row)));
        return new ContactMatrix(
                List.copyOf(cells.keySet()), Map.copyOf(immutable));
    }

    /** Row residues in order of first appearance. */
    public List<ResidueId> rows() {
        return rows;
    }

    /** Column types in {@link InteractionType} enum order. */
    public List<InteractionType> columns() {
        return List.of(InteractionType.values());
    }

    /** Returns the cell; an empty cell (count 0) when no contact exists. */
    public Cell cell(ResidueId residue, InteractionType type) {
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(type, "type");
        return cells.getOrDefault(residue, Map.of())
                .getOrDefault(type, EMPTY);
    }

    /**
     * Renders the count matrix as CSV: a header row of interaction type
     * names, then one row per residue keyed {@code chainId:number}
     * (with insertion code when present). Distances are available per
     * cell via {@link #cell}.
     */
    public String toCsv() {
        StringBuilder csv = new StringBuilder("residue");
        for (InteractionType type : columns()) {
            csv.append(',').append(type.name());
        }
        for (ResidueId residue : rows) {
            csv.append('\n').append(label(residue));
            for (InteractionType type : columns()) {
                csv.append(',').append(cell(residue, type).count());
            }
        }
        return csv.toString();
    }

    private static String label(ResidueId residue) {
        return residue.chainId() + ":" + residue.residueNumber()
                + (residue.insertionCode() == null
                        ? "" : residue.insertionCode().toString());
    }
}
