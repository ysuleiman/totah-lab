package totah.lab.daedalus.system;

import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;
import static totah.lab.athena.system.SystemGraphCertificate.*;

/** Preserve first, interpret second. All durable records use the existing Mnemosyne catalog. */
public final class SystemQualificationPipeline {
    public static final ScientificReference METHOD=new ScientificReference(ScientificReference.Kind.METHOD,"daedalus.system","qualification-pipeline","1");
    private final SystemGraphValidation validation;
    public SystemQualificationPipeline(SystemGraphValidation validation){this.validation=Objects.requireNonNull(validation);}
    public record Published(SystemStateView state,SystemGraphCertificate certificate,EvidenceAdmission.Pin catalogSnapshot) { }

    public Published run(EvidenceSnapshotCatalog catalog,Optional<EvidenceAdmission.Pin> parent,
                         SystemStateView state,List<EvidenceEnvelope> observations,Map<String,String> configuration,
                         List<SystemGraphAnalyzer> analyzers,ScientificReference run,Instant at) throws IOException {
        run.require(ACTIVITY);observations=List.copyOf(observations);configuration=Collections.unmodifiableMap(new TreeMap<>(configuration));
        analyzers=analyzers.stream().sorted(Comparator.comparing(a->a.method().toString())).toList();
        if(analyzers.stream().map(SystemGraphAnalyzer::method).distinct().count()!=analyzers.size())throw new IllegalArgumentException("duplicate analyzer version");
        var history=parent.isPresent()?catalog.read(parent.orElseThrow()).orElseThrow(()->new IOException("missing parent")).history():new EvidenceHistory();
        for(var observation:observations)history=history.append(observation);
        var stateEnvelope=envelope(run,"state","athena:system-state",SystemStateView.bytes(state.snapshot()),METHOD,state.subject(),at,List.of("exact source graph snapshot; not scientific truth"));
        history=history.append(stateEnvelope);
        // This write/read-back completes BEFORE validation, analyzers or certificate creation.
        var inputSnapshot=persist(catalog,parent,history,run,"inputs",at);
        history=catalog.read(inputSnapshot).orElseThrow(()->new IOException("input snapshot disappeared")).history();
        // Evaluate only explicitly supplied inputs for this state; inherited evidence remains immutable.
        var requestedInputs=new LinkedHashMap<ScientificReference,EvidenceEnvelope>();
        for(var observation:observations)requestedInputs.put(observation.reference(),observation);
        requestedInputs.put(stateEnvelope.reference(),stateEnvelope);
        var retainedInputs=requestedInputs.values().stream().sorted(Comparator.comparing(e->e.reference().toString())).toList();
        var outputIds=new ArrayList<ScientificReference>();
        SystemGraphValidation.Result checked;
        try { checked=validation.validate(state); }
        catch(RuntimeException failure) {
            var failedCaps=new EnumMap<Capability,Qualification>(Capability.class);
            for(var c:Capability.values())failedCaps.put(c,new Qualification(c==Capability.ENERGETICS?Status.NOT_EVALUATED:Status.FAILED,List.of("validation failed: "+failure)));
            checked=new SystemGraphValidation.Result(failedCaps,List.of(new Check("VALIDATION","exception",Status.FAILED,failure.toString())));
            var validationFailure=interpretation(run,"validation-failure",List.of(stateEnvelope),METHOD,configuration,List.of(state.subject()),
                    EvidenceInterpretation.Status.FAILED,Map.of(),List.of(failure.toString()),List.of("input evidence already preserved"),at);
            history=history.append(validationFailure);outputIds.add(validationFailure.reference());
        }
        final var prerequisites=checked.capabilities();
        var caps=new EnumMap<Capability,Qualification>(Capability.class);caps.putAll(checked.capabilities());
        var versions=new ArrayList<ScientificReference>();
        versions.add(new ScientificReference(ScientificReference.Kind.METHOD,"athena.system",SystemGraphValidation.VERSION,"1"));
        for(var analyzer:analyzers)versions.add(analyzer.method());
        int serial=0;
        for(var source:retainedInputs)if(analyzers.stream().noneMatch(a->a.evidenceTypes().contains(source.evidenceType()))) {
            var unsupported=interpretation(run,"unsupported-"+serial++,List.of(source),METHOD,configuration,source.subjects(),
                    EvidenceInterpretation.Status.UNSUPPORTED,Map.of("evidenceType",source.evidenceType()),List.of("no registered analyzer for payload type; preserved unchanged"),List.of(),at);
            history=history.append(unsupported);outputIds.add(unsupported.reference());
        }
        for(var analyzer:analyzers) {
            var supported=retainedInputs.stream().filter(e->analyzer.evidenceTypes().contains(e.evidenceType())).toList();
            // Even an evaluator with no applicable input has an attributed unevaluated result, referencing the preserved state.
            var inputs=supported.isEmpty()?List.of(stateEnvelope):supported;
            List<SystemGraphAnalyzer.Finding> findings;
            try {
                if(supported.isEmpty() || analyzer.requires().stream().anyMatch(c->prerequisites.get(c).status()!=Status.QUALIFIED))
                    findings=List.of(new SystemGraphAnalyzer.Finding("not-evaluated",List.of(state.subject()),EvidenceInterpretation.Status.NOT_EVALUATED,
                            Map.of(),List.of("required qualified capability or supported input unavailable"),List.of("conditional capability is not automatically accepted")));
                else findings=List.copyOf(analyzer.analyze(state,inputs,configuration));
                if(findings.isEmpty())findings=List.of(new SystemGraphAnalyzer.Finding("no-claim",List.of(state.subject()),EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,
                        Map.of(),List.of("analyzer returned no explicit finding; not scientific absence"),List.of()));
                if(findings.stream().map(SystemGraphAnalyzer.Finding::id).distinct().count()!=findings.size())throw new IllegalArgumentException("duplicate finding IDs");
            }catch(Exception failed) {
                findings=List.of(new SystemGraphAnalyzer.Finding("failed",List.of(state.subject()),EvidenceInterpretation.Status.FAILED,
                        Map.of("exception",failed.getClass().getName()),List.of(String.valueOf(failed.getMessage())),List.of("input evidence and graph preserved before evaluator ran")));
            }
            for(var finding:findings) {
                if(finding.status()==EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE || finding.status()==EvidenceInterpretation.Status.UNSUPPORTED)
                    for(var c:analyzer.qualifies())if(caps.get(c).status()==Status.QUALIFIED)
                        caps.put(c,new Qualification(finding.status()==EvidenceInterpretation.Status.UNSUPPORTED?Status.UNSUPPORTED:Status.CONDITIONAL,List.of("analyzer limitations: "+analyzer.method())));
                var interpretation=interpretation(run,"finding-"+serial++,inputs,analyzer.method(),configuration,finding.subjects(),finding.status(),finding.measurements(),finding.reasons(),finding.limitations(),at);
                history=history.append(interpretation);outputIds.add(interpretation.reference());
                if(finding.status()==EvidenceInterpretation.Status.FAILED) for(var c:analyzer.qualifies())caps.put(c,new Qualification(Status.FAILED,List.of("analyzer failed: "+analyzer.method())));
                if(finding.status()==EvidenceInterpretation.Status.NOT_EVALUATED) for(var c:analyzer.qualifies()) {
                    if(caps.get(c).status()==Status.QUALIFIED)caps.put(c,new Qualification(Status.NOT_EVALUATED,List.of("analyzer could not evaluate required inputs")));
                }
            }
        }
        var analysisSnapshot=persist(catalog,Optional.of(inputSnapshot),history,run,"analysis",at);
        var certificate=new SystemGraphCertificate(ref(SUBJECT,run,"certificate"),state.binding(),SystemStateView.digest(configuration),inputSnapshot,
                versions,caps,checked.checks(),outputIds,state.limitations(),at);
        var certificateEnvelope=envelope(run,"certificate","athena:system-certificate",SystemStateView.bytes(certificate),METHOD,state.subject(),at,
                List.of("capability-specific; no global truth or binding-mode assertion"));
        history=history.append(certificateEnvelope);
        var published=persist(catalog,Optional.of(analysisSnapshot),history,run,"published",at);
        var read=catalog.read(published).orElseThrow(()->new IOException("published snapshot missing"));
        if(!Arrays.equals(read.history().envelopes().get(certificateEnvelope.reference()).readPayload(),SystemStateView.bytes(certificate)))
            throw new IOException("certificate read-back mismatch");
        return new Published(state,certificate,published);
    }
    private static EvidenceAdmission.Pin persist(EvidenceSnapshotCatalog catalog,Optional<EvidenceAdmission.Pin> parent,EvidenceHistory history,
                                                 ScientificReference run,String phase,Instant at)throws IOException {
        var exchange=new EvidenceExchange();var snapshot=exchange.snapshot(ref(SNAPSHOT,run,phase),run,at,parent.map(EvidenceAdmission.Pin::reference),history);
        var pin=new EvidenceAdmission.Pin(snapshot.manifest().reference(),EvidenceExchange.sha256(exchange.encode(snapshot)));
        var result=parent.isEmpty()?catalog.seed(snapshot,pin):catalog.append(snapshot,new EvidenceAdmission.Expectation(parent.orElseThrow(),pin));
        if(result.status()==EvidenceSnapshotCatalog.Status.CONFLICT)throw new IOException("snapshot publication conflict: "+result.reason());
        if(catalog.read(pin).isEmpty())throw new IOException("durable read-back missing");return pin;
    }
    private static EvidenceInterpretation interpretation(ScientificReference run,String id,List<EvidenceEnvelope> inputs,ScientificReference method,
                                                         Map<String,String> configuration,List<EvidenceSubject> subjects,EvidenceInterpretation.Status status,
                                                         Map<String,String> measurements,List<String> reasons,List<String> limitations,Instant at)throws IOException {
        var pins=new ArrayList<EvidenceInterpretation.Input>();var exchange=new EvidenceExchange();
        for(var input:inputs)pins.add(new EvidenceInterpretation.Input(input.reference(),exchange.contentDigest(input)));
        return new EvidenceInterpretation(ref(EVIDENCE_INTERPRETATION,run,id),pins,method,configuration,subjects,status,measurements,reasons,limitations,Optional.empty(),at);
    }
    public static EvidenceEnvelope envelope(ScientificReference run,String id,String type,byte[] payload,ScientificReference method,
                                             EvidenceSubject subject,Instant at,List<String> limitations) {
        String hash=EvidenceExchange.sha256(payload);
        return new EvidenceEnvelope(ref(EVIDENCE_ENVELOPE,run,id),type,"application/octet-stream","1",Optional.of(Base64.getEncoder().encodeToString(payload)),Optional.empty(),hash,
                new Observation.Provenance(ref(SOURCE,run,id),new ScientificReference(ARTIFACT,"sha256",hash,"1"),ref(RECEIPT,run,id),method,"inline exact bytes",List.of()),
                method,new ScientificReference(CONTEXT,run.namespace(),run.id(),run.version()),List.of(subject),List.of("engineering qualification; no biological inference"),limitations,at);
    }
    private static ScientificReference ref(ScientificReference.Kind kind,ScientificReference run,String suffix){return new ScientificReference(kind,run.namespace(),run.id()+"/"+suffix,run.version());}
}
