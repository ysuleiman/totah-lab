package totah.lab.web.ligandcontact;

import totah.lab.athena.pocket.compare.residue.MatchType;
import totah.lab.athena.pocket.compare.residue
        .ResidueCorrespondenceCalculator;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.web.ligandcontact.ComplexLigandContactExtractor
        .ResidueMoietyContact;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Moiety-level ligand-contact conservation: given per-residue
 * ligand-moiety contacts of two homologous complexes and the protein
 * sequence alignment, reports for every aligned residue pair which
 * {@link SamMoiety} each side faces, and aggregates how often the
 * aligned residues contact the same moiety.
 *
 * <p>This answers the stronger Phase 2 question — does the same
 * residue contact the same part of the ligand in both proteins —
 * which the residue-level report cannot distinguish.</p>
 */
public final class LigandMoietyConservationAnalyzer {

    /**
     * One aligned residue pair with its ligand-facing moieties.
     */
    public record Row(
            Integer queryResidueNumber,
            String queryResidueName,
            Integer candidateResidueNumber,
            String candidateResidueName,
            SamMoiety queryFacingMoiety,
            SamMoiety candidateFacingMoiety,
            Double queryFacingDistance,
            Double candidateFacingDistance,
            boolean queryDirectContact,
            boolean candidateDirectContact,
            boolean sameFacingMoiety,
            boolean sequenceConsistent,
            MatchType matchType
    ) {
    }

    public record Aggregate(
            Map<SamMoiety, Integer> queryContactsByMoiety,
            Map<SamMoiety, Integer> candidateContactsByMoiety,
            int sharedContactCount,
            int sameMoietyCount,
            int moietySwitchCount,
            double meanFacingDistanceDifference,
            double medianFacingDistanceDifference
    ) {
        public Aggregate {
            queryContactsByMoiety =
                    new EnumMap<>(Map.copyOf(queryContactsByMoiety));
            candidateContactsByMoiety = new EnumMap<>(
                    Map.copyOf(candidateContactsByMoiety)
            );
        }
    }

    public record LigandMoietyConservationReport(
            String queryLabel,
            String candidateLabel,
            String ligandCcd,
            List<Row> rows,
            Aggregate aggregate
    ) {
        public LigandMoietyConservationReport {
            rows = List.copyOf(rows);
        }
    }

    public LigandMoietyConservationReport analyze(
            String queryLabel,
            String candidateLabel,
            String ligandCcd,
            List<ResidueMoietyContact> queryContacts,
            List<ResidueMoietyContact> candidateContacts,
            SequenceAlignment alignment
    ) {
        Objects.requireNonNull(queryContacts, "queryContacts");
        Objects.requireNonNull(candidateContacts, "candidateContacts");
        Objects.requireNonNull(alignment, "alignment");

        Map<Integer, ResidueMoietyContact> queryByNumber =
                byResidueNumber(queryContacts);
        Map<Integer, ResidueMoietyContact> candidateByNumber =
                byResidueNumber(candidateContacts);

        Map<Integer, AlignedResiduePair> byQueryNumber = new HashMap<>();
        Map<Integer, AlignedResiduePair> byCandidateNumber =
                new HashMap<>();
        for (AlignedResiduePair pair : alignment.pairs()) {
            byQueryNumber.put(pair.queryResidueNumber(), pair);
            byCandidateNumber.put(pair.candidateResidueNumber(), pair);
        }

        List<Row> rows = new ArrayList<>();

        for (AlignedResiduePair pair : alignment.pairs()) {
            ResidueMoietyContact query =
                    queryByNumber.get(pair.queryResidueNumber());
            ResidueMoietyContact candidate =
                    candidateByNumber.get(pair.candidateResidueNumber());

            boolean queryContact =
                    query != null && query.directContact();
            boolean candidateContact =
                    candidate != null && candidate.directContact();
            if (!queryContact && !candidateContact) {
                continue;
            }

            rows.add(new Row(
                    pair.queryResidueNumber(),
                    pair.queryResidueName(),
                    pair.candidateResidueNumber(),
                    pair.candidateResidueName(),
                    queryContact ? query.facingMoiety() : null,
                    candidateContact ? candidate.facingMoiety() : null,
                    queryContact ? query.facingDistance() : null,
                    candidateContact ? candidate.facingDistance() : null,
                    queryContact,
                    candidateContact,
                    queryContact && candidateContact
                            && query.facingMoiety()
                            == candidate.facingMoiety(),
                    true,
                    ResidueCorrespondenceCalculator.matchTypeOf(
                            pair.queryResidueName(),
                            pair.candidateResidueName()
                    )
            ));
        }

        for (ResidueMoietyContact query : queryContacts) {
            if (query.directContact()
                    && !byQueryNumber.containsKey(query.residueNumber())) {
                rows.add(singleSidedRow(query, true));
            }
        }
        for (ResidueMoietyContact candidate : candidateContacts) {
            if (candidate.directContact()
                    && !byCandidateNumber.containsKey(
                    candidate.residueNumber())) {
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

        return new LigandMoietyConservationReport(
                queryLabel,
                candidateLabel,
                ligandCcd,
                rows,
                aggregate(rows, queryContacts, candidateContacts)
        );
    }

    private static Row singleSidedRow(
            ResidueMoietyContact contact,
            boolean querySide
    ) {
        return new Row(
                querySide ? contact.residueNumber() : null,
                querySide ? contact.residueName() : null,
                querySide ? null : contact.residueNumber(),
                querySide ? null : contact.residueName(),
                querySide ? contact.facingMoiety() : null,
                querySide ? null : contact.facingMoiety(),
                querySide ? contact.facingDistance() : null,
                querySide ? null : contact.facingDistance(),
                querySide,
                !querySide,
                false,
                false,
                null
        );
    }

    private static Aggregate aggregate(
            List<Row> rows,
            List<ResidueMoietyContact> queryContacts,
            List<ResidueMoietyContact> candidateContacts
    ) {
        int shared = 0;
        int sameMoiety = 0;
        int switches = 0;
        List<Double> facingDifferences = new ArrayList<>();

        for (Row row : rows) {
            if (!row.sequenceConsistent()
                    || !row.queryDirectContact()
                    || !row.candidateDirectContact()) {
                continue;
            }
            shared++;
            if (row.sameFacingMoiety()) {
                sameMoiety++;
                facingDifferences.add(Math.abs(
                        row.queryFacingDistance()
                                - row.candidateFacingDistance()
                ));
            } else {
                switches++;
            }
        }

        return new Aggregate(
                contactsByMoiety(queryContacts),
                contactsByMoiety(candidateContacts),
                shared,
                sameMoiety,
                switches,
                mean(facingDifferences),
                median(facingDifferences)
        );
    }

    private static Map<SamMoiety, Integer> contactsByMoiety(
            List<ResidueMoietyContact> contacts
    ) {
        Map<SamMoiety, Integer> counts = new EnumMap<>(SamMoiety.class);
        for (ResidueMoietyContact contact : contacts) {
            if (contact.directContact()) {
                counts.merge(contact.facingMoiety(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static Map<Integer, ResidueMoietyContact> byResidueNumber(
            List<ResidueMoietyContact> contacts
    ) {
        Map<Integer, ResidueMoietyContact> byNumber = new HashMap<>();
        for (ResidueMoietyContact contact : contacts) {
            byNumber.put(contact.residueNumber(), contact);
        }
        return byNumber;
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
}
