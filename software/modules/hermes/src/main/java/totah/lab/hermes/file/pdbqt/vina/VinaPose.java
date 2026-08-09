package totah.lab.hermes.file.pdbqt.vina;

import totah.lab.hermes.file.pdbqt.PdbqtModel;

public record VinaPose(
        PdbqtModel model,
        double affinity,
        Double rmsdLowerBound,
        Double rmsdUpperBound
) {}