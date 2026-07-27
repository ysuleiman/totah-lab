package totah.lab.topology;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class AtomTemplate {

    private final String name;
    private final String amberType;
    private final double charge;
}
