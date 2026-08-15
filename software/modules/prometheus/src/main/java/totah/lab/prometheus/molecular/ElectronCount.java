package totah.lab.prometheus.molecular;

public record ElectronCount(int value){public ElectronCount{if(value<1)throw new IllegalArgumentException("electron count must be positive");}}
