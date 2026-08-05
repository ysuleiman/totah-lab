package totah.lab.hephaestus.client;


import org.biojava.nbio.structure.chem.ChemCompProvider;
import org.biojava.nbio.structure.chem.ReducedChemCompProvider;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.molecule.Protein;
import totah.lab.hephaestus.ligand.DefaultLigandPreparer;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationRequest;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.hephaestus.ligand.operation.LigandPdbqtExportOperation;
import totah.lab.hephaestus.model.PreparationIssue;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.model.Severity;
import totah.lab.hephaestus.receptor.ReceptorPreparer;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.ReceptorPreparationRequest;
import totah.lab.hephaestus.receptor.ReceptorPreparationResult;
import totah.lab.hephaestus.validation.PreparedLigandValidator;
import totah.lab.hephaestus.validation.ValidationIssue;
import totah.lab.hephaestus.validation.ValidationSeverity;
import totah.lab.hermes.file.reader.SdfLigand;
import totah.lab.hermes.file.reader.SdfLigandReader;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefaultHephaestusClient
        implements HephaestusClient {

    private final StructureReader structureReader;
    private final ProteinFactory proteinFactory;
    private final ReceptorPreparer receptorPreparer;
    private final PdbqtWriter pdbqtWriter;
    private final PdbqtValidator pdbqtValidator;
    private final SdfLigandReader sdfLigandReader;
    private final ChemCompProvider ligandChemCompProvider;
    private final LigandPdbqtExportOperation ligandExport;
    private final PreparedProteinValidator preparedProteinValidator =
            new PreparedProteinValidator();
    private final PreparedLigandValidator preparedLigandValidator =
            new PreparedLigandValidator();

    public DefaultHephaestusClient(
            StructureReader structureReader,
            ProteinFactory proteinFactory,
            ReceptorPreparer receptorPreparer,
            PdbqtWriter pdbqtWriter,
            PdbqtValidator pdbqtValidator) {

        this(structureReader, proteinFactory, receptorPreparer, pdbqtWriter,
                pdbqtValidator, new SdfLigandReader(),
                new ReducedChemCompProvider(), new LigandPdbqtExportOperation());
    }

    public DefaultHephaestusClient(
            StructureReader structureReader,
            ProteinFactory proteinFactory,
            ReceptorPreparer receptorPreparer,
            PdbqtWriter pdbqtWriter,
            PdbqtValidator pdbqtValidator,
            SdfLigandReader sdfLigandReader,
            ChemCompProvider ligandChemCompProvider,
            LigandPdbqtExportOperation ligandExport) {

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

        this.sdfLigandReader = Objects.requireNonNull(
                sdfLigandReader,
                "sdfLigandReader");

        this.ligandChemCompProvider = Objects.requireNonNull(
                ligandChemCompProvider,
                "ligandChemCompProvider");

        this.ligandExport = Objects.requireNonNull(
                ligandExport,
                "ligandExport");
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

    /**
     * CCD-driven ligand preparation: the ligand's component code (or
     * residue name) is resolved against the chemical component dictionary.
     */
    @Override
    public LigandPreparationResult prepareLigand(
            Ligand ligand,
            LigandPreparationOptions options) {
        Objects.requireNonNull(ligand, "ligand");
        Objects.requireNonNull(options, "options");
        LigandPreparationResult result = DefaultLigandPreparer
                .standard(ligandChemCompProvider)
                .prepare(new LigandPreparationRequest(ligand, options));
        return withValidation(result);
    }

    /**
     * SDF-driven ligand preparation: topology comes from the parsed bond
     * table; the SDF must carry explicit hydrogens and 3D coordinates.
     */
    @Override
    public LigandPreparationResult prepareLigand(
            Path sdfInput,
            LigandPreparationOptions options) throws IOException {
        Objects.requireNonNull(sdfInput, "sdfInput");
        Objects.requireNonNull(options, "options");
        SdfLigand model = sdfLigandReader.readModel(sdfInput);
        LigandPreparationResult result = DefaultLigandPreparer.sdf(model)
                .prepare(new LigandPreparationRequest(model.ligand(), options));
        return withValidation(result);
    }

    @Override
    public Path prepareAndWriteLigand(
            Path sdfInput,
            Path output,
            LigandPreparationOptions options) throws IOException {
        return writePreparedLigand(
                prepareLigand(sdfInput, options).preparedLigand(), output);
    }

    @Override
    public Path writePreparedLigand(
            PreparedLigand preparedLigand,
            Path output) throws IOException {
        Objects.requireNonNull(preparedLigand, "preparedLigand");
        Objects.requireNonNull(output, "output");
        ValidationReport validation = preparedLigandValidator.validate(
                preparedLigand);
        if (validation.hasErrors()) {
            throw new ValidationException(validation);
        }
        Path written = ligandExport.export(preparedLigand, output);
        PdbqtValidationReport pdbqtValidation =
                pdbqtValidator.validateLigandPdbqt(written);
        if (pdbqtValidation.hasErrors()) {
            throw new IllegalStateException(
                    "Written ligand PDBQT failed validation: "
                            + pdbqtValidation.issues());
        }
        return written;
    }

    @Override
    public ValidationReport validatePreparedLigand(
            PreparedLigand preparedLigand) {
        return preparedLigandValidator.validate(preparedLigand);
    }

    /** Surfaces post-preparation validation findings as report issues. */
    private LigandPreparationResult withValidation(
            LigandPreparationResult result) {
        ValidationReport validation = preparedLigandValidator.validate(
                result.preparedLigand());
        if (validation.issues().isEmpty()) {
            return result;
        }
        List<PreparationIssue> issues = new ArrayList<>(result.issues());
        for (ValidationIssue issue : validation.issues()) {
            issues.add(new PreparationIssue(
                    issue.severity() == ValidationSeverity.ERROR
                            ? Severity.ERROR
                            : issue.severity() == ValidationSeverity.WARNING
                                    ? Severity.WARNING
                                    : Severity.INFO,
                    "VALIDATION_" + issue.code().name(),
                    issue.message()));
        }
        return new LigandPreparationResult(result.preparedLigand(), issues);
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
