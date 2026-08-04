package totah.lab.web.controller;

public record PocketCandidateResponse(
        long pocketId,
        long structureId,
        String sourceAccession,
        int pocketNumber,
        double descriptorDistance,
        double volumeDistance,
        double residueDistance,
        double chemistryDistance
) {
}
