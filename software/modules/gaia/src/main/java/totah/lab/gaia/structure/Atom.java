package totah.lab.gaia.structure;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

@Getter
@ToString
@Builder(toBuilder = true) // Enabled copy-on-write rebuilding safely across stages
public final class Atom {
    private final int pdbSerial;
    private final String name;
    private final String amberType;
    //@Builder.Default
    private final String autoDockType;// = "C";  // NEW: AutoDock4 atom type

    private final Point3D position;
    private final double charge;
    private final double occupancy;
    private final double bFactor;
    private final Element element;

    private final AlternateLocationProvenance alternateLocationProvenance;

    public Atom(
            int pdbSerial,
            String name,
            String amberType,
            String autoDockType,
            Point3D position,
            double charge,
            double occupancy,
            double bFactor,
            Element element,
            AlternateLocationProvenance alternateLocationProvenance) {
        this.pdbSerial = pdbSerial;
        this.name = Objects.requireNonNull(name, "name").trim();
        if (this.name.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.amberType = amberType;
        this.autoDockType = autoDockType;
        this.position = Objects.requireNonNull(position, "position");
        this.charge = charge;
        this.occupancy = occupancy;
        this.bFactor = bFactor;
        this.element = element;
        this.alternateLocationProvenance = alternateLocationProvenance == null
                ? AlternateLocationProvenance.NONE
                : alternateLocationProvenance;
    }

    public boolean isHydrogen() {
        return element == Element.H;
    }

    /**
     * Returns {@code true} when this atom has a known element that is
     * not hydrogen. Atoms with an unknown ({@code null}) element are
     * not considered heavy; this matches
     * {@link Residue#getHeavyAtomCount()}.
     */
    public boolean isHeavyAtom() {
        return element != null && !isHydrogen();
    }
}
