package totah.lab.hephaestus.client;


import totah.lab.gaia.molecule.Protein;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparer;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.ReceptorPreparationRequest;
import totah.lab.hephaestus.receptor.ReceptorPreparationResult;
import totah.lab.hermes.file.reader.StructureReader;
import totah.lab.hephaestus.factory.ProteinFactory;
import totah.lab.hephaestus.validation.PreparedProteinValidator;
import totah.lab.hephaestus.validation.ValidationException;
import totah.lab.hephaestus.validation.ValidationReport;
import totah.lab.hermes.file.writer.pdbqt.PdbqtWriter;
import totah.lab.hermes.file.writer.pdbqt.PdbqtWriteOptions;
import totah.lab.hermes.file.writer.pdbqt.PdbqtWriteResult;
import totah.lab.hermes.file.writer.pdbqt.validation.PdbqtValidationReport;
import totah.lab.hermes.file.writer.pdbqt.validation.PdbqtValidator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class DefaultHephaestusClient
        implements HephaestusClient {

    private final StructureReader structureReader;
    private final ProteinFactory proteinFactory;
    private final ReceptorPreparer receptorPreparer;
    private final PdbqtWriter pdbqtWriter;
    private final PdbqtValidator pdbqtValidator;
    private final PreparedProteinValidator preparedProteinValidator =
            new PreparedProteinValidator();

    public DefaultHephaestusClient(
            StructureReader structureReader,
            ProteinFactory proteinFactory,
            ReceptorPreparer receptorPreparer,
            PdbqtWriter pdbqtWriter,
            PdbqtValidator pdbqtValidator) {

        this.structureReader = Objects.requireNonNull(
                structureReader,
                "structureReader");

        this.proteinFactory = Objects.requireNonNull(
                proteinFactory,
                "proteinFactory");

        this.receptorPreparer = Objects.requireNonNull(
                receptorPreparer,
                "receptorPreparer");

        this.pdbqtWriter = Objects.requireNonNull(
                pdbqtWriter,
                "pdbqtWriter");

        this.pdbqtValidator = Objects.requireNonNull(
                pdbqtValidator,
                "pdbqtValidator");
    }

    @Override
    public ReceptorPreparationResult prepareReceptor(
            Protein protein,
            ReceptorPreparationOptions options) {
        Objects.requireNonNull(protein, "protein");
        Objects.requireNonNull(options, "options");
        return receptorPreparer.prepare(
                new ReceptorPreparationRequest(protein, options));
    }

    @Override
    public ReceptorPreparationResult prepareReceptor(
            Path input,
            ReceptorPreparationOptions options) throws IOException {

        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(options, "options");

        var structure = structureReader.read(input);

        Protein protein = proteinFactory.create(
                input.getFileName().toString(),
                structure);

        return prepareReceptor(protein, options);
    }

    @Override
    public PdbqtWriteResult prepareAndWriteReceptor(
            Path input,
            Path output,
            ReceptorPreparationOptions options) throws IOException {
        ReceptorPreparationResult prepared = prepareReceptor(input, options);
        return writePreparedReceptor(prepared.preparedProtein(), output);
    }

    @Override
    public PdbqtWriteResult writePreparedReceptor(
            PreparedProtein preparedProtein,
            Path output) throws IOException {
        Objects.requireNonNull(preparedProtein, "preparedProtein");
        Objects.requireNonNull(output, "output");
        ValidationReport validation = preparedProteinValidator.validate(
                preparedProtein);
        if (validation.hasErrors()) {
            throw new ValidationException(validation);
        }
        var result = pdbqtWriter.write(
                preparedProtein.protein().structure(),
                output,
                PdbqtWriteOptions.defaults());
        return new PdbqtWriteResult(
                result.rigidOutput(), null, result.rigidAtomCount(), 0, 0, 0);
    }

    @Override
    public ValidationReport validatePreparedProtein(
            PreparedProtein preparedProtein) {
        return preparedProteinValidator.validate(preparedProtein);
    }

    @Override
    public PdbqtValidationReport validatePdbqt(Path input)
            throws IOException {
        return pdbqtValidator.validatePdbqt(input);
    }

    @Override
    public PdbqtValidationReport validateFlexiblePdbqt(
            Path rigidInput,
            Path flexibleInput) throws IOException {
        return pdbqtValidator.validateFlexiblePdbqt(
                rigidInput, flexibleInput);
    }
}
