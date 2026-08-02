package totah.lab.daedalus.docking.importer;

public record ChemflowImportResult(
        int runs,
        int poses,
        int contacts,
        int rejectedPoses
) {
}
