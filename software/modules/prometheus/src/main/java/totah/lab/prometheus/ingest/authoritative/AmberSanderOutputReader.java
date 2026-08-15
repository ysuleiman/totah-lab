package totah.lab.prometheus.ingest.authoritative;

import totah.lab.prometheus.recovery.FieldSourceProvenance;
import totah.lab.prometheus.recovery.RecoveredField;
import totah.lab.prometheus.recovery.RecoveryClassification;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict parser for authoritative Amber SANDER output; no report/CSV fallback. */
public final class AmberSanderOutputReader {
    private static final Pattern BANNER = Pattern.compile("Amber\\s+(\\S+)\\s+SANDER");
    private static final Pattern EXECUTABLE = Pattern.compile("\\|\\s*Executable path:\\s*(.+)");
    private static final Pattern ASSIGNMENT = Pattern.compile("\\|?\\s*(MDIN|MDOUT|INPCRD|PARM|RESTRT|REFC|MDVEL|MDFRC|MDEN|MDCRD|MDINFO):\\s*(.+)");
    private static final Pattern KEY_VALUE = Pattern.compile("([a-z][a-z0-9]*)\\s*=\\s*([^,\\s]+)");
    private static final Pattern COMPONENT = Pattern.compile("(?<![A-Z0-9-])(BOND|ANGLE|DIHED|VDWAALS|EEL|1-4 VDW|1-4 EEL|RESTRAINT)\\s*=\\s*([-+0-9.Ee]+)");

    public AmberSanderResult read(Path mdout) throws IOException {
        ArtifactLines file = ArtifactLines.read(mdout);
        String software = null, executable = null;
        int softwareLine = -1, executableLine = -1, assignmentFirst = -1, assignmentLast = -1;
        Map<String, String> assignments = new LinkedHashMap<>();
        Map<String, String> controls = new LinkedHashMap<>();
        Map<String, Double> latest = new LinkedHashMap<>();
        int controlFirst = -1, controlLast = -1, energyFirst = -1, energyLast = -1;
        for (int i = 0; i < file.lines().size(); i++) {
            String line = file.lines().get(i);
            Matcher banner = BANNER.matcher(line);
            if (banner.find()) { software = "Amber " + banner.group(1) + " SANDER"; softwareLine = i; }
            Matcher executableMatcher = EXECUTABLE.matcher(line);
            if (executableMatcher.find()) { executable = executableMatcher.group(1).trim(); executableLine = i; }
            Matcher assignment = ASSIGNMENT.matcher(line);
            if (assignment.matches()) {
                assignments.put(assignment.group(1), assignment.group(2).trim());
                if (assignmentFirst < 0) assignmentFirst = i;
                assignmentLast = i;
            }
            Matcher kv = KEY_VALUE.matcher(line);
            while (kv.find()) {
                String key = kv.group(1).toLowerCase(Locale.ROOT);
                if (isScientificControl(key)) {
                    controls.put(key, kv.group(2));
                    if (controlFirst < 0) controlFirst = i;
                    controlLast = i;
                }
            }
            Matcher component = COMPONENT.matcher(line);
            boolean found = false;
            while (component.find()) { latest.put(component.group(1), Double.parseDouble(component.group(2))); found = true; }
            if (found) { if (line.contains("BOND")) energyFirst = i; energyLast = i; }
        }
        if (software == null || executable == null || latest.size() < 8) throw new IOException("Incomplete SANDER output: " + mdout);
        double total = latest.values().stream().mapToDouble(Double::doubleValue).sum();
        AmberEnergyComponents components = new AmberEnergyComponents(latest.get("BOND"), latest.get("ANGLE"),
                latest.get("DIHED"), latest.get("VDWAALS"), latest.get("EEL"), latest.get("1-4 VDW"),
                latest.get("1-4 EEL"), latest.get("RESTRAINT"), total);
        return new AmberSanderResult(raw("amber.software", software, file.line(softwareLine)),
                raw("amber.executable", executable, file.line(executableLine)),
                raw("amber.file_assignments", Map.copyOf(assignments), file.field("lines:" + (assignmentFirst + 1) + "-" + (assignmentLast + 1))),
                raw("amber.controls", Map.copyOf(controls), file.field("lines:" + (controlFirst + 1) + "-" + (controlLast + 1))),
                raw("amber.energy_components", components, file.field("lines:" + (energyFirst + 1) + "-" + (energyLast + 1) + ":last-energy-block")));
    }

    private static boolean isScientificControl(String key) {
        return switch (key) {
            case "imin", "maxcyc", "ncyc", "ntb", "igb", "cut", "ntpr", "dielc", "intdiel", "ntf", "ipol", "nmropt", "ntx", "irest" -> true;
            default -> false;
        };
    }

    private static <T> RecoveredField<T> raw(String name, T value, FieldSourceProvenance source) {
        return new RecoveredField<>(name, Optional.of(value), RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT,
                List.of(source), "parsed directly from the native SANDER mdout");
    }
}
