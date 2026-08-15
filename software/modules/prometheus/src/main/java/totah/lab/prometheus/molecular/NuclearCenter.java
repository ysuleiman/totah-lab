package totah.lab.prometheus.molecular;

import java.util.Objects;

public record NuclearCenter(int orderedIndex,String element,NuclearCharge charge,CartesianPosition position){public NuclearCenter{if(orderedIndex<0)throw new IllegalArgumentException("negative nuclear index");if(Objects.requireNonNull(element).isBlank())throw new IllegalArgumentException("blank element");Objects.requireNonNull(charge);Objects.requireNonNull(position);}}
