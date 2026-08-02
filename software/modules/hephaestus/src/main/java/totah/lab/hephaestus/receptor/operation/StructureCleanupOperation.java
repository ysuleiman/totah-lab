package totah.lab.hephaestus.receptor.operation;

import lombok.extern.slf4j.Slf4j;
import totah.lab.gaia.classification.ResidueClassificationEvidence;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.factory.ProteinFactory;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.receptor.ReceptorPreparationOperation;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.cleanup.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
public final class StructureCleanupOperation
        implements ReceptorPreparationOperation {

    public static final String CLEANUP_RESULT_ATTRIBUTE =
            "structure-cleanup-result";

    public static final String CLEANUP_REPORT_ATTRIBUTE =
            "structure-cleanup-report";

    public static final String EXTRACTED_LIGANDS_ATTRIBUTE =
            "extracted-ligands";

    private static final Set<String> DEFAULT_SPECIAL_RESIDUES =
            Set.of("MSE", "TYS");

    private final ResidueClassifier residueClassifier;

    public StructureCleanupOperation() {
        this(new ResidueClassifier());
    }

    public StructureCleanupOperation(
            ResidueClassifier residueClassifier) {

        this.residueClassifier = Objects.requireNonNull(
                residueClassifier,
                "residueClassifier");
    }

    @Override
    public OperationResult<PreparedProtein> apply(
            PreparedProtein preparedProtein,
            ReceptorPreparationOptions options) {

        Objects.requireNonNull(
                preparedProtein,
                "preparedProtein");

        Objects.requireNonNull(options, "options");

        Protein protein = preparedProtein.protein();
        Structure structure = protein.structure();

        if (structure.getResidueCount() == 0) {
            throw new IllegalStateException(
                    "Protein structure contains no residues.");
        }

        Set<String> allowedSpecialResidues =
                new HashSet<>(DEFAULT_SPECIAL_RESIDUES);

        allowedSpecialResidues.addAll(
                options.allowedSpecialResidues());

        List<Chain> cleanedChains = new ArrayList<>();
        List<Residue> extractedLigands = new ArrayList<>();

        List<String> removedWaters = new ArrayList<>();
        List<String> removedMetals = new ArrayList<>();
        List<String> keptSpecial = new ArrayList<>();

        List<ClassifiedResidue> classifiedReceptor =
                new ArrayList<>();

        List<ClassifiedResidue> classifiedLigands =
                new ArrayList<>();

        List<ClassifiedResidue> classifiedWaters =
                new ArrayList<>();

        List<ClassifiedResidue> classifiedMetals =
                new ArrayList<>();

        List<ClassifiedResidue> classifiedSpecial =
                new ArrayList<>();

        for (Chain chain : structure.getChains()) {
            List<Residue> keptInChain = new ArrayList<>();

            for (Residue residue : chain.residues()) {
                classifyAndApplyPolicy(
                        chain,
                        residue,
                        options,
                        allowedSpecialResidues,
                        keptInChain,
                        extractedLigands,
                        removedWaters,
                        removedMetals,
                        keptSpecial,
                        classifiedReceptor,
                        classifiedLigands,
                        classifiedWaters,
                        classifiedMetals,
                        classifiedSpecial);
            }

            if (!keptInChain.isEmpty()) {
                cleanedChains.add(
                        new Chain(
                                chain.id(),
                                keptInChain));
            }
        }

        Structure cleanedStructure =
                new Structure(cleanedChains);

        if (cleanedStructure.getResidueCount() == 0) {
            throw new IllegalStateException(
                    "Structure cleanup removed every residue; "
                            + "no receptor residues remain.");
        }

        Protein cleanedProtein = new ProteinFactory().copyWithStructure(
                protein, cleanedStructure);

        StructureCleanupResult cleanupResult =
                new StructureCleanupResult(
                        classifiedReceptor,
                        classifiedLigands,
                        classifiedWaters,
                        classifiedMetals,
                        classifiedSpecial);

        StructureCleanupReport cleanupReport =
                new StructureCleanupReport(
                        structure.getResidueCount(),
                        cleanedStructure.getResidueCount(),
                        removedWaters,
                        removedMetals,
                        keptSpecial);

        PreparedProtein updated =
                preparedProtein
                        .withProtein(cleanedProtein)
                        .withAttribute(
                                EXTRACTED_LIGANDS_ATTRIBUTE,
                                List.copyOf(extractedLigands))
                        .withAttribute(
                                CLEANUP_RESULT_ATTRIBUTE,
                                cleanupResult)
                        .withAttribute(
                                CLEANUP_REPORT_ATTRIBUTE,
                                cleanupReport);

        return OperationResult.success(updated);
    }

    private void classifyAndApplyPolicy(
            Chain chain,
            Residue residue,
            ReceptorPreparationOptions options,
            Set<String> allowedSpecialResidues,
            List<Residue> kept,
            List<Residue> extractedLigands,
            List<String> removedWaters,
            List<String> removedMetals,
            List<String> keptSpecial,
            List<ClassifiedResidue> classifiedReceptor,
            List<ClassifiedResidue> classifiedLigands,
            List<ClassifiedResidue> classifiedWaters,
            List<ClassifiedResidue> classifiedMetals,
            List<ClassifiedResidue> classifiedSpecial) {

        String name = normalizeName(residue.getName());
        String label = residueLabel(chain, residue);

        ResidueClassificationEvidence evidence =
                primaryEvidence(residue);

        ResidueKind kind =
                residueClassifier.classify(residue);

        log.debug(
                "Residue {} classified as {} using evidence {}",
                label,
                kind,
                evidence);

        switch (kind) {
            case WATER -> handleWater(chain,
                    residue,
                    label,
                    options,
                    kept,
                    removedWaters,
                    classifiedWaters);

            case STANDARD_AMINO_ACID -> {
                kept.add(residue);

                classifiedReceptor.add(
                        classified(
                                chain,
                                residue,
                                ResidueRole.STANDARD_AMINO_ACID,
                                ResidueDisposition.KEEP_IN_RECEPTOR,
                                "standard protein residue"));
            }

            case MODIFIED_AMINO_ACID -> {
                if (allowedSpecialResidues.contains(name)) {
                    kept.add(residue);
                    keptSpecial.add(label);

                    ClassifiedResidue classified =
                            classified(
                                    chain,
                                    residue,
                                    ResidueRole.MODIFIED_AMINO_ACID,
                                    ResidueDisposition.KEEP_IN_RECEPTOR,
                                    "modified protein residue enabled "
                                            + "by special-residue policy");

                    classifiedReceptor.add(classified);
                    classifiedSpecial.add(classified);
                } else {
                    extractedLigands.add(residue);

                    classifiedLigands.add(
                            classified(
                                    chain,
                                    residue,
                                    ResidueRole.MODIFIED_AMINO_ACID,
                                    ResidueDisposition.EXTRACT_AS_LIGAND,
                                    "modified protein residue lacks "
                                            + "enabled receptor support"));

                    log.warn(
                            "Modified protein residue {} is not enabled as a supported "
                                    + "special residue",
                            label);
                }
            }

            case ION_OR_METAL -> {
                if (options.keepMetals()) {
                    kept.add(residue);
                    keptSpecial.add(label);

                    ClassifiedResidue classified =
                            classified(
                                    chain,
                                    residue,
                                    ResidueRole.METAL_OR_ION,
                                    ResidueDisposition.KEEP_IN_RECEPTOR,
                                    "metal or ion retained by cleanup policy");

                    classifiedReceptor.add(classified);
                    classifiedSpecial.add(classified);
                } else {
                    removedMetals.add(label);

                    classifiedMetals.add(
                            classified(
                                    chain,
                                    residue,
                                    ResidueRole.METAL_OR_ION,
                                    ResidueDisposition.REMOVE,
                                    "metal or ion removed by cleanup policy"));
                }
            }

            case NON_POLYMER -> {
                if (allowedSpecialResidues.contains(name)) {
                    kept.add(residue);
                    keptSpecial.add(label);

                    ClassifiedResidue classified =
                            classified(
                                    chain,
                                    residue,
                                    ResidueRole.LIGAND,
                                    ResidueDisposition.KEEP_IN_RECEPTOR,
                                    "non-polymer retained by "
                                            + "special-residue policy");

                    classifiedReceptor.add(classified);
                    classifiedSpecial.add(classified);
                } else {
                    extractedLigands.add(residue);

                    classifiedLigands.add(
                            classified(
                                    chain,
                                    residue,
                                    ResidueRole.LIGAND,
                                    ResidueDisposition.EXTRACT_AS_LIGAND,
                                    "CCD identifies a non-polymer component"));
                }
            }

            case UNKNOWN -> {
                if (allowedSpecialResidues.contains(name)) {
                    kept.add(residue);
                    keptSpecial.add(label);

                    ClassifiedResidue classified =
                            classified(
                                    chain,
                                    residue,
                                    ResidueRole.UNKNOWN,
                                    ResidueDisposition.KEEP_IN_RECEPTOR,
                                    "unknown component retained by "
                                            + "special-residue policy");

                    classifiedReceptor.add(classified);
                    classifiedSpecial.add(classified);
                } else {
                    extractedLigands.add(residue);

                    classifiedLigands.add(
                            classified(
                                    chain,
                                    residue,
                                    ResidueRole.UNKNOWN,
                                    ResidueDisposition.EXTRACT_AS_LIGAND,
                                    "unknown component extracted by "
                                            + "fallback policy"));
                }
            }
        }
    }

    private void handleWater(Chain chain,
            Residue residue,
            String label,
            ReceptorPreparationOptions options,
            List<Residue> kept,
            List<String> removedWaters,
            List<ClassifiedResidue> classifiedWaters) {

        if (options.removeWaters()) {
            removedWaters.add(label);

            classifiedWaters.add(
                    new ClassifiedResidue(chain.id(),
                            residue,
                            ResidueRole.WATER,
                            ResidueDisposition.REMOVE,
                            "water removed by cleanup policy"));

            return;
        }

        throw new IllegalArgumentException(
                "Unsupported residue "
                        + label
                        + ": water retention is not supported "
                        + "for docking preparation");
    }

    private ResidueClassificationEvidence primaryEvidence(
            Residue residue) {

        return residue.getClassificationEvidence()
                .stream()
                .findFirst()
                .orElse(null);
    }

    private ClassifiedResidue classified(
            Chain chain,
            Residue residue,
            ResidueRole role,
            ResidueDisposition disposition,
            String reason) {

        return new ClassifiedResidue(
                chain.id(),
                residue,
                role,
                disposition,
                reason);
    }

    private String normalizeName(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private String residueLabel(
            Chain chain,
            Residue residue) {

        String insertionCode =
                residue.getInsertionCode() == null
                        ? ""
                        : residue.getInsertionCode().toString();

        return residue.getName()
                + " "
                + chain.id()
                + ":"
                + residue.getNumber()
                + insertionCode;
    }
}
