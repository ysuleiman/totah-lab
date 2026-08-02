package totah.lab.hephaestus.amber;

/**
 * Minimal interface for looking up residue templates by name.
 * Decouples HydrogenOptimizer from the concrete AmberResidueTemplateLibrary singleton.
 */
public interface ResidueTemplateProvider {
    ResidueTemplate getTemplate(String residueName);
}
