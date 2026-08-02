package totah.lab.hephaestus.amber;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class BondTemplate {
    private final String atom1;
    private final String atom2;
}