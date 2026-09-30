package totah.lab.aether.model;

import java.util.Objects;
import totah.lab.gaia.geometry.Point3D;

/** Basis template only: this center contributes no nuclear charge or electrons. */
public record GhostCenter(int basisAtomicNumber, Point3D centerBohr) {
    public GhostCenter {
        Objects.requireNonNull(centerBohr);
        if(basisAtomicNumber<1)throw new IllegalArgumentException("Ghost basis requires an element template");
        if(!Double.isFinite(centerBohr.x())||!Double.isFinite(centerBohr.y())||!Double.isFinite(centerBohr.z()))
            throw new IllegalArgumentException("Nonfinite ghost center");
    }
}
