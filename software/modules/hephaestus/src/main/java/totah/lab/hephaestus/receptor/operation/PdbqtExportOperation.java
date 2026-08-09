package totah.lab.hephaestus.receptor.operation;

import totah.lab.hephaestus.export.PdbqtExportReport;
import totah.lab.hephaestus.export.ReceptorPdbqtExportOptions;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.receptor.ReceptorPreparationOperation;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hermes.file.pdbqt.PdbqtWriteResult;
import totah.lab.hermes.file.pdbqt.writer.PdbqtWriter;
import totah.lab.hermes.file.pdbqt.PdbqtFlexibleReceptor;
import totah.lab.hermes.file.pdbqt.writer.PdbqtFlexibilityWriter;
import totah.lab.hephaestus.validation.PdbqtExportValidator;
import totah.lab.hephaestus.validation.ValidationException;
import totah.lab.hephaestus.validation.ValidationReport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

public final class PdbqtExportOperation
        implements ReceptorPreparationOperation {

    public static final String PDBQT_EXPORT_REPORT_ATTRIBUTE =
            "pdbqt-export-report";
    public static final String PDBQT_EXPORT_VALIDATION_REPORT_ATTRIBUTE =
            "pdbqt-export-validation-report";

    private final PdbqtWriter writer;
    private final PdbqtFlexibilityWriter flexibilitySerializer;
    private final PdbqtFlexibleReceptorAdapter flexibilityAdapter;
    private final PdbqtExportValidator exportValidator;

    public PdbqtExportOperation() {
        this(new PdbqtWriter(), new PdbqtFlexibilityWriter(),
                new PdbqtFlexibleReceptorAdapter(), new PdbqtExportValidator());
    }

    public PdbqtExportOperation(PdbqtWriter writer) {
        this(writer, new PdbqtFlexibilityWriter(),
                new PdbqtFlexibleReceptorAdapter(), new PdbqtExportValidator());
    }

    PdbqtExportOperation(PdbqtWriter writer,
            PdbqtFlexibilityWriter flexibilitySerializer,
            PdbqtFlexibleReceptorAdapter flexibilityAdapter,
            PdbqtExportValidator exportValidator) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.flexibilitySerializer = Objects.requireNonNull(
                flexibilitySerializer, "flexibilitySerializer");
        this.flexibilityAdapter = Objects.requireNonNull(
                flexibilityAdapter, "flexibilityAdapter");
        this.exportValidator = Objects.requireNonNull(exportValidator, "exportValidator");
    }

    @Override
    public OperationResult<PreparedProtein> apply(
            PreparedProtein preparedProtein,
            ReceptorPreparationOptions options) {
        Objects.requireNonNull(preparedProtein, "preparedProtein");
        Objects.requireNonNull(options, "options");

        ReceptorPdbqtExportOptions export = options.pdbqtExportOptions();
        if (export == null) {
            return OperationResult.success(preparedProtein);
        }
        ValidationReport validationReport = exportValidator.validate(preparedProtein);
        if (validationReport.hasErrors()) throw new ValidationException(validationReport);
        PreparedProtein validatedProtein = preparedProtein.withAttribute(
                PDBQT_EXPORT_VALIDATION_REPORT_ATTRIBUTE, validationReport);

        try {
            if (!validatedProtein.flexibility().isEmpty()) {
                PdbqtFlexibleReceptor input = flexibilityAdapter.adapt(
                        validatedProtein, validatedProtein.flexibility());
                var result = flexibilitySerializer.write(
                        input, export.outputPath(), export.flexibleOutputPath());
                PdbqtExportReport report = new PdbqtExportReport(
                        validatedProtein.protein().structure().getChainCount(),
                        validatedProtein.protein().structure().getResidueCount(),
                        validatedProtein.protein().structure().getAtomCount(),
                        result.rigidOutput(), result.flexibleOutput(),
                        result.rigidAtomCount(), result.flexibleAtomCount(),
                        result.torsionCount());
                return OperationResult.success(validatedProtein.withAttribute(
                        PDBQT_EXPORT_REPORT_ATTRIBUTE, report));
            }
            PdbqtWriteResult result = writer.write(
                    validatedProtein.protein().structure(),
                    export.outputPath(),
                    export.writeOptions());
            PdbqtExportReport report = new PdbqtExportReport(
                    validatedProtein.protein().structure().getChainCount(),
                    validatedProtein.protein().structure().getResidueCount(),
                    result.rigidAtomCount(),
                    result.rigidOutput(),
                    null,
                    result.rigidAtomCount(),
                    0,
                    0);
            return OperationResult.success(
                    validatedProtein.withAttribute(
                            PDBQT_EXPORT_REPORT_ATTRIBUTE, report));
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to export receptor PDBQT to "
                            + export.outputPath(),
                    exception);
        }
    }

}
