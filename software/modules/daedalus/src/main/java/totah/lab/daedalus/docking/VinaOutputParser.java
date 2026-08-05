package totah.lab.daedalus.docking;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the pose table of AutoDock Vina standard output. */
final class VinaOutputParser {

    private static final Pattern POSE_ROW = Pattern.compile(
            "^\\s*(\\d+)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)"
                    + "\\s+(-?\\d+(?:\\.\\d+)?)\\s*$");

    private VinaOutputParser() {
    }

    static List<VinaPose> parse(String output) {
        List<VinaPose> poses = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return poses;
        }
        for (String line : output.split("\\R")) {
            Matcher matcher = POSE_ROW.matcher(line);
            if (matcher.matches()) {
                poses.add(new VinaPose(
                        Integer.parseInt(matcher.group(1)),
                        Double.parseDouble(matcher.group(2)),
                        Double.parseDouble(matcher.group(3)),
                        Double.parseDouble(matcher.group(4))));
            }
        }
        return List.copyOf(poses);
    }
}
