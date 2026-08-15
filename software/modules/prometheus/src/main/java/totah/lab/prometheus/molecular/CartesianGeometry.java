package totah.lab.prometheus.molecular;

import java.util.List;
import java.util.Objects;

/** Immutable ordered, single-unit nuclear Cartesian geometry. */
public record CartesianGeometry(List<CartesianPosition> positions,LengthUnit unit){public CartesianGeometry{positions=List.copyOf(Objects.requireNonNull(positions));Objects.requireNonNull(unit);if(positions.isEmpty())throw new IllegalArgumentException("geometry must contain at least one center");if(positions.stream().anyMatch(p->p.unit()!=unit))throw new IllegalArgumentException("all positions must use the declared geometry unit");}}
