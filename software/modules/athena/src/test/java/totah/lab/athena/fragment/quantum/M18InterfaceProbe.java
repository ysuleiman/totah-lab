package totah.lab.athena.fragment.quantum;

import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.athena.energy.MolecularState;
import totah.lab.athena.interaction.InteractionProfiler;
import totah.lab.gaia.structure.*;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;

/** Fixed control and frozen-fixture demonstration harness; never changes structural coordinates. */
public final class M18InterfaceProbe {
    private static final ObjectMapper JSON=new ObjectMapper().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[1]),cache=Path.of(args[2]);Files.createDirectories(out);
        if(args[0].equals("CONTROL")) {
            var rows=run(M18Fixtures.water(true),out,cache,false);
            if(rows.size()!=1)throw new AssertionError("Expected exactly one controlled water pair");
            var row=rows.getFirst();
            check(row.rhfCp().orElseThrow(),-.006835006266655341,1e-9);
            check(row.pbeCp().orElseThrow(),-.008408004694032911,1e-9);
            check(row.d3Delta().orElseThrow(),-6.232753352955552E-4,1e-12);
            check(row.pbeD3Cp().orElseThrow(),-.009031280029328466,1e-9);
            if(row.calculation().orElseThrow().components().stream().noneMatch(c->c.permutationReuse()))throw new AssertionError("Ghost AO permutation reuse not exercised");
            var water=M18Fixtures.water(true);
            var cappedEnvironment=M18Fixtures.environment(M18Fixtures.ethane(),water.state().ligand(),false,true,QuantumEnvironment.InteractionClass.dispersion);
            var capped=QuantumFragmentBuilder.receptorUnits(cappedEnvironment,List.of(M18Fixtures.RESIDUE),true,"methane-cap-control");
            if(capped.quantum().isEmpty())throw new AssertionError("Cap reconstruction unavailable");
            FragmentFeatureJson.write(out.resolve("cap-reconstruction.json"),FragmentFeatureJson.fragmentProvenance(capped));
            System.out.println("CONTROL_PASS");
        } else if(args[0].equals("METTL7")) {
            for(String system:List.of("7A","7B"))run(mettl7(system,out),out.resolve(system),cache,true);
            System.out.println("METTL7_SCHEMA_DEMONSTRATION_COMPLETE_NO_SELECTIVITY_INTERPRETATION");
        } else throw new IllegalArgumentException("Unknown probe mode");
    }
    private static void check(double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(actual+" != "+expected);}
    private static List<FragmentPhysicalFeatures> run(QuantumEnvironment environment,Path out,Path cache,boolean noQuantumExpected)throws Exception {
        Files.createDirectories(out);var selection=FragmentSelection.select(environment);
        FragmentFeatureJson.write(out.resolve("selection.json"),FragmentFeatureJson.selection(selection));
        var plans=FragmentCalculationPlan.pairs(environment,selection);var results=new ArrayList<FragmentPhysicalFeatures>();
        for(int i=0;i<plans.size();i++) {
            var row=FragmentFeatureService.calculate(plans.get(i),cache,x->{if(noQuantumExpected)throw new AssertionError("Ambiguous frozen fixture must not enter SCF");System.out.println(x);});
            results.add(row);FragmentFeatureJson.write(out.resolve(String.format(Locale.ROOT,"feature-%03d.json",i)),FragmentFeatureJson.feature(row));
        }
        FragmentFeatureJson.write(out.resolve("aggregates.json"),FragmentFeatureJson.aggregates(FragmentFeatureService.aggregate(results)));
        var counts=new TreeMap<String,Object>();counts.put("state_id",environment.state().stateId());counts.put("environment_hash",environment.identity());
        counts.put("source_provenance",environment.state().provenance());counts.put("plans",plans.size());
        counts.put("RHF_available",results.stream().filter(r->r.rhfCp().isPresent()).count());counts.put("PBE_available",results.stream().filter(r->r.pbeCp().isPresent()).count());
        counts.put("D3_available",results.stream().filter(r->r.d3Delta().isPresent()).count());counts.put("PBE_D3_available",results.stream().filter(r->r.pbeD3Cp().isPresent()).count());
        counts.put("OUT_OF_VALIDATED_DOMAIN",results.stream().filter(r->r.validity()==FragmentPhysicalFeatures.Validity.OUT_OF_VALIDATED_DOMAIN).count());
        counts.put("status","SCREENING_ONLY");
        Files.writeString(out.resolve("summary.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(counts)+"\n");
        return results;
    }
    private static QuantumEnvironment mettl7(String system,Path staging)throws Exception {
        var manifest=JSON.readTree(M18InterfaceProbe.class.getResourceAsStream("/quantum/m18/mettl7-demo-manifest.json"));
        String receptorName="stage12j/"+system+"_WT_SAM_BOUND.pdbqt",ligandName="stage12j/raw/"+system+"_WT_R_s1.pdbqt";
        var receptorFile=new PdbqtReader().read(fixture(receptorName,manifest,staging));
        var ligandFile=new PdbqtReader().read(fixture(ligandName,manifest,staging));
        var pose=ligandFile.models().stream().filter(m->m.modelNumber()==1).findFirst().orElseThrow();
        var receptor=PdbqtGaiaMapper.toStructure(receptorFile);
        var ligand=PdbqtGaiaMapper.toLigandWithMeekoTopology(pose,"DCMB_R_MODEL_1").structure();
        var profile=new InteractionProfiler().profile(receptor,ligand);
        var water=new HashSet<ResidueId>();var cofactor=new HashSet<ResidueId>();
        FragmentSelection.residues(receptor).forEach((id,residue)->{if(Set.of("HOH","WAT").contains(residue.getName()))water.add(id);if(Set.of("SAM","SAH").contains(residue.getName()))cofactor.add(id);});
        var provenance=new TreeMap<String,String>();provenance.put("receptor_fixture",receptorName);provenance.put("ligand_fixture",ligandName);
        provenance.put("receptor_sha256",manifest.get("fixtures").get(receptorName).get("sha256").asText());
        provenance.put("ligand_sha256",manifest.get("fixtures").get(ligandName).get("sha256").asText());
        provenance.put("model","1");provenance.put("seed","1");provenance.put("enantiomer","R");
        provenance.put("selection_basis","Frozen fixture identity only; no Aether values or selectivity outcomes");
        provenance.put("perception",profile.perception().toString());provenance.put("cofactor_identity",cofactor.isEmpty()?"NOT_PRESENT":"SAM/SAH source residue identity retained");
        var state=new MolecularState("M18_"+system+"_WT_DCMB_R_s1_MODEL1",system+"_WT_SAM_BOUND","DCMB_R",receptor,ligand,Optional.empty(),
                "PDBQT preparation retained; complete formal-charge and hydrogen attestation unavailable", "SOURCE_PARTIAL_CHARGES_RETAINED",
                "No force-field parameter changes","FIXED_GAS_PHASE_FRAGMENT_DESCRIPTOR","NO_COORDINATE_CHANGE",provenance);
        // Existing source connectivity is retained, but it is not an attestation of complete quantum chemistry.
        var environment=new QuantumEnvironment(state,profile.interactions(),List.of(),Map.of(),water,cofactor,true,true);
        var mapped=QuantumEnvironment.atoms(ligand).values().stream().toList();
        if(mapped.size()!=pose.atoms().size())throw new AssertionError("Ligand atom count changed");
        for(int i=0;i<mapped.size();i++) {
            var source=pose.atoms().get(i);var target=mapped.get(i);
            if(source.serial()!=target.getPdbSerial()||source.position().distanceSquared(target.getPosition())!=0)throw new AssertionError("Ligand atom order/coordinates changed");
        }
        return environment;
    }
    private static Path fixture(String name,com.fasterxml.jackson.databind.JsonNode manifest,Path staging)throws Exception {
        byte[] bytes;
        try(var in=M18InterfaceProbe.class.getResourceAsStream("/mettl7-v2-regression/"+name)){bytes=Objects.requireNonNull(in).readAllBytes();}
        if(!ContentHash.sha256(bytes).equals(manifest.get("fixtures").get(name).get("sha256").asText()))throw new AssertionError("Frozen fixture checksum mismatch: "+name);
        Path file=staging.resolve("inputs").resolve(name);Files.createDirectories(file.getParent());Files.write(file,bytes);return file;
    }
}
