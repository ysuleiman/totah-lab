package totah.lab.mettl7.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.athena.design.generation.GrammarDrivenCandidateGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/** Audits whether the frozen METTL7 grammar is executable by the generic generator. */
public final class Mettl7bGenerationReadinessCli {
    private Mettl7bGenerationReadinessCli() {}

    public static void main(String[] args) throws IOException {
        if(args.length!=5)throw new IllegalArgumentException("target scaffold counter rules output required");
        var grammar=new Mettl7bGrammarConfigurationLoader().load(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]),Path.of(args[3]));
        var readiness=new GrammarDrivenCandidateGenerator().inspect(grammar);
        var receipt=new LinkedHashMap<String,Object>();
        receipt.put("grammar_id",grammar.id());receipt.put("generation_ready",readiness.ready());receipt.put("blockers",readiness.blockers());
        receipt.put("editable_vector_count",grammar.editableVectors().size());receipt.put("analogs_generated",0);receipt.put("docking_run",false);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[4]).toFile(),receipt);
    }
}
