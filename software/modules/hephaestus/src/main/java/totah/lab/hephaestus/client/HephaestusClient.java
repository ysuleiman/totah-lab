package totah.lab.hephaestus.client;

import totah.lab.gaia.molecule.Protein;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.ReceptorPreparationResult;
import totah.lab.hermes.file.writer.pdbqt.PdbqtWriteResult;
import totah.lab.hephaestus.validation.ValidationReport;
import totah.lab.hermes.file.writer.pdbqt.validation.PdbqtValidationReport;

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

    ValidationReport validatePreparedProtein(
            PreparedProtein preparedProtein);

    PdbqtValidationReport validatePdbqt(
            Path input)
            throws IOException;

    PdbqtValidationReport validateFlexiblePdbqt(
            Path rigidInput,
            Path flexibleInput)
            throws IOException;
}
