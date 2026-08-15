package totah.lab.prometheus.ingest;

import java.util.Objects;

import totah.lab.prometheus.evidence.QmProtocol;

/**
 * Derives a {@link QmProtocol} from an archive method string such as
 * {@code "PBE-D3(BJ)/def2-SVP density-fitted gas phase"},
 * {@code "CP-PBE0-D3(BJ)/def2-TZVP"} or {@code "HF/6-31G(d)"}. Everything is
 * parsed from the string: a leading {@code CP-} marks counterpoise, the token
 * after {@code /} is the basis, {@code D3(BJ)} is detected as dispersion, and
 * a trailing {@code gas phase} marker sets the environment.
 */
final class QmProtocolParser {

    private QmProtocolParser() {
    }

    static QmProtocol fromMethodString(String methodString, String software, String softwareVersion) {
        Objects.requireNonNull(methodString, "methodString");
        Objects.requireNonNull(software, "software");
        Objects.requireNonNull(softwareVersion, "softwareVersion");

        String working = methodString.trim();
        if (working.equalsIgnoreCase("N/A") || working.equalsIgnoreCase("NA")) {
            return new QmProtocol(
                    "unknown", "none", "none", "none", false, software, softwareVersion);
        }
        boolean counterpoise = working.startsWith("CP-");
        if (counterpoise) {
            working = working.substring(3);
        }

        String basis = "none";
        int slash = working.indexOf('/');
        if (slash >= 0) {
            String after = working.substring(slash + 1).trim();
            int space = firstWhitespace(after);
            basis = space >= 0 ? after.substring(0, space) : after;
            working = working.substring(0, slash);
        }

        String dispersion = "none";
        if (working.contains("D3(BJ)")) {
            dispersion = "D3(BJ)";
        } else if (working.contains("D3")) {
            dispersion = "D3";
        }

        String environment = "none";
        if (methodString.contains("gas phase")) {
            environment = "gas";
        }

        String method = working
                .replace("-D3(BJ)", "")
                .replace("-D3", "")
                .trim();
        int space = firstWhitespace(method);
        if (space >= 0) {
            method = method.substring(0, space);
        }
        if (method.isBlank()) {
            throw new IllegalArgumentException("no method parseable from: " + methodString);
        }
        return new QmProtocol(
                method,
                basis.isBlank() ? "none" : basis,
                dispersion,
                environment,
                counterpoise,
                software,
                softwareVersion);
    }

    private static int firstWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
