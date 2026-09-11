package totah.lab.mettl7.surface;

import totah.lab.athena.surface.differential.*;
import totah.lab.gaia.structure.ResidueId;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads, hash-validates, and materializes the frozen METTL7 SurfDiff-compatible evidence. */
public final class Mettl7FrozenDifferentialSurfaceLoader {
    public static final String A_VS_B_SHA256="f5328accc14db38be4f36518af1018f67a90b315fab6f3f54134530bff714801";
    public static final String B_VS_A_SHA256="a6c9647daa7ae25cee05c1d4c5e4ab48d65e13c48fd33501fc72562426c9c911";
    public record Loaded(DifferentialSurfaceMap map,String direction,String sourceSha256,Path source){}
    public Loaded load(Path repositoryRoot,String queryParalog)throws IOException{
        if (!List.of("A", "B").contains(queryParalog)) {
            throw new IllegalArgumentException("queryParalog must be A or B");
        }
        String direction=queryParalog.equals("A")?"METTL7A_VS_METTL7B":"METTL7B_VS_METTL7A";
        String expected=queryParalog.equals("A")?A_VS_B_SHA256:B_VS_A_SHA256;
        Path path=repositoryRoot.resolve("research/mettl7-surfdiff-compatible-v1/"+direction+"_residue_scores.csv");
        String observed=sha256(path);if(!observed.equals(expected))throw new IOException("frozen SurfDiff hash mismatch: "+path);
        List<DifferentialResidueScore> scores=new ArrayList<>();
        for(Map<String,String> row:Mettl7Csv.read(path)){
            if(!direction.equals(row.get("direction")))throw new IOException("SurfDiff direction mismatch");
            ResidueId query=new ResidueId(row.get("query_chain"),Integer.parseInt(row.get("query_number")),null);
            String subjectNumber=row.get("subject_number");Optional<ResidueId> subject=subjectNumber.isBlank()?Optional.empty():Optional.of(
                    new ResidueId(row.get("subject_chain"),Integer.parseInt(subjectNumber),null));
            scores.add(new DifferentialResidueScore(query,subject,Double.parseDouble(row.get("query_rsasa")),
                    Double.parseDouble(row.get("exposure_weight")),Double.parseDouble(row.get("rup")),
                    Double.parseDouble(row.get("rus")),Double.parseDouble(row.get("rss"))));
        }
        return new Loaded(new DifferentialSurfaceMap(DifferentialSurfaceMode.SURFDIFF_COMPATIBLE,
                DifferentialSurfaceOptions.SURFDIFF_COMPATIBLE,scores,Map.of()),direction,observed,path);
    }
    private static String sha256(Path path)throws IOException{try{var d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[]b=new byte[8192];for(int n;(n=in.read(b))>=0;)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
