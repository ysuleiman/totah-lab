package totah.lab.web.ligandcontact;

import totah.lab.athena.pocket.compare.residue.MatchType;
import totah.lab.athena.pocket.compare.residue
        .ResidueCorrespondenceCalculator;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.evidence.LigandContact;
import totah.lab.athena.pocket.evidence.LigandContactType;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.hermes.biohub.model.BiohubPocketEvidence;
import totah.lab.hermes.biohub.model.BiohubPocketEvidence.ResidueContact;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Residue-level ligand-contact conservation between two homologous
 * targets, computed entirely from existing BioHub pocket evidence
 * artifacts and the cached protein sequence alignment.
 *
 * <p>Every aligned residue pair that touches the ligand shell on at
 * least one side becomes a report row carrying both sides' direct
 * contact flag, minimum ligand distance, contacting atom-pair count,
 * and the pair's {@link MatchType} (identity / conservative /
 * chemistry-compatible / different, classified by the exact rules of
 * the production residue-correspondence pipeline). Contact residues
 * lost to alignment gaps appear as single-sided rows flagged as not
 * sequence-consistent.</p>
 *
 * <p>The aggregation deliberately keeps the directional counts
 * separate: a small ligand shell embedded in a larger one shows up as
 * high candidate coverage with lower query coverage.</p>
 */
public final class LigandContactConservationAnalyzer {

    /**
     * The evidence source recorded on the canonical contact records.
     */
    public static final String BIOHUB_EVIDENCE_SOURCE = "BIOHUB";

    /**
     * One report row: an aligned residue pair (or a single-sided row
     * for a contact residue lost to an alignment gap).
     */
    public record Row(
            Integer queryResidueNumber,
            String queryResidueName,
            Integer candidateResidueNumber,
            String candidateResidueName,
            boolean queryDirectContact,
            boolean candidateDirectContact,
            Double queryMinimumDistance,
            Double candidateMinimumDistance,
            Integer queryAtomPairCount,
            Integer candidateAtomPairCount,
            boolean sequenceConsistent,
            MatchType matchType
    ) {
    }

    /**
     * Directional aggregate counts over the report rows.
     *
     * @param queryContactCount query residues in direct ligand contact
     * @param matchedContactCount query contacts with an aligned
     *                            candidate residue
     * @param sharedContactCount aligned pairs where both sides are
     *                           direct contacts
     * @param identicalSharedCount shared contacts with identical
     *                             residue names
     * @param conservativeSharedCount shared contacts in the same
     *                                conservative substitution set
     * @param nonConservativeSharedCount shared contacts classified
     *                                CHEMISTRY_COMPATIBLE or DIFFERENT
     * @param unmatchedQueryContactCount query contacts absent from the
     *                                alignment (gap)
     * @param queryContactsAlignedToNonContact aligned query contacts
     *                                whose partner is not a contact
     * @param candidateContactCount candidate direct-contact count
     * @param candidateOnlyContactCount candidate contacts absent from
     *                                the alignment (gap)
     * @param candidateContactsAlignedToNonContact aligned candidate
     *                                contacts whose partner is not a
     *                                contact
     * @param meanDistanceDifference mean absolute minimum-distance
     *                               difference over shared contacts
     * @param medianDistanceDifference median of the same
     * @param queryContactCoverage shared / query contacts
     * @param candidateContactCoverage shared / candidate contacts
     */
    public record Aggregate(
            int queryContactCount,
            int matchedContactCount,
            int sharedContactCount,
            int identicalSharedCount,
            int conservativeSharedCount,
            int nonConservativeSharedCount,
            int unmatchedQueryContactCount,
            int queryContactsAlignedToNonContact,
            int candidateContactCount,
            int candidateOnlyContactCount,
            int candidateContactsAlignedToNonContact,
            double meanDistanceDifference,
            double medianDistanceDifference,
            double queryContactCoverage,
            double candidateContactCoverage
    ) {
    }

    /**
     * The full conservation report: the aligned rows, the directional
     * aggregate, and the canonical {@link LigandContact} records of
     * every evidence residue on both sides (contact strength, minimum
     * distance, CCD code and evidence source populated from the
     * BioHub evidence).
     */
    public record LigandContactConservationReport(
            String queryLabel,
            String candidateLabel,
            String ligandCcd,
            String model,
            double directContactCutoff,
            double alignmentIdentity,
            List<Row> rows,
            Aggregate aggregate,
            List<LigandContact> contacts
    ) {
        public LigandContactConservationReport {
            rows = List.copyOf(rows);
            contacts = List.copyOf(contacts);
        }
    }

