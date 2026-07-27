package totah.lab.protein;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Element {

    private final String symbol;
    private final int atomicNumber;
    private final double atomicMass;
    private final double vdwRadius;
    private final double covalentRadius;

}
