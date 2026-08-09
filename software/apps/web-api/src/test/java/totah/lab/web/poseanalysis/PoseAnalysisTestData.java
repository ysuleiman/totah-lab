package totah.lab.web.poseanalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Synthetic PDBQT content for the pocket-assignment tests: receptor
 * files with one CA atom per residue and single-model pose files, in
 * the fixed-column format {@code PdbqtReader} accepts (the same format
 * {@code PoseContactProfileTest} uses).
 */
final class PoseAnalysisTestData {

    private PoseAnalysisTestData() {
    }

    /**
     * Receptor PDBQT with one CA atom per residue; {@code names},
     * {@code residueNumbers} and {@code positions} are parallel.
     */
    static String receptorPdbqt(
            String[] names,
            int[] residueNumbers,
            double[][] positions
    ) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            double[] position = positions[index];
            lines.add(atom(
                    index + 1,
                    "CA",
                    names[index],
                    "A",
                    residueNumbers[index],
                    position[0],
                    position[1],
                    position[2],
                    0.1,
                    "C"
            ));
        }
        return String.join("\n", lines) + "\n";
    }

    /** Single-model pose PDBQT with one carbon atom per position. */
    static String posePdbqt(double[][] positions) {
        List<String> lines = new ArrayList<>();
        lines.add("MODEL 1");
        lines.add("ROOT");
        for (int index = 0; index < positions.length; index++) {
            double[] position = positions[index];
            lines.add(atom(
                    index + 1,
                    "C" + (index + 1),
                    "UNL",
                    "L",
                    1,
                    position[0],
                    position[1],
                    position[2],
                    0.0,
                    "C"
            ));
        }
        lines.add("ENDROOT");
        lines.add("TORSDOF 0");
        lines.add("ENDMDL");
        return String.join("\n", lines) + "\n";
    }

    static String atom(
            int serial,
            String name,
            String residueName,
            String chain,
            int residueNumber,
            double x,
            double y,
            double z,
            double charge,
            String type
    ) {
        return String.format(
                Locale.ROOT,
                "ATOM  %5d %-4s %-3s %1s%4d    %8.3f%8.3f%8.3f"
                        + "  1.00  0.00    %+6.3f %-2s",
                serial, name, residueName, chain, residueNumber,
                x, y, z, charge, type
        );
    }
}
