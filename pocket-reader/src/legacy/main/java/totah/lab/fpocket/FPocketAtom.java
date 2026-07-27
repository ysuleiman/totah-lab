package totah.lab.fpocket;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import totah.lab.pocket.Atom;

@Builder
@Getter
@ToString
public class FPocketAtom implements Atom {
    public final String name;
    public final String element;

    public final double x;
    public final double y;
    public final double z;
}
