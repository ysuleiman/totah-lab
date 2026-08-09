package totah.lab.hermes.file.pdbqt.vina;

import totah.lab.hermes.file.pdbqt.PdbqtRemarkParser;

import java.util.List;
import java.util.Optional;

public final class VinaResultParser implements PdbqtRemarkParser<VinaResult> {

    private static final String MARKER = "VINA RESULT:";

    public Optional<VinaResult> parse(List<String> remarks) {
        for (String remark : remarks) {
            int index = remark.indexOf(MARKER);

            if (index < 0) {
                continue;
            }

            String tail = remark
                    .substring(index + MARKER.length())
                    .trim();

            String[] tokens = tail.split("\\s+");

            if (tokens.length < 1) {
                continue;
            }

            try {
                double affinity = Double.parseDouble(tokens[0]);

                Double rmsdLower = tokens.length >= 2
                        ? Double.parseDouble(tokens[1])
                        : null;

                Double rmsdUpper = tokens.length >= 3
                        ? Double.parseDouble(tokens[2])
                        : null;

                return Optional.of(
                        new VinaResult(
                                affinity,
                                rmsdLower,
                                rmsdUpper
                        )
                );

            } catch (NumberFormatException ignored) {
                // Not a valid Vina result remark.
            }
        }

        return Optional.empty();
    }
}
