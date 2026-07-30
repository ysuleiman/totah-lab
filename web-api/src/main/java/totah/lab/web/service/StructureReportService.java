package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public final class StructureReportService {

    public static final double STRONG_CONTACT_CUTOFF = 4.0;

    private final StructureService structureService;
    private final PocketService pocketService;

    public StructureReportService(
            StructureService structureService,
            PocketService pocketService
    ) {
        this.structureService = structureService;
        this.pocketService = pocketService;
    }

    @Transactional(readOnly = true)
    public StructureReport generate(long structureId) {
        StructureService.StructureDetails structure =
                structureService.getStructure(structureId);
        PocketService.PocketDetails chosenPocket =
                chosenPocket(structure);
        List<ReportResidue> chosenResidues = chosenPocket == null
                ? List.of()
                : chosenPocket.residues().stream()
                        .map(this::toReportResidue)
                        .sorted(RESIDUE_ORDER)
                        .toList();
        List<LigandEvidence> ligandEvidence = pocketService
                .getPocketsForStructure(structureId)
                .stream()
                .filter(pocket -> pocket.evidence() != null)
                .map(this::toLigandEvidence)
                .toList();

        return new StructureReport(
                structure.id(),
                reportTitle(structure),
                Instant.now(),
                structure.receptor().uniProtId(),
                structure.receptor().geneName(),
                structure.receptor().proteinName(),
                chosenPocket == null ? null : toPocketSummary(chosenPocket),
                chosenResidues,
                ligandEvidence,
                narrative(chosenPocket, ligandEvidence)
        );
    }

    private PocketService.PocketDetails chosenPocket(
            StructureService.StructureDetails structure
    ) {
        if (structure.chosenPocket() == null) {
            return null;
        }
        return pocketService.getPocket(structure.chosenPocket().id());
    }

    private LigandEvidence toLigandEvidence(
            PocketService.PocketSummary pocket
    ) {
        PocketService.PocketEvidence evidence = pocket.evidence();
        List<ContactResidue> contacts = evidence.residueEvidence().stream()
                .map(this::toContactResidue)
                .sorted(Comparator.comparing(
                                ContactResidue::chain
                        ).thenComparingInt(ContactResidue::residueNumber))
                .toList();
        long strongCount = contacts.stream()
                .filter(contact ->
                        contact.minimumDistance()
                                <= STRONG_CONTACT_CUTOFF
                )
                .count();
        long nearCount = contacts.stream()
                .filter(contact ->
                        contact.minimumDistance()
                                > STRONG_CONTACT_CUTOFF
                                && contact.directContact()
                )
                .count();
        long outsideDirectCount = contacts.stream()
                .filter(ContactResidue::directContact)
                .filter(contact -> !contact.chosenPocketMember())
                .count();
        return new LigandEvidence(
                evidence.ligandCcd(),
                evidence.model(),
                evidence.ptm(),
                evidence.interfacePtm(),
                STRONG_CONTACT_CUTOFF,
                evidence.directContactCutoff(),
                evidence.shellCutoff(),
                Math.toIntExact(strongCount),
                Math.toIntExact(nearCount),
                evidence.directContactResidueCount(),
                evidence.shellResidueCount(),
                evidence.directChosenPocketOverlapCount(),
                Math.toIntExact(outsideDirectCount),
                contacts
        );
    }

    private ContactResidue toContactResidue(
            PocketService.PocketResidueEvidence residue
    ) {
        return new ContactResidue(
                residue.residueId(),
                residue.chain(),
                residue.residueNumber(),
                residue.residueName(),
                oneLetter(residue.residueName()),
                residue.minimumDistance(),
                residue.contactingAtomPairCount(),
                classification(
                        residue.minimumDistance(),
                        residue.directContact()
                ),
                residue.directContact(),
                residue.chosenPocketMember()
        );
    }

    private String classification(double distance, boolean directContact) {
        if (distance <= STRONG_CONTACT_CUTOFF) {
            return "STRONG";
        }
        if (directContact) {
            return "NEAR";
        }
        return "CONTEXT";
    }

    private ReportResidue toReportResidue(
            PocketService.ResidueDetails residue
    ) {
        return new ReportResidue(
                residue.id(),
                residue.chain(),
                residue.residueNumber(),
                residue.insertionCode(),
                residue.residueName(),
                oneLetter(residue.residueName())
        );
    }

    private PocketSummary toPocketSummary(
            PocketService.PocketDetails pocket
    ) {
        return new PocketSummary(
                pocket.id(),
                pocket.source(),
                pocket.pocketNumber(),
                pocket.score(),
                pocket.druggabilityScore(),
                pocket.volume(),
                pocket.residues().size()
        );
    }

    private String narrative(
            PocketService.PocketDetails chosenPocket,
            List<LigandEvidence> ligandEvidence
    ) {
        if (chosenPocket == null) {
            return "No chosen pocket is stored for this structure.";
        }
        StringBuilder report = new StringBuilder();
        report.append("The chosen site is ")
                .append(chosenPocket.source())
                .append(' ')
                .append(chosenPocket.pocketNumber())
                .append(" with ")
                .append(chosenPocket.residues().size())
                .append(" residues.");
        for (LigandEvidence evidence : ligandEvidence) {
            report.append(' ')
                    .append(evidence.ligandCcd())
                    .append(" has ")
                    .append(evidence.strongContactCount())
                    .append(" strong contacts within ")
                    .append(formatDistance(evidence.strongContactCutoff()))
                    .append(" Å and ")
                    .append(evidence.nearContactCount())
                    .append(" near contacts between ")
                    .append(formatDistance(evidence.strongContactCutoff()))
                    .append(" and ")
                    .append(formatDistance(evidence.directContactCutoff()))
                    .append(" Å. ")
                    .append(evidence.outsideDirectContactCount())
                    .append(" direct contacts lie outside the chosen pocket.");
        }
        report.append(" The original pocket membership is unchanged.");
        return report.toString();
    }

    private String reportTitle(
            StructureService.StructureDetails structure
    ) {
        String name = structure.receptor().proteinName();
        if (name == null || name.isBlank()) {
            name = structure.receptor().targetName();
        }
        return name + " structure report";
    }

    private String formatDistance(double distance) {
        return String.format(Locale.ROOT, "%.1f", distance);
    }

    private String oneLetter(String residueName) {
        return switch (residueName.toUpperCase(Locale.ROOT)) {
            case "ALA" -> "A";
            case "ARG" -> "R";
            case "ASN" -> "N";
            case "ASP" -> "D";
            case "CYS" -> "C";
            case "GLN" -> "Q";
            case "GLU" -> "E";
            case "GLY" -> "G";
            case "HIS" -> "H";
            case "ILE" -> "I";
            case "LEU" -> "L";
            case "LYS" -> "K";
            case "MET" -> "M";
            case "PHE" -> "F";
            case "PRO" -> "P";
            case "SER" -> "S";
            case "THR" -> "T";
            case "TRP" -> "W";
            case "TYR" -> "Y";
            case "VAL" -> "V";
            default -> "X";
        };
    }

    private static final Comparator<ReportResidue> RESIDUE_ORDER =
            Comparator.comparing(ReportResidue::chain)
                    .thenComparingInt(ReportResidue::residueNumber)
                    .thenComparing(ReportResidue::insertionCode);

    public record StructureReport(
            long structureId,
            String title,
            Instant generatedAt,
            String uniProtId,
            String geneName,
            String proteinName,
            PocketSummary chosenPocket,
            List<ReportResidue> chosenPocketResidues,
            List<LigandEvidence> ligandEvidence,
            String narrative
    ) {
        public StructureReport {
            chosenPocketResidues = List.copyOf(chosenPocketResidues);
            ligandEvidence = List.copyOf(ligandEvidence);
        }
    }

    public record PocketSummary(
            long id,
            String source,
            int pocketNumber,
            Double score,
            Double druggabilityScore,
            Double volume,
            int residueCount
    ) {
    }

    public record ReportResidue(
            long id,
            String chain,
            int residueNumber,
            String insertionCode,
            String residueName,
            String oneLetterCode
    ) {
    }

    public record LigandEvidence(
            String ligandCcd,
            String model,
            Double ptm,
            Double interfacePtm,
            double strongContactCutoff,
            double directContactCutoff,
            double contextCutoff,
            int strongContactCount,
            int nearContactCount,
            int directContactCount,
            int contextResidueCount,
            int directChosenPocketOverlapCount,
            int outsideDirectContactCount,
            List<ContactResidue> residues
    ) {
        public LigandEvidence {
            residues = List.copyOf(residues);
        }
    }

    public record ContactResidue(
            long id,
            String chain,
            int residueNumber,
            String residueName,
            String oneLetterCode,
            double minimumDistance,
            int contactingAtomPairCount,
            String classification,
            boolean directContact,
            boolean chosenPocketMember
    ) {
    }
}
