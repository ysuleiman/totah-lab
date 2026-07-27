package totah.lab.fpocket;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import totah.lab.pocket.AlphaSphere;

@Builder
@Getter
@ToString
public class FPocketAlphaSphere implements AlphaSphere {
    private long id;
    private double x;
    private double y;
    private double z;
    private double radius; // Alpha spheres have distinct radii from fpocket
}
