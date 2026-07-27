package totah.lab.pocket;

import java.util.List;

public interface Pocket {
    public long getId();
    public String getName();
    public double getDruggabilityScore();
    public double getScore();
    public double getVolume();
    public double getVolumeScore();
    public AlphaSphereGeometry getGeometry();
    public Sasa getSasa();
    public ChemicalProperties getChemistry();

    public List<Residue> getResidues();
    public List<AlphaSphere> getAlphaSpheres();
    public void add(Residue residue);
    public void addResidues(List<Residue> residues);
    public void addAlphaSpheres(List<AlphaSphere> spheres);
}