    public LigandContactConservationReport analyze(
            String queryLabel,
            String candidateLabel,
            BiohubPocketEvidence queryEvidence,
            BiohubPocketEvidence candidateEvidence,
            SequenceAlignment alignment
    ) {
        Objects.requireNonNull(queryEvidence, "queryEvidence");
        Objects.requireNonNull(candidateEvidence, "candidateEvidence");
        Objects.requireNonNull(alignment, "alignment");

        Map<Integer, ResidueContact> queryContacts =
                byResidueNumber(queryEvidence.residues());
        Map<Integer, ResidueContact> candidateContacts =
                byResidueNumber(candidateEvidence.residues());

        Map<Integer, AlignedResiduePair> byQueryNumber = new HashMap<>();
        Map<Integer, AlignedResiduePair> byCandidateNumber =
                new HashMap<>();
        for (AlignedResiduePair pair : alignment.pairs()) {
            byQueryNumber.put(pair.queryResidueNumber(), pair);
            byCandidateNumber.put(pair.candidateResidueNumber(), pair);
        }

        List<Row> rows = new ArrayList<>();

        for (AlignedResiduePair pair : alignment.pairs()) {
            ResidueContact query =
                    queryContacts.get(pair.queryResidueNumber());
            ResidueContact candidate =
                    candidateContacts.get(pair.candidateResidueNumber());
            if (query == null && candidate == null) {
                continue;
            }
            rows.add(alignedRow(pair, query, candidate));
        }

        for (ResidueContact query : queryEvidence.residues()) {
            if (!byQueryNumber.containsKey(query.residueNumber())) {
                rows.add(singleSidedRow(query, true));
            }
        }
        for (ResidueContact candidate : candidateEvidence.residues()) {
            if (!byCandidateNumber.containsKey(candidate.residueNumber())) {
                rows.add(singleSidedRow(candidate, false));
            }
        }

        rows.sort(Comparator
                .comparing((Row row) -> row.queryResidueNumber() != null
                        ? row.queryResidueNumber()
                        : Integer.MAX_VALUE)
                .thenComparing(row -> row.candidateResidueNumber() != null
                        ? row.candidateResidueNumber()
                        : Integer.MAX_VALUE));

        return new LigandContactConservationReport(
                queryLabel,
                candidateLabel,
                queryEvidence.ligandCcd(),
                queryEvidence.model(),
                queryEvidence.directContactCutoff(),
                alignment.identity(),
                rows,
                aggregate(
                        rows,
                        queryEvidence.residues(),
                        candidateEvidence.residues()
                ),
                canonicalContacts(
                        queryLabel,
                        queryEvidence,
                        candidateLabel,
                        candidateEvidence
                )
        );
    }

    /**
     * The canonical contact records of both sides' evidence residues:
     * the free-form CCD code, the residue reference (chain, number,
     * name), the minimum ligand distance and the contact strength
     * (DIRECT within the direct-contact cutoff, SHELL beyond it),
     * sourced from the BioHub pocket evidence. The labels stand in as
     * the pocket references of the two compared structures.
     */
    private static List<LigandContact> canonicalContacts(
            String queryLabel,
            BiohubPocketEvidence queryEvidence,
            String candidateLabel,
            BiohubPocketEvidence candidateEvidence
    ) {
        List<LigandContact> contacts = new ArrayList<>();

        for (ResidueContact contact : queryEvidence.residues()) {
            contacts.add(canonicalContact(
                    queryLabel,
                    queryEvidence.ligandCcd(),
                    contact
            ));
        }
        for (ResidueContact contact : candidateEvidence.residues()) {
            contacts.add(canonicalContact(
                    candidateLabel,
                    candidateEvidence.ligandCcd(),
                    contact
            ));
        }

        return contacts;
    }

    /**
     * The canonical contact record of one BioHub evidence residue:
     * free-form CCD code, residue reference, minimum distance and
     * contact strength (DIRECT within the direct-contact cutoff,
     * SHELL beyond it), sourced from the BioHub pocket evidence.
     */
    public static LigandContact canonicalContact(
            String pocketReference,
            String ligandCcd,
            ResidueContact contact
    ) {
        return LigandContact.available(
                pocketReference,
                ligandCcd,
                new ResidueReference(
                        contact.chain(),
                        contact.residueNumber(),
                        ' ',
                        contact.residueName()
                ),
                contact.minimumDistance(),
                contact.directContact()
                        ? LigandContactType.DIRECT
                        : LigandContactType.SHELL,
                BIOHUB_EVIDENCE_SOURCE
        );
    }

