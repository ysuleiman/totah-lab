package totah.lab.prometheus.potential.delta.model;

import java.util.Objects;

public record DeltaModelIdentity(String modelLevel,String basisChecksum,String chemicalTypesChecksum,String trainingManifestChecksum,String softwareIdentity){public DeltaModelIdentity{Objects.requireNonNull(modelLevel);Objects.requireNonNull(basisChecksum);Objects.requireNonNull(chemicalTypesChecksum);Objects.requireNonNull(trainingManifestChecksum);Objects.requireNonNull(softwareIdentity);}}
