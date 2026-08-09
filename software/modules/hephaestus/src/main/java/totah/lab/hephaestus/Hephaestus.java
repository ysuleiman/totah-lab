package totah.lab.hephaestus;

import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.molecule.Protein;
import totah.lab.hephaestus.client.HephaestusClients;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.ReceptorPreparationResult;

import java.util.Objects;

/**
 * Convenient in-memory entry point for molecular preparation.
 *
 * <p>The facade delegates to the canonical Hephaestus preparation client. It
 * performs no chemistry itself and does not read or write files.</p>
 */
public final class Hephaestus {

    private Hephaestus() {
    }

    /** Prepares a ligand using the documented default options. */
    public static PreparedLigand prepareLigand(Ligand ligand) {
        return prepareLigand(ligand, LigandPreparationOptions.defaults());
    }

    /** Prepares a ligand using explicit options. */
    public static PreparedLigand prepareLigand(
            Ligand ligand,
            LigandPreparationOptions options) {

        LigandPreparationResult result =
                prepareLigandDetailed(ligand, options);
        requireSuccessful("Ligand", result.successful(), result.issues());
        return result.preparedLigand();
    }

    /**
     * Prepares a ligand and retains all preparation warnings and errors.
     */
    public static LigandPreparationResult prepareLigandDetailed(
            Ligand ligand,
            LigandPreparationOptions options) {

        Objects.requireNonNull(ligand, "ligand");
        Objects.requireNonNull(options, "options");
        return HephaestusClients.createDefault()
                .prepareLigand(ligand, options);
    }

    /** Prepares a receptor using the documented default options. */
    public static PreparedProtein prepareReceptor(Protein protein) {
        return prepareReceptor(
                protein,
                ReceptorPreparationOptions.defaults());
    }

    /** Prepares a receptor using explicit options. */
    public static PreparedProtein prepareReceptor(
            Protein protein,
            ReceptorPreparationOptions options) {

        ReceptorPreparationResult result =
                prepareReceptorDetailed(protein, options);
        requireSuccessful("Receptor", result.successful(), result.issues());
        return result.preparedProtein();
    }

    /**
     * Prepares a receptor and retains all preparation warnings and errors.
     */
    public static ReceptorPreparationResult prepareReceptorDetailed(
            Protein protein,
            ReceptorPreparationOptions options) {

        Objects.requireNonNull(protein, "protein");
        Objects.requireNonNull(options, "options");
        return HephaestusClients.createDefault()
                .prepareReceptor(protein, options);
    }

    private static void requireSuccessful(
            String subject,
            boolean successful,
            Object issues) {

        if (!successful) {
            throw new IllegalStateException(
                    subject + " preparation failed: " + issues);
        }
    }
}