    /**
     * Whether two evidence artifacts (e.g. SAM and SAH of one target)
     * describe the same residue set with the same distances and
     * atom-pair counts, within a distance epsilon.
     */
    public static boolean equivalent(
            BiohubPocketEvidence first,
            BiohubPocketEvidence second,
            double distanceEpsilon
    ) {
        Map<Integer, ResidueContact> firstByNumber =
                byResidueNumber(first.residues());
        Map<Integer, ResidueContact> secondByNumber =
                byResidueNumber(second.residues());

        if (!firstByNumber.keySet().equals(secondByNumber.keySet())) {
            return false;
        }

        for (Map.Entry<Integer, ResidueContact> entry :
                firstByNumber.entrySet()) {
            ResidueContact other = secondByNumber.get(entry.getKey());
            ResidueContact contact = entry.getValue();
            if (contact.directContact() != other.directContact()
                    || contact.contactingAtomPairCount()
                    != other.contactingAtomPairCount()
                    || Math.abs(contact.minimumDistance()
                    - other.minimumDistance()) > distanceEpsilon) {
                return false;
            }
        }
        return true;
    }

    private static Row alignedRow(
            AlignedResiduePair pair,
            ResidueContact query,
            ResidueContact candidate
    ) {
        return new Row(
                pair.queryResidueNumber(),
                pair.queryResidueName(),
                pair.candidateResidueNumber(),
                pair.candidateResidueName(),
                query != null && query.directContact(),
                candidate != null && candidate.directContact(),
                query == null ? null : query.minimumDistance(),
                candidate == null ? null : candidate.minimumDistance(),
                query == null ? null : query.contactingAtomPairCount(),
                candidate == null
                        ? null
                        : candidate.contactingAtomPairCount(),
                true,
                ResidueCorrespondenceCalculator.matchTypeOf(
                        pair.queryResidueName(),
                        pair.candidateResidueName()
                )
        );
    }

    private static Row singleSidedRow(
            ResidueContact contact,
            boolean querySide
    ) {
        return new Row(
                querySide ? contact.residueNumber() : null,
                querySide ? contact.residueName() : null,
                querySide ? null : contact.residueNumber(),
                querySide ? null : contact.residueName(),
                querySide && contact.directContact(),
                !querySide && contact.directContact(),
                querySide ? contact.minimumDistance() : null,
                querySide ? null : contact.minimumDistance(),
                querySide ? contact.contactingAtomPairCount() : null,
                querySide ? null : contact.contactingAtomPairCount(),
                false,
                null
        );
    }

    private static Aggregate aggregate(
            List<Row> rows,
            List<ResidueContact> queryResidues,
            List<ResidueContact> candidateResidues
    ) {
        int queryContacts = countDirect(queryResidues);
        int candidateContacts = countDirect(candidateResidues);

        int matched = 0;
        int shared = 0;
        int identical = 0;
        int conservative = 0;
        int nonConservative = 0;
        int unmatchedQuery = 0;
        int queryAlignedToNonContact = 0;
        int candidateOnly = 0;
        int candidateAlignedToNonContact = 0;
        List<Double> distanceDifferences = new ArrayList<>();

        for (Row row : rows) {
            if (row.queryDirectContact()) {
                if (!row.sequenceConsistent()) {
                    unmatchedQuery++;
                } else {
                    matched++;
                    if (row.candidateDirectContact()) {
                        shared++;
                        if (row.matchType() == MatchType.IDENTICAL) {
                            identical++;
                        } else if (row.matchType()
                                == MatchType.CONSERVATIVE) {
                            conservative++;
                        } else {
                            nonConservative++;
                        }
                        distanceDifferences.add(Math.abs(
                                row.queryMinimumDistance()
                                        - row.candidateMinimumDistance()
                        ));
                    } else {
                        queryAlignedToNonContact++;
                    }
                }
            }
            if (row.candidateDirectContact()) {
                if (!row.sequenceConsistent()) {
                    candidateOnly++;
                } else if (!row.queryDirectContact()) {
                    candidateAlignedToNonContact++;
                }
            }
        }

        return new Aggregate(
                queryContacts,
                matched,
                shared,
                identical,
                conservative,
                nonConservative,
                unmatchedQuery,
                queryAlignedToNonContact,
                candidateContacts,
                candidateOnly,
                candidateAlignedToNonContact,
                mean(distanceDifferences),
                median(distanceDifferences),
                ratio(shared, queryContacts),
                ratio(shared, candidateContacts)
        );
    }

    private static Map<Integer, ResidueContact> byResidueNumber(
            List<ResidueContact> residues
    ) {
        Map<Integer, ResidueContact> byNumber = new HashMap<>();
        for (ResidueContact contact : residues) {
            byNumber.put(contact.residueNumber(), contact);
        }
        return byNumber;
    }

    private static int countDirect(List<ResidueContact> residues) {
        int count = 0;
        for (ResidueContact contact : residues) {
            if (contact.directContact()) {
                count++;
            }
        }
        return count;
    }

    private static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private static double ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return numerator / (double) denominator;
    }
}
