package totah.lab.prometheus.variational;

/** Replayable bounded source; implementations must not retain a complete unbounded population. */
@FunctionalInterface public interface GeneralMolecularSampleSource { void forEach(SampleConsumer consumer); @FunctionalInterface interface SampleConsumer{void accept(double weight,QuantumCoordinates coordinates);} }
