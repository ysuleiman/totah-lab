package totah.lab.fpocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import totah.lab.pocket.Sasa;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FPocketSasa implements Sasa {
    public double total;
    public double polar;
    public double apolar;
}
