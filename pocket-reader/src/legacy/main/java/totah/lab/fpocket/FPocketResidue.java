package totah.lab.fpocket;

import lombok.*;
import totah.lab.pocket.Atom;
import totah.lab.pocket.Residue;

import java.util.List;

@Builder
@Setter
@Getter
@ToString
@EqualsAndHashCode(
        onlyExplicitlyIncluded = true
)
public class FPocketResidue implements Residue {
    @EqualsAndHashCode.Include
    private final String chainId;
    @EqualsAndHashCode.Include
    private final String name;
    @EqualsAndHashCode.Include
    private final int number;
    private final String position;

    @Singular("atom")
    private final List<Atom> atoms;

}
