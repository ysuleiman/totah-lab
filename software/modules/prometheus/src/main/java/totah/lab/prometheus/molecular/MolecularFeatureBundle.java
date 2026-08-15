package totah.lab.prometheus.molecular;

import java.util.List;

/** Immutable reusable geometry features in canonical electron/nuclear order. */
public record MolecularFeatureBundle(List<List<Double>> electronNuclearDistancesBohr,List<Double> electronElectronDistancesBohr,List<Double> nuclearNuclearDistancesBohr){public MolecularFeatureBundle{electronNuclearDistancesBohr=electronNuclearDistancesBohr.stream().map(List::copyOf).toList();electronElectronDistancesBohr=List.copyOf(electronElectronDistancesBohr);nuclearNuclearDistancesBohr=List.copyOf(nuclearNuclearDistancesBohr);}}
