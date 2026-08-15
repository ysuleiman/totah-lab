package totah.lab.prometheus.molecular;

import java.util.Objects;

public record CartesianPosition(double x,double y,double z,LengthUnit unit){public CartesianPosition{Objects.requireNonNull(unit);if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("coordinates must be finite");}public CartesianPosition inBohr(){return unit==LengthUnit.BOHR?this:new CartesianPosition(unit.toBohr(x),unit.toBohr(y),unit.toBohr(z),LengthUnit.BOHR);}}
