package totah.lab.hephaestus.receptor;

@FunctionalInterface
public interface ReceptorPreparer {
    ReceptorPreparationResult prepare(
            ReceptorPreparationRequest request);
}
