package totah.lab.athena.design.backend.ocl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import totah.lab.athena.design.knowledge.*;
import totah.lab.athena.design.knowledge.ContextualTransformationEffects.*;
import totah.lab.athena.design.knowledge.MatchedPairExtractor.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.junit.jupiter.api.Assertions.*;

/** A bounded empirical diagnostic, not a claim of predictive qualification or METTL7 applicability. */
class ContextualMmpBenchmarkTest {
    @Test void publishedAssaySubsetFlowsThroughExtractionEffectsAndLeakageSafeHoldout() throws Exception {
        var backend=new OclMolecularBackend(); var sources=new TreeMap<String,Source>();
        var measurements=new ArrayList<Measurement>(); var rejected=new TreeMap<String,String>();
        int rawRows=0;
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/mmp/chembl32-maximal-CHEMBL3714130.csv.gz")));
            var reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
            assertEquals("assay_chembl_id,compound_chembl_id,canonical_smiles,pchembl_value,assay_conditions_hash",reader.readLine());
            String line;
            while((line=reader.readLine())!=null) {
                rawRows++; var fields=line.split(",",-1); assertEquals(5,fields.length);
                String assay=fields[0], id=fields[1], reference="published-chembl32:"+assay+":"+id;
                if(rejected.containsKey(id)) continue;
                try {
                    var graph=backend.decodeStructure("SMILES",fields[2]);
                    var previous=sources.get(id); var refs=new ArrayList<String>();
                    if(previous!=null) { assertEquals(previous.graph(),graph); refs.addAll(previous.observationReferences()); }
                    refs.add(reference);
                    sources.put(id,new Source(id,"ChEMBL32/Jnelen/0a77cdd28abf40a1562aadce727d032cd668815c",graph,refs));
                    String identity=backend.identify(graph).canonicalKey(); double p=Double.parseDouble(fields[3]);
                    var context=new Context("CHEMBL3714130","IC50","published maximal-curation assay",assay,
                            fields[4],"dimensionless","pIC50","published-maximal-curation/same-assay-only;original-study-not-resolved");
                    measurements.add(new Measurement(reference,identity,context,"UNRESOLVED:"+assay,reference,p,p,"="));
                } catch(totah.lab.athena.design.backend.MolecularBackendException error) {
                    rejected.put(id,error.getMessage());
                }
            }
        }
        assertEquals(116,rawRows); assertEquals(29,sources.size()+rejected.size());
        var extraction=new OclMatchedPairExtractor().extract(new ArrayList<>(sources.values()),8);
        var algorithm=new ContextualTransformationEffects();
        var analysis=algorithm.analyze(extraction.pairs(),measurements,0.3);
        assertFalse(extraction.pairs().isEmpty(),extraction.issues().toString());
        assertFalse(analysis.effects().isEmpty());
        var predictions=new ArrayList<Map<String,Object>>();
        double unconditionalError=0,contextError=0,pairedUnconditionalError=0,zeroError=0,pairedZeroError=0;
        int unconditionalCount=0,contextCount=0,disagreements=0;
        for(var held:analysis.effects()) {
            var identities=Set.of(held.left().moleculeIdentity(),held.right().moleculeIdentity());
            var train=analysis.effects().stream().filter(e->!identities.contains(e.left().moleculeIdentity())
                    && !identities.contains(e.right().moleculeIdentity())).toList();
            var global=algorithm.summarize(train,held.pair().transformation(),held.left().context(),Optional.empty());
            var local=algorithm.summarize(train,held.pair().transformation(),held.left().context(),Optional.of(held.pair().leftFragment().chemicalContext()));
            assertEquals(0,global.studyCount(),"fixture must not invent independent experimental studies");
            var row=new LinkedHashMap<String,Object>();
            row.put("left",held.left().reference());row.put("right",held.right().reference());
            row.put("transformation",held.pair().transformation());row.put("context",held.pair().leftFragment().chemicalContext());
            row.put("actualDelta",held.lower());row.put("trainingPairCount",global.pairCount());row.put("contextTrainingPairCount",local.pairCount());
            row.put("unconditional",global.mean().isPresent()?global.mean().getAsDouble():null);
            row.put("contextual",local.mean().isPresent()?local.mean().getAsDouble():null);
            if(global.mean().isPresent()) { unconditionalCount++;unconditionalError+=Math.abs(global.mean().getAsDouble()-held.lower()); }
            if(local.mean().isPresent()) {
                contextCount++;contextError+=Math.abs(local.mean().getAsDouble()-held.lower());
                pairedUnconditionalError+=Math.abs(global.mean().orElseThrow()-held.lower());pairedZeroError+=Math.abs(held.lower());
                if(Math.signum(local.mean().getAsDouble())!=Math.signum(held.lower()))disagreements++;
            }
            zeroError+=Math.abs(held.lower()); predictions.add(row);
        }
        var report=new LinkedHashMap<String,Object>();
        report.put("status","EXPLORATORY_NOT_PRODUCTION_QUALIFIED"); report.put("rawRows",rawRows);report.put("supportedMolecules",sources.size());
        report.put("rejectedMolecules",rejected);report.put("structuralPairPaths",extraction.pairs().size());report.put("extractionIssues",extraction.issues());
        report.put("effects",analysis.effects().size());report.put("comparabilityRejections",analysis.rejections().size());
        report.put("holdout","exclude BOTH held-out chemical identities from ALL training effects; exact assay context only");
        report.put("unconditionalPredictions",unconditionalCount);report.put("contextualPredictions",contextCount);
        report.put("unconditionalMAE",unconditionalCount==0?null:unconditionalError/unconditionalCount);
        report.put("contextualMAE",contextCount==0?null:contextError/contextCount);
        report.put("unconditionalMAEOnContextCovered",contextCount==0?null:pairedUnconditionalError/contextCount);
        report.put("zeroChangeMAEOnContextCovered",contextCount==0?null:pairedZeroError/contextCount);
        report.put("zeroChangeMAEAll",zeroError/analysis.effects().size());report.put("contextSignDisagreements",disagreements);
        report.put("predictions",predictions);
        Files.createDirectories(Path.of("target"));
        try(var output=Files.newOutputStream(Path.of("target/mmp-benchmark.json"))) {
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output,report);
        }
        System.out.println("MMP_BENCHMARK sources="+sources.size()+" rejected="+rejected.size()+" pairs="+extraction.pairs().size()+" effects="+analysis.effects().size()+" predictions="+contextCount+"/"+analysis.effects().size());
    }
}
