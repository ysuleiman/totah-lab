package totah.lab.mettl7.campaign.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import totah.lab.athena.clash.StericClashAnalysis;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.PdbqtWriteOptions;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.pdbqt.writer.PdbqtWriter;
import totah.lab.proteus.protein.mutation.rotamer.RotamerEvaluator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded six-state Phe-199 integrity resolution for B3 and B5 only. */
public final class Mettl7G199fIntegrityValidation {
    private static final List<State> STATES = states();
    private static final Set<String> BACKBONE=Set.of("N","CA","C","O","OXT","H","HA","HA2","HA3");
    private Mettl7G199fIntegrityValidation() {}

    private static List<State> states() {
        List<State> result = new ArrayList<>();
        for (double chi1 : List.of(60.0, 180.0, -60.0)) {
            for (double chi2 : List.of(0.0, 60.0, 90.0, 180.0, -60.0, -90.0)) {
                result.add(new State("chi1_" + angleId(chi1) + "_chi2_" + angleId(chi2), chi1, chi2));
            }
        }
        return List.copyOf(result);
    }

    private static String angleId(double angle) {
        return (angle >= 0 ? "plus" : "minus") + (int) Math.abs(angle);
    }

    public static void main(String[] args) throws IOException {
        if(args.length!=1)throw new IllegalArgumentException("Usage: <receptor-directory>");
        Path directory=Path.of(args[0]).toAbsolutePath().normalize();
        Path candidatesDirectory=directory.resolve("g199f_candidates"); Files.createDirectories(candidatesDirectory);
        PdbqtReader reader=new PdbqtReader(); PdbqtWriter writer=new PdbqtWriter();
        List<Map<String,Object>> metrics=new ArrayList<>(), selections=new ArrayList<>();
        for(String id:List.of("B3","B5")) {
            Path input=directory.resolve(id+"_SAM_BOUND.pdbqt"); Structure source=PdbqtGaiaMapper.toStructure(reader.read(input));
            LocatedResidue phe=locate(source); List<Candidate> candidates=new ArrayList<>();
            for(State state:STATES) {
                // Existing Proteus G199F is gauche-minus chi1 with template ring plane (chi2 zero delta).
                Structure candidate=Mettl7S47yIntegrityValidation.rotateAromatic(source,phe.chain(),phe.residue(),
                        Math.toRadians(state.chi1()+60.0),Math.toRadians(state.chi2()));
                LocatedResidue cp=locate(candidate);
                List<AtomReference> scope=cp.residue().getAtoms().stream().filter(a->!BACKBONE.contains(a.getName()))
                        .map(a->reference(cp.chain(),cp.residue(),a)).toList();
                var clashes=StericClashAnalysis.findClashes(candidate,scope,new StericClashAnalysis.Options(0.70));
                double score=new RotamerEvaluator().score(candidate,new totah.lab.gaia.structure.ResidueId(cp.chain(),199,cp.residue().getInsertionCode()),cp.residue().getAtoms());
                Path output=candidatesDirectory.resolve(id+"_"+state.id()+".pdbqt");
                writer.write(PdbqtGaiaMapper.fromStructure(candidate),output,PdbqtWriteOptions.defaults());
                Candidate c=new Candidate(state.id(),score,clashes.size(),output,sha256(output)); candidates.add(c);
                Map<String,Object> row=new LinkedHashMap<>(); row.put("receptor_id",id);row.put("rotamer",c.id());row.put("steric_score",score);
                row.put("severe_clash_count",clashes.size());row.put("severe_clashes",clashes.stream().map(x->Map.of("first",x.first().toString(),"second",x.second().toString(),"distance_A",x.distance(),"overlap_A",x.overlapAmount())).toList());
                row.put("candidate",output.toString());row.put("sha256",c.sha());metrics.add(row);
            }
            Candidate selected=candidates.stream().filter(c->c.clashes()==0).min(Comparator.comparingDouble(Candidate::score)).orElse(null);
            Map<String,Object> row=new LinkedHashMap<>();row.put("receptor_id",id);row.put("status",selected==null?"TECHNICAL_FAILURE":"PASS");
            row.put("selected_rotamer",selected==null?null:selected.id());row.put("selected_candidate",selected==null?null:selected.path().toString());row.put("selected_sha256",selected==null?null:selected.sha());
            if(selected!=null){Path original=directory.resolve(id+"_SAM_BOUND_PROTEUS_ORIGINAL.pdbqt");if(!Files.exists(original))Files.copy(input,original);Files.copy(selected.path(),input,StandardCopyOption.REPLACE_EXISTING);row.put("production_path",input.toString());row.put("production_sha256",sha256(input));row.put("original_path",original.toString());row.put("original_sha256",sha256(original));}
            selections.add(row);
        }
        ObjectMapper mapper=new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);mapper.writeValue(directory.resolve("g199f_candidate_metrics.json").toFile(),metrics);mapper.writeValue(directory.resolve("g199f_integrity_selections.json").toFile(),selections);
    }
    private static LocatedResidue locate(Structure s){List<LocatedResidue> f=new ArrayList<>();for(Chain c:s.getChains())for(Residue r:c.residues())if(r.getNumber()==199&&r.getName().equalsIgnoreCase("PHE"))f.add(new LocatedResidue(c.id(),r));if(f.size()!=1)throw new IllegalStateException("Expected one PHE199, found "+f.size());return f.getFirst();}
    private static AtomReference reference(String c,Residue r,Atom a){return new AtomReference(c,r.getNumber(),r.getInsertionCode()==null?' ':r.getInsertionCode(),a.getName());}
    private static String sha256(Path path)throws IOException{try{MessageDigest d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)if(n>0)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private record State(String id,double chi1,double chi2){} private record Candidate(String id,double score,int clashes,Path path,String sha){} private record LocatedResidue(String chain,Residue residue){}
}
