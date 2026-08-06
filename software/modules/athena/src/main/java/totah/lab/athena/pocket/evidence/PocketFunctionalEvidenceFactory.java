package totah.lab.athena.pocket.evidence;

import totah.lab.athena.pocket.compare.residue.MatchType;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.ResidueCorrespondence;
import totah.lab.athena.pocket.compare.residue.ResidueMatch;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.compare.residue.ResidueSubstitutionScorer;
import totah.lab.athena.sequence.SequenceAlignment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds {@link PocketFunctionalEvidence} components under the
 * SELECTED alignment. The factory is ligand-agnostic: contact
 * annotations come from a {@link LigandContactProvider} for any
 * {@link FunctionalLigand}; nothing is hardcoded to SAM.
 *
 * <p>Conservation rule: a query contact residue is conserved only
 * when it has a spatial correspondence under the selected alignment;
 * whether the pair also agrees with the sequence alignment is
 * reported per pair via
 * {@link ResidueCorrespondenceEvidence#sequenceAlignedPair()}.
 * Identity, substitution and chemistry are reported per pair; the
 * candidate-side annotation is reported separately, so both-annotated,
 * query-only and candidate-only pairs are all representable.</p>
 *
 * <p>Contact annotations are matched to pocket residues by chain id,
 * residue number and insertion code.</p>
 */
public final class PocketFunctionalEvidenceFactory {

    private final PocketResidueEvidenceFactory residueFactory;

    public PocketFunctionalEvidenceFactory(
            ResidueSubstitutionScorer substitutionScorer
    ) {
        this.residueFactory = new PocketResidueEvidenceFactory(
                Objects.requireNonNull(
                        substitutionScorer,
                        "substitutionScorer"
                )
        );
    }

    /**
     * Key-residue summary: how many of the configured key residues
     * (named like {@code "LEU145"}, case-insensitive) are present in
     * the query pocket, matched, identical and chemically acceptable.
     */
    public KeyResidueEvidence keyResidues(
            ResidueCorrespondence selectedCorrespondence,
            Set<String> keyResidues
    ) {
        Objects.requireNonNull(
                selectedCorrespondence,
                "selectedCorrespondence"
        );
        Objects.requireNonNull(keyResidues, "keyResidues");

        Set<String> normalizedKeys = new HashSet<>();

        for (String keyResidue : keyResidues) {
            normalizedKeys.add(
                    keyResidue.trim().toUpperCase(Locale.ROOT)
            );
        }

        Set<String> queryLabels = new HashSet<>();

        for (ResidueMatch match : selectedCorrespondence.matches()) {
            queryLabels.add(PocketResidueEvidenceFactory.label(
                    match.query().reference()
            ));
        }

        for (PocketResiduePoint unmatched
                : selectedCorrespondence.unmatchedQuery()) {
            queryLabels.add(PocketResidueEvidenceFactory.label(
                    unmatched.reference()
            ));
        }

        int totalKeyResidueCount = 0;

        for (String normalizedKey : normalizedKeys) {
            if (queryLabels.contains(normalizedKey)) {
                totalKeyResidueCount++;
            }
        }

        int matchedCount = 0;
        int identicalCount = 0;
        int chemistryCompatibleCount = 0;

        for (ResidueMatch match : selectedCorrespondence.matches()) {
            String label = PocketResidueEvidenceFactory.label(
                    match.query().reference()
            );

            if (!normalizedKeys.contains(label)) {
                continue;
            }

            matchedCount++;

            if (match.matchType() == MatchType.IDENTICAL) {
                identicalCount++;
            }

            if (match.matchType() != MatchType.DIFFERENT) {
                chemistryCompatibleCount++;
            }
        }

        return new KeyResidueEvidence(
                totalKeyResidueCount,
                matchedCount,
                identicalCount,
                chemistryCompatibleCount
        );
    }

    /**
     * Ligand-contact conservation evidence for one ligand.
     *
     * @param sequenceAlignment the protein sequence alignment, or
     *                          {@code null} when no sequence evidence
     *                          exists
     */
    public LigandContactEvidence ligandContacts(
            ResidueCorrespondence selectedCorrespondence,
            SequenceAlignment sequenceAlignment,
            LigandContactProvider provider,
            String queryStructureKey,
            String candidateStructureKey,
            FunctionalLigand ligand
    ) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(queryStructureKey, "queryStructureKey");
        Objects.requireNonNull(
                candidateStructureKey,
                "candidateStructureKey"
        );
        Objects.requireNonNull(ligand, "ligand");

        return ligandContacts(
                selectedCorrespondence,
                sequenceAlignment,
                provider.contacts(queryStructureKey, ligand),
                provider.contacts(candidateStructureKey, ligand),
                ligand.name()
        );
    }

    /**
     * Ligand-contact conservation evidence for one ligand named by
     * its free-form CCD code (a String, not an enum, so ligands
     * beyond the {@link FunctionalLigand} values are representable).
     * The contact annotation sets are matched to pocket residues by
     * chain id, residue number and insertion code.
     *
     * @param sequenceAlignment the protein sequence alignment, or
     *                          {@code null} when no sequence evidence
     *                          exists
     */
    public LigandContactEvidence ligandContacts(
            ResidueCorrespondence selectedCorrespondence,
            SequenceAlignment sequenceAlignment,
            Set<ResidueReference> queryContacts,
            Set<ResidueReference> candidateContacts,
            String ligandCcd
    ) {
        Objects.requireNonNull(
                selectedCorrespondence,
                "selectedCorrespondence"
        );
        Objects.requireNonNull(ligandCcd, "ligandCcd");

        Set<String> queryContactKeys = contactKeys(queryContacts);
        Set<String> candidateContactKeys = contactKeys(candidateContacts);
        Set<String> queryContactLabels = contactLabels(queryContacts);
        Set<String> candidateContactLabels =
                contactLabels(candidateContacts);

        List<FunctionalResidueCorrespondence> correspondences =
                new ArrayList<>();

        int matchedContactCount = 0;
        int identicalCount = 0;
        int conservativeCount = 0;
        int chemistryCompatibleCount = 0;
        int incompatibleCount = 0;
        int sharedAnnotationCount = 0;
        double substitutionSum = 0.0;
        double chemistrySum = 0.0;

        for (ResidueMatch match : selectedCorrespondence.matches()) {
            boolean queryAnnotated = queryContactKeys.contains(
                    contactKey(match.query().reference())
            );
            boolean candidateAnnotated =
                    candidateContactKeys.contains(
                            contactKey(match.candidate().reference())
                    );

            if (!queryAnnotated && !candidateAnnotated) {
                continue;
            }

            ResidueCorrespondenceEvidence pair =
                    residueFactory.mapMatch(
                            match,
                            sequenceAlignment,
                            Set.of(),
                            queryContactLabels,
                            candidateContactLabels
                    );

            correspondences.add(new FunctionalResidueCorrespondence(
                    match.query().reference(),
                    Optional.of(pair),
                    queryAnnotated,
                    candidateAnnotated
            ));

            if (!queryAnnotated) {
                // Candidate-only annotation: reported in the
                // correspondence list but not a query contact.
                continue;
            }

            matchedContactCount++;
            substitutionSum += pair.substitutionScore();
            chemistrySum += pair.chemistryScore();

            switch (match.matchType()) {
                case IDENTICAL -> identicalCount++;
                case CONSERVATIVE -> conservativeCount++;
                case CHEMISTRY_COMPATIBLE ->
                        chemistryCompatibleCount++;
                case DIFFERENT -> incompatibleCount++;
                default -> {
                    // UNMATCHED never appears in a match list.
                }
            }

            if (candidateAnnotated) {
                sharedAnnotationCount++;
            }
        }

        int unmatchedContactCount = 0;

        for (PocketResiduePoint unmatched
                : selectedCorrespondence.unmatchedQuery()) {
            if (!queryContactKeys.contains(
                    contactKey(unmatched.reference())
            )) {
                continue;
            }

            unmatchedContactCount++;

            correspondences.add(new FunctionalResidueCorrespondence(
                    unmatched.reference(),
                    Optional.empty(),
                    true,
                    false
            ));
        }

        int queryContactCount =
                matchedContactCount + unmatchedContactCount;

        return new LigandContactEvidence(
                ligandCcd,
                queryContactCount,
                matchedContactCount,
                identicalCount,
                conservativeCount,
                chemistryCompatibleCount,
                incompatibleCount,
                unmatchedContactCount,
                sharedAnnotationCount,
                fraction(matchedContactCount, queryContactCount),
                fraction(identicalCount, matchedContactCount),
                matchedContactCount == 0
                        ? 0.0
                        : substitutionSum / matchedContactCount,
                matchedContactCount == 0
                        ? 0.0
                        : chemistrySum / matchedContactCount,
                correspondences
        );
    }

    private static Set<String> contactKeys(
            Set<ResidueReference> contacts
    ) {
        Objects.requireNonNull(contacts, "contacts");

        Set<String> keys = new HashSet<>();

        for (ResidueReference contact : contacts) {
            keys.add(contactKey(contact));
        }

        return keys;
    }

    private static Set<String> contactLabels(
            Set<ResidueReference> contacts
    ) {
        Set<String> labels = new HashSet<>();

        for (ResidueReference contact : contacts) {
            labels.add(PocketResidueEvidenceFactory.label(contact));
        }

        return labels;
    }

    private static String contactKey(ResidueReference reference) {
        return reference.chainId() + "|"
                + reference.residueNumber() + "|"
                + reference.insertionCode();
    }

    private static double fraction(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }

        return (double) numerator / denominator;
    }
}
