package totah.lab.fpocket;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import totah.lab.pocket.AlphaSphereGeometry;

@Getter
@Setter
@ToString
public class FPocketGeometry implements AlphaSphereGeometry {
    private double meanAlphaSphereRadius;
    private double meanAlphaSphereSolventAccess;
    private double alphaSphereDensity;
    private double centOfMassAlphaSphereMaxDist;
    private double apolarAlphaSphereProportion;
}
