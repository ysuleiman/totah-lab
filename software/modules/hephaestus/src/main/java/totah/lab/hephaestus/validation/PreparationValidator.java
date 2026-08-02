package totah.lab.hephaestus.validation;

@FunctionalInterface
public interface PreparationValidator<T> { ValidationReport validate(T value); }
