package totah.lab.protein;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@Builder(toBuilder = true) // Enabled copy-on-write rebuilding safely across stages
public class Atom {
    private  int pdbSerial;
    private final String name;
    private final String amberType;
    //@Builder.Default
    private final String autoDockType;// = "C";  // NEW: AutoDock4 atom type

    private final Point3D position;
    private final double charge;
    private final double occupancy;
    private final double bFactor;
    private final Element element;
}
