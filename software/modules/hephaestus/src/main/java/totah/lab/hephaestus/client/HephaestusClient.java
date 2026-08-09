package totah.lab.hephaestus.client;

import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.molecule.Protein;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.ReceptorPreparationResult;
import totah.lab.hermes.file.pdbqt.PdbqtWriteResult;
import totah.lab.hephaestus.validation.ValidationReport;
import totah.lab.hermes.file.pdbqt.validation.PdbqtValidationReport;

import java.io.IOException;
import java.nio.file.Path;

public interface HephaestusClient {

    ReceptorPreparationResult prepareReceptor(
            Protein protein,
            ReceptorPreparationOptions options);

    ReceptorPreparationResult prepareReceptor(
            Path input,
            ReceptorPreparationOptions options)
            throws IOException;

    PdbqtWriteResult prepareAndWriteReceptor(
            Path input,
            Path output,
            ReceptorPreparationOptions options)
            throws IOException;

    PdbqtWriteResult writePreparedReceptor(
            PreparedProtein preparedProtein,
            Path output)
            throws IOException;

    LigandPreparationResult prepareLigand(
            Ligand ligand,
            LigandPreparationOptions options);

    LigandPreparationResult prepareLigand(
            Path sdfInput,
            LigandPreparationOptions options)
            throws IOException;

    Path prepareAndWriteLigand(
            Path sdfInput,
            Path output,
            LigandPreparationOptions options)
            throws IOException;

    Path writePreparedLigand(
            PreparedLigand preparedLigand,
            Path output)
            throws IOException;

    ValidationReport validatePreparedProtein(
            PreparedProtein preparedProtein);

    ValidationReport validatePreparedLigand(
            PreparedLigand preparedLigand);

    PdbqtValidationReport validatePdbqt(
            Path input)
            throws IOException;

    PdbqtValidationReport validateLigandPdbqt(
            Path input)
            throws IOException;

    PdbqtValidationReport validateFlexiblePdbqt(
            Path rigidInput,
            Path flexibleInput)
            throws IOException;
}
