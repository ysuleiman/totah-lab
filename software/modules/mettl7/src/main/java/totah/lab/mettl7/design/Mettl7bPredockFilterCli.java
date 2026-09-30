package totah.lab.mettl7.design;

import totah.lab.athena.design.grammar.DesignGrammarJsonCodec;
import totah.lab.athena.design.grammar.PredockGrammarEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/** Thin METTL7 configuration adapter around Athena's generic pre-docking engine. */
public final class Mettl7bPredockFilterCli {
    private Mettl7bPredockFilterCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 6) {
            throw new IllegalArgumentException("target.json scaffold.json counter.json rules.csv candidates.json output.json required");
        }
        Optional<Path> scaffold=args[1].equals("-")?Optional.empty():Optional.of(Path.of(args[1]));
        var grammar = new Mettl7bGrammarConfigurationLoader().load(
                Path.of(args[0]), scaffold, Path.of(args[2]), Path.of(args[3]));
        var codec = new DesignGrammarJsonCodec();
        var engine = new PredockGrammarEngine();
        var results = Arrays.stream(codec.readCandidates(Path.of(args[4])))
                .map(candidate -> engine.evaluate(grammar, candidate))
                .toList();
        codec.writeEvidence(Path.of(args[5]), results);
    }
}
