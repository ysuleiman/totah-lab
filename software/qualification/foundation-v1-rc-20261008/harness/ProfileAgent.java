package foundation.harness;

import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Optional measurement-only agent; not used for scientific qualification or production. */
public final class ProfileAgent {
    public static final ConcurrentMap<String, Stats> totals = new ConcurrentHashMap<>();
    public static final ThreadLocal<ArrayDeque<Span>> stack = ThreadLocal.withInitial(ArrayDeque::new);
    public static final class Stats { public final LongAdder calls=new LongAdder(), inclusive=new LongAdder(), exclusive=new LongAdder(); }
    public static final class Span { public final String name; public final long start=System.nanoTime(); public long child; public Span(String n){name=n;} }
    public static void enter(String n){stack.get().push(new Span(n));}
    public static void exit(){var s=stack.get().pop();long elapsed=System.nanoTime()-s.start;var v=totals.computeIfAbsent(s.name,k->new Stats());v.calls.increment();v.inclusive.add(elapsed);v.exclusive.add(elapsed-s.child);if(!stack.get().isEmpty())stack.get().peek().child+=elapsed;}
    public static class MethodTiming {
        @Advice.OnMethodEnter public static void enter(@Advice.Origin("#t.#m") String n){ProfileAgent.enter(n);}
        @Advice.OnMethodExit(onThrowable=Throwable.class) public static void exit(){ProfileAgent.exit();}
    }
    public static class ConstructorTiming {
        @Advice.OnMethodEnter public static void enter(@Advice.Origin("#t.#m") String n){ProfileAgent.enter(n);}
        @Advice.OnMethodExit public static void exit(){ProfileAgent.exit();}
    }
    public static void premain(String output, Instrumentation instrumentation){
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try{
            var lines=new ArrayList<String>();lines.add("operation\tcalls\tinclusive_seconds\texclusive_seconds");
            totals.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{var v=e.getValue();lines.add(e.getKey()+"\t"+v.calls.sum()+"\t"+v.inclusive.sum()/1e9+"\t"+v.exclusive.sum()/1e9);});Files.write(Path.of(output),lines);
        }catch(Exception e){throw new RuntimeException(e);}}));
        new AgentBuilder.Default().type(nameStartsWith("totah.lab.").and(nameMatches(".*(EvidenceExchange|EvidenceSnapshotCatalog|PdbqtReader|PdbqtGaiaMapper|OclMolecularBackend|RuleRegistry|FoundationV1ConsumerAcceptanceTest|ResearchCodec|ResearchGate|SystemStateView|SystemGraphValidation|OclOccurrenceMatcher|OclSubstructureMatcher|ContinuousGeometryRules|S1Qualification|S1ResearchFixtures|ResearchV2Fixtures|DirectAssessmentResearchFixture|CurrentRuleExecution|CurrentRuleExecutionV2|RuleExecutionPipeline|SystemQualificationPipeline|ClPheAcceptanceTest).*")))
            .transform((builder,type,loader,module,domain)->builder.visit(Advice.to(MethodTiming.class).on(isMethod().and(not(isAbstract())).and(not(isNative())).and(not(nameMatches("sorted|lambda.*|toString|equals|hashCode|access.*")))))
                .visit(Advice.to(ConstructorTiming.class).on(isConstructor().and(isDeclaredBy(named("totah.lab.mnemosyne.EvidenceExchange"))))))
            .installOn(instrumentation);
    }
}
