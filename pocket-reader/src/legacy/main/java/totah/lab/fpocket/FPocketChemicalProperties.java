package totah.lab.fpocket;

import lombok.*;
import totah.lab.pocket.ChemicalProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@ToString
public class FPocketChemicalProperties implements ChemicalProperties {
    public double meanLocalHydrophobicDensity;
    public double hydrophobicityScore;
    public int polarityScore;
    public int chargeScore;
    public double proportionOfPolarAtoms;
    public double flexibility;
}
