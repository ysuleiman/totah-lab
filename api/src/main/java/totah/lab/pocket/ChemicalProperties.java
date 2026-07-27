package totah.lab.pocket;

public interface ChemicalProperties {
    public double getMeanLocalHydrophobicDensity();

    public double getHydrophobicityScore();

    public int getPolarityScore();

    public int getChargeScore();

    public double getProportionOfPolarAtoms();

    public double getFlexibility();
}