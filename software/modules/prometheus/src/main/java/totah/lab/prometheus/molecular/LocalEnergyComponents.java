package totah.lab.prometheus.molecular;

public record LocalEnergyComponents(double kineticHartree,double electronNuclearHartree,double electronElectronHartree,double nuclearNuclearHartree){public LocalEnergyComponents{if(!Double.isFinite(kineticHartree)||!Double.isFinite(electronNuclearHartree)||!Double.isFinite(electronElectronHartree)||!Double.isFinite(nuclearNuclearHartree))throw new IllegalArgumentException("local energy components must be finite");}public double totalHartree(){return kineticHartree+electronNuclearHartree+electronElectronHartree+nuclearNuclearHartree;}}
