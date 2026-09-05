package totah.lab.mettl7.triage;

import java.io.IOException;
import java.nio.file.Path;

/** Minimal framework-independent JSON command boundary for batch use. */
public final class Mettl7TriageCli {
    private Mettl7TriageCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: Mettl7TriageCli INPUT.json OUTPUT.json");
        }
        Mettl7TriageJsonCodec codec = new Mettl7TriageJsonCodec();
        Mettl7TriageResult result = new Mettl7LigandTriageService()
                .assess(codec.readInput(Path.of(args[0])));
        codec.writeResult(Path.of(args[1]), result);
        System.out.print(new Mettl7TriageReportRenderer().render(result));
    }
}
