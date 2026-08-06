package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.compare.residue.ResidueSubstitutionScorer;
import totah.lab.athena.pocket.compare.residue.ResidueChemistryScorer;
import totah.lab.athena.pocket.evidence.GlobalShapeRetrievalEvidence;
import totah.lab.athena.pocket.evidence.KeyResidueEvidence;
import totah.lab.athena.pocket.evidence.LigandContact;
import totah.lab.athena.pocket.evidence.LigandContactEvidence;
import totah.lab.athena.pocket.evidence.PocketAlignmentEvidence;
import totah.lab.athena.pocket.evidence.PocketAlignmentEvidenceFactory;
import totah.lab.athena.pocket.evidence.PocketAssessmentRules;
import totah.lab.athena.pocket.evidence.PocketAssessmentVerdict;
import totah.lab.athena.pocket.evidence.PocketCandidateSource;
import totah.lab.athena.pocket.evidence.PocketComparisonEvidence;
import totah.lab.athena.pocket.evidence.PocketFunctionalEvidence;
import totah.lab.athena.pocket.evidence.PocketFunctionalEvidenceFactory;
import totah.lab.athena.pocket.evidence.PocketMatchRetrievalEvidence;
import totah.lab.athena.pocket.evidence.PocketResidueEvidence;
import totah.lab.athena.pocket.evidence.PocketResidueEvidenceFactory;
import totah.lab.athena.pocket.evidence.PocketRetrievalEvidence;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.hermes.biohub.artifact.BiohubPocketEvidenceReader;
import totah.lab.hermes.biohub.model.BiohubPocketEvidence;
import totah.lab.web.ligandcontact.LigandContactConservationAnalyzer;
import totah.lab.web.persistence.PocketRepository;
import totah.lab.web.persistence.StructureRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Assembles the structured pocket-comparison report of one
 * query/candidate pair from the CURRENT live comparison path (the
 * shared {@link PocketSimilarityService#compareRun}): the athena
 * {@link PocketComparisonEvidence} bundle is built from the same
 * alignment, correspondence and assessments the compare endpoint
 * uses, the rules derive the verdict, and the serializable
 * {@link PocketComparisonReportView} renders the seven sections. No
 * alignment or metric is recomputed here.
 *
 * <p>Ligand-contact conservation is included when both structures
 * carry BioHub pocket evidence for a common ligand CCD code
 * (free-form String, matched case-insensitively on lookup); when no
 * common ligand evidence exists, the section reports
 * {@code NOT_AVAILABLE} — never zeroed counts. The assessment rules
 * are the documented, uncalibrated defaults of
 * {@link PocketAssessmentRules#defaults()}.</p>
 *
 * <p>A direct pairwise report does not pass through the retrieval
 * stages: both retrieval methods report {@code evaluated = false};
 * only the chosen-reference flag is resolved.</p>
 */
@Service
public class PocketComparisonReportService {

    private static final String LIGAND_EVIDENCE_SOURCE =
            LigandContactConservationAnalyzer.BIOHUB_EVIDENCE_SOURCE;

    private final PocketSimilarityService similarityService;
    private final StructureRepository structureRepository;
    private final PocketRepository pocketRepository;
    private final BiohubPocketEvidenceReader biohubReader =
            new BiohubPocketEvidenceReader();
    private final ResidueChemistryScorer chemistryScorer =
            new ResidueChemistryScorer();
    private final PocketAlignmentEvidenceFactory alignmentFactory =
            new PocketAlignmentEvidenceFactory();
    private final PocketResidueEvidenceFactory residueFactory =
            new PocketResidueEvidenceFactory(
                    new ResidueSubstitutionScorer()
            );
    private final PocketFunctionalEvidenceFactory functionalFactory =
            new PocketFunctionalEvidenceFactory(
                    new ResidueSubstitutionScorer()
            );
    private final PocketAssessmentRules assessmentRules =
            PocketAssessmentRules.defaults();

    public PocketComparisonReportService(
            PocketSimilarityService similarityService,
            StructureRepository structureRepository,
            PocketRepository pocketRepository
    ) {
        this.similarityService = similarityService;
        this.structureRepository = structureRepository;
        this.pocketRepository = pocketRepository;
    }

    /**
     * The full structured report of one query/candidate pair.
     */
    @Transactional(readOnly = true)
    public PocketComparisonReportView report(
            long queryPocketId,
            long candidatePocketId
    ) {
        PocketSimilarityService.ComparisonRun run =
                similarityService.compareRun(
                        queryPocketId,
                        candidatePocketId
                );

        boolean chosenReference = structureRepository
                .findAllChosenPocketIds()
                .contains(candidatePocketId);

        PocketRetrievalEvidence retrieval = new PocketRetrievalEvidence(
                GlobalShapeRetrievalEvidence.notEvaluated(),
                PocketMatchRetrievalEvidence.notEvaluated(),
                chosenReference,
                chosenReference
                        ? Set.of(PocketCandidateSource.CHOSEN_REFERENCE)
                        : Set.of()
        );

        PocketAlignmentEvidence alignment =
                alignmentFactory.create(run.alignmentResult());

        Set<String> keyResidues = new HashSet<>(run.keyResidues());
        PocketResidueEvidence residues = residueFactory.create(
                run.alignmentResult().correspondence(),
                run.sequenceAlignment(),
                keyResidues
        );
        KeyResidueEvidence keyResidueEvidence =
                functionalFactory.keyResidues(
                        run.alignmentResult().correspondence(),
                        keyResidues
                );

        LigandContactSelection ligand = ligandContacts(
                run,
                queryPocketId,
                candidatePocketId
        );

        PocketFunctionalEvidence functional = new PocketFunctionalEvidence(
                ligand.evidence(),
                keyResidueEvidence
        );

        PocketAssessmentVerdict verdict = assessmentRules.assess(
                alignment,
                residues,
                functional
        );

        PocketComparisonEvidence evidence = new PocketComparisonEvidence(
                retrieval,
                alignment,
                residues,
                functional,
                verdict
        );

        return PocketComparisonReportView.toView(
                queryPocketId,
                candidatePocketId,
                evidence,
                AlignmentMetadataView.toView(run.alignmentResult()),
                ChemistryAssessmentView.toView(
                        run.chemistryAssessment(),
                        run.substitutionAssessment(),
                        chemistryScorer.classify(
                                run.chemistryAssessment(),
                                run.finalSimilarity()
                        ),
                        run.finalSimilarity()
                ),
                run.keyResidues(),
                ligand.evidence().isPresent()
                        ? LIGAND_EVIDENCE_SOURCE
                        : null,
                ligand.contacts()
        );
    }

    /**
     * The BioHub pocket evidence artifacts of one structure (empty
     * for structures without BioHub pockets). Package-private seam:
     * tests substitute the artifact store.
     */
    List<BiohubPocketEvidence> biohubEvidence(long structureId) {
        List<BiohubPocketEvidence> evidence = new ArrayList<>();

        for (String location : pocketRepository
                .findArtifactStorageLocations(
                        structureId,
                        PocketSource.BIOHUB
                )) {
            try {
                evidence.add(biohubReader.read(Path.of(location)));
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Cannot read BioHub pocket artifact " + location,
                        exception
                );
            }
        }

        return evidence;
    }

    /**
     * Ligand-contact conservation for the pair: when both structures
     * carry BioHub evidence for a common ligand, the athena
     * ligand-contact evidence under the selected alignment plus the
     * canonical per-residue contact records; an empty Optional and an
     * empty contact list otherwise (absence is reported, never
     * fabricated).
     */
    private LigandContactSelection ligandContacts(
            PocketSimilarityService.ComparisonRun run,
            long queryPocketId,
            long candidatePocketId
    ) {
        Map<String, BiohubPocketEvidence> queryByLigand = byLigandCcd(
                biohubEvidence(run.querySummary().getStructureId())
        );
        Map<String, BiohubPocketEvidence> candidateByLigand = byLigandCcd(
                biohubEvidence(run.candidateSummary().getStructureId())
        );

        Optional<String> ligandCcd = preferredCommonLigand(
                queryByLigand.keySet(),
                candidateByLigand.keySet()
        );

        if (ligandCcd.isEmpty()) {
            return new LigandContactSelection(
                    Optional.empty(),
                    List.of()
            );
        }

        BiohubPocketEvidence query = queryByLigand.get(ligandCcd.get());
        BiohubPocketEvidence candidate =
                candidateByLigand.get(ligandCcd.get());

        LigandContactEvidence contactEvidence =
                functionalFactory.ligandContacts(
                        run.alignmentResult().correspondence(),
                        run.sequenceAlignment(),
                        directContacts(query),
                        directContacts(candidate),
                        ligandCcd.get()
                );

        List<LigandContact> contacts = new ArrayList<>();
        String queryReference = String.valueOf(queryPocketId);
        String candidateReference = String.valueOf(candidatePocketId);

        for (BiohubPocketEvidence.ResidueContact contact
                : query.residues()) {
            contacts.add(LigandContactConservationAnalyzer
                    .canonicalContact(
                            queryReference,
                            query.ligandCcd(),
                            contact
                    ));
        }
        for (BiohubPocketEvidence.ResidueContact contact
                : candidate.residues()) {
            contacts.add(LigandContactConservationAnalyzer
                    .canonicalContact(
                            candidateReference,
                            candidate.ligandCcd(),
                            contact
                    ));
        }

        return new LigandContactSelection(
                Optional.of(contactEvidence),
                contacts
        );
    }

    /**
     * The ligand to evaluate: SAM when both sides annotate it (the
     * report runner's preference), otherwise the first common CCD
     * code in sorted order — deterministic either way.
     */
    private static Optional<String> preferredCommonLigand(
            Set<String> queryLigands,
            Set<String> candidateLigands
    ) {
        List<String> common = queryLigands.stream()
                .filter(candidateLigands::contains)
                .sorted()
                .toList();

        if (common.contains("SAM")) {
            return Optional.of("SAM");
        }

        return common.stream().findFirst();
    }

    private static Map<String, BiohubPocketEvidence> byLigandCcd(
            List<BiohubPocketEvidence> evidence
    ) {
        Map<String, BiohubPocketEvidence> byLigand =
                new LinkedHashMap<>();

        for (BiohubPocketEvidence pocket : evidence) {
            byLigand.putIfAbsent(pocket.ligandCcd(), pocket);
        }

        return byLigand;
    }

    /**
     * The direct-contact residues of one BioHub evidence artifact as
     * residue references (shell members beyond the direct-contact
     * cutoff are not contacts in the athena conservation sense).
     */
    private static Set<ResidueReference> directContacts(
            BiohubPocketEvidence evidence
    ) {
        Set<ResidueReference> contacts = new HashSet<>();

        for (BiohubPocketEvidence.ResidueContact contact
                : evidence.residues()) {
            if (contact.directContact()) {
                contacts.add(new ResidueReference(
                        contact.chain(),
                        contact.residueNumber(),
                        ' ',
                        contact.residueName()
                ));
            }
        }

        return contacts;
    }

    private record LigandContactSelection(
            Optional<LigandContactEvidence> evidence,
            List<LigandContact> contacts
    ) {

        private LigandContactSelection {
            Objects.requireNonNull(evidence, "evidence");
            contacts = List.copyOf(
                    Objects.requireNonNull(contacts, "contacts")
            );
        }
    }
}
