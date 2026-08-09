package totah.lab.hermes.file.pdbqt.vina;

public record VinaResult(
        double affinity,
        Double rmsdLowerBound,
        Double rmsdUpperBound
) {}
