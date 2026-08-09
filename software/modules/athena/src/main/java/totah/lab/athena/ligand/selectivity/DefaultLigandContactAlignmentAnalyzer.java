package totah.lab.athena.ligand.selectivity;

import totah.lab.athena.ligand.contact.ContactType;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.athena.sequence.StructureSequences;
import totah.lab.gaia.classification.ResidueCategory;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Default {@link LigandContactAlignmentAnalyzer}. The two receptor
 * sequences are aligned with {@link NeedlemanWunschSequenceAligner}
 * (via the shared {@link StructureSequences} mapping); every contact,
 * distance, pocket-membership and chemistry lookup is then keyed by
 * the aligned residue numbers — raw residue numbers are never matched
 * across receptors directly.
 *
 * <p>Consistent with the underlying alignment machinery, residues are
 * keyed by residue number within a receptor; multi-chain receptors
 * with duplicate numbering resolve to the first chain in structure
 * order.</p>
 */
public final class DefaultLigandContactAlignmentAnalyzer
        implements LigandContactAlignmentAnalyzer {

    private final NeedlemanWunschSequenceAligner sequenceAligner =
            new NeedlemanWunschSequenceAligner();

    @Override
    public LigandContactAlignment align(
            Structure receptorA,
            Ligand poseA,
            List<LigandContact> contactsA,
            Structure receptorB,
            Ligand poseB,
            List<LigandContact> contactsB,
            Pocket pocketA,
            Pocket pocketB
    ) {
        Objects.requireNonNull(receptorA, "receptorA");
        Objects.requireNonNull(poseA, "poseA");
        Objects.requireNonNull(contactsA, "contactsA");
        Objects.requireNonNull(receptorB, "receptorB");
        Objects.requireNonNull(poseB, "poseB");
        Objects.requireNonNull(contactsB, "contactsB");

        SequenceAlignment alignment = sequenceAligner.align(
                StructureSequences.sequenceResidues(receptorA),
                StructureSequences.sequenceResidues(receptorB)
        );

        Map<Integer, ResidueInfo> residuesA =
                residuesByNumber(receptorA);
        Map<Integer, ResidueInfo> residuesB =
                residuesByNumber(receptorB);
        Map<Integer, ContactInfo> contactIndexA =
                indexContacts(contactsA);
        Map<Integer, ContactInfo> contactIndexB =
                indexContacts(contactsB);
        Set<Integer> pocketNumbersA = pocketResidueNumbers(pocketA);
        Set<Integer> pocketNumbersB = pocketResidueNumbers(pocketB);

        List<AlignedLigandContact> rows = new ArrayList<>();

        int position = 0;

        for (AlignedResiduePair pair : alignment.pairs()) {
            position++;

            SubstitutionChemistry chemistry = SubstitutionChemistry
                    .between(
                            pair.queryResidueName(),
                            pair.candidateResidueName()
                    );

            ContactInfo contactA =
                    contactIndexA.get(pair.queryResidueNumber());
            ContactInfo contactB =
                    contactIndexB.get(pair.candidateResidueNumber());

            boolean hasContactA = contactA != null;
            boolean hasContactB = contactB != null;

            if (chemistry.identical() && !hasContactA && !hasContactB) {
                // No differential information at this position.
                continue;
            }

            Boolean memberA = pocketA == null
                    ? null
                    : pocketNumbersA.contains(pair.queryResidueNumber());
            Boolean memberB = pocketB == null
                    ? null
                    : pocketNumbersB.contains(
                            pair.candidateResidueNumber());

            ResidueInfo residueA =
                    residuesA.get(pair.queryResidueNumber());
            ResidueInfo residueB =
                    residuesB.get(pair.candidateResidueNumber());

            rows.add(new AlignedLigandContact(
                    position,
                    pair.queryResidueName(),
                    pair.candidateResidueName(),
                    residueA == null ? null : residueA.id(),
                    residueB == null ? null : residueB.id(),
                    hasContactA,
                    hasContactB,
                    contactA == null ? null : contactA.minDistance(),
                    contactB == null ? null : contactB.minDistance(),
                    contactA == null ? null : contactA.type(),
                    contactB == null ? null : contactB.type(),
                    memberA,
                    memberB,
                    chemistry.categoriesA(),
                    chemistry.categoriesB(),
                    chemistry.substitutionClass(),
                    chemistry.conservative(),
                    structuralEquivalence(memberA, memberB),
                    classify(
                            hasContactA,
                            hasContactB,
                            chemistry.identical()
                    )
            ));
        }

        appendUnmapped(
                rows,
                position,
                alignment,
                contactIndexA,
                contactIndexB,
                residuesA,
                residuesB,
                pocketA == null ? null : pocketNumbersA,
                pocketB == null ? null : pocketNumbersB
        );

        return new LigandContactAlignment(rows, alignment);
    }

    /**
     * Classifies one mapped alignment position from its contact flags
     * and residue identity.
     */
    public static DifferentialContactType classify(
            boolean contactA,
            boolean contactB,
            boolean identical
    ) {
        if (contactA && contactB) {
            return identical
                    ? DifferentialContactType.CONSERVED_CONTACT
                    : DifferentialContactType.CONTACT_BOTH_DIFFERENT_RESIDUE;
        }

        if (contactA) {
            return DifferentialContactType.A_ONLY_CONTACT;
        }

        if (contactB) {
            return DifferentialContactType.B_ONLY_CONTACT;
        }

        return DifferentialContactType.NONCONTACT_DIFFERENCE;
    }

    /**
     * Appends one {@link DifferentialContactType#UNMAPPED} row per
     * ligand-contact residue that the alignment did not map onto the
     * other receptor (gap). Unmapped rows follow the mapped rows in
     * residue-number order, A side first, with synthetic positions
     * continuing the mapped numbering.
     */
    private static void appendUnmapped(
            List<AlignedLigandContact> rows,
            int lastPosition,
            SequenceAlignment alignment,
            Map<Integer, ContactInfo> contactIndexA,
            Map<Integer, ContactInfo> contactIndexB,
            Map<Integer, ResidueInfo> residuesA,
            Map<Integer, ResidueInfo> residuesB,
            Set<Integer> pocketNumbersA,
            Set<Integer> pocketNumbersB
    ) {
        Set<Integer> mappedA = new TreeSet<>();
        Set<Integer> mappedB = new TreeSet<>();

        for (AlignedResiduePair pair : alignment.pairs()) {
            mappedA.add(pair.queryResidueNumber());
            mappedB.add(pair.candidateResidueNumber());
        }

        int position = lastPosition;

        for (int residueNumber : new TreeSet<>(contactIndexA.keySet())) {
            if (!mappedA.contains(residueNumber)) {
                position++;
                rows.add(unmappedRow(
                        position,
                        true,
                        residuesA.get(residueNumber),
                        contactIndexA.get(residueNumber),
                        pocketNumbersA,
                        residueNumber
                ));
            }
        }

        for (int residueNumber : new TreeSet<>(contactIndexB.keySet())) {
            if (!mappedB.contains(residueNumber)) {
                position++;
                rows.add(unmappedRow(
                        position,
                        false,
                        residuesB.get(residueNumber),
                        contactIndexB.get(residueNumber),
                        pocketNumbersB,
                        residueNumber
                ));
            }
        }
    }

    private static AlignedLigandContact unmappedRow(
            int position,
            boolean sideA,
            ResidueInfo residue,
            ContactInfo contact,
            Set<Integer> pocketNumbers,
            int residueNumber
    ) {
        String name = residue == null ? null : residue.name();
        ResidueId id = residue == null ? null : residue.id();
        Boolean member = pocketNumbers == null
                ? null
                : pocketNumbers.contains(residueNumber);
        Set<ResidueCategory> chemistry =
                residue == null
                        ? Set.of()
                        : SubstitutionChemistry.between(name, name)
                                .categoriesA();

        return new AlignedLigandContact(
                position,
                sideA ? name : null,
                sideA ? null : name,
                sideA ? id : null,
                sideA ? null : id,
                sideA,
                !sideA,
                sideA ? contact.minDistance() : null,
                sideA ? null : contact.minDistance(),
                sideA ? contact.type() : null,
                sideA ? null : contact.type(),
                sideA ? member : null,
                sideA ? null : member,
                sideA ? chemistry : Set.of(),
                sideA ? Set.of() : chemistry,
                null,
                false,
                null,
                DifferentialContactType.UNMAPPED
        );
    }

    private static Map<Integer, ResidueInfo> residuesByNumber(
            Structure receptor
    ) {
        Map<Integer, ResidueInfo> index = new LinkedHashMap<>();

        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                index.putIfAbsent(
                        residue.getNumber(),
                        new ResidueInfo(
                                new ResidueId(
                                        chain.id(),
                                        residue.getNumber(),
                                        residue.getInsertionCode()
                                ),
                                residue.getName()
                        )
                );
            }
        }

        return index;
    }

    private static Map<Integer, ContactInfo> indexContacts(
            List<LigandContact> contacts
    ) {
        Map<Integer, ContactInfo> index = new HashMap<>();

        for (LigandContact contact : contacts) {
            int residueNumber = contact.residue().residueNumber();
            ContactInfo existing = index.get(residueNumber);

            if (existing == null
                    || contact.distance() < existing.minDistance()) {
                index.put(residueNumber, new ContactInfo(
                        contact.distance(),
                        contact.type()
                ));
            }
        }

        return index;
    }

    private static Set<Integer> pocketResidueNumbers(Pocket pocket) {
        if (pocket == null) {
            return Set.of();
        }

        return pocket.residues().stream()
                .map(ResidueId::residueNumber)
                .collect(Collectors.toSet());
    }

    private static Boolean structuralEquivalence(
            Boolean memberA,
            Boolean memberB
    ) {
        if (memberA == null || memberB == null) {
            return null;
        }

        return memberA && memberB;
    }

    private record ResidueInfo(
            ResidueId id,
            String name
    ) {
    }

    private record ContactInfo(
            double minDistance,
            ContactType type
    ) {
    }
}
