import com.actelion.research.chem.ugly.PainsDetector;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Explicit research extraction only. Never used by production matching. */
public class ExtractPinnedCatalog {
 static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 public static void main(String[] args)throws Exception{
  var jar=Path.of(PainsDetector.class.getProtectionDomain().getCodeSource().getLocation().toURI());
  String pin="2e1642f29f09c1def25dc405a77973c28bae02355a6807320e42f1dc4cd157c9";
  if(!sha(Files.readAllBytes(jar)).equals(pin))throw new IllegalStateException("jar pin");
  var f=PainsDetector.class.getDeclaredField("PAINS");f.setAccessible(true);var catalog=(String[][])f.get(null);
  var entries=new ArrayList<Map<String,Object>>();var ordered=new StringBuilder();
  for(int i=0;i<catalog.length;i++){var e=catalog[i];if(e.length!=2)throw new IllegalStateException("entry shape");for(var v:e)ordered.append(v.length()).append(':').append(v);entries.add(Map.of("index",i,"label",e[1],"queryFormat","OCL_IDCODE_QUERY/2026.7.2","query",e[0],"querySha256",sha(e[0].getBytes(StandardCharsets.UTF_8))));}
  String digest=sha(ordered.toString().getBytes(StandardCharsets.UTF_8));
  if(catalog.length!=890||!digest.equals("d0e92e06907a6bcb9e8a0cb59674a6410b42816b65e05e725b269fb2e736f7c2"))throw new IllegalStateException("catalog pin");
  Files.write(Path.of(args[0]),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("schema","athena-advisory-alert-catalog/1","catalogId","OCL.PAINS","catalogVersion","2026.7.2","sourceJarSha256",pin,"orderedCatalogSha256",digest,"entries",entries)));
 }
}
