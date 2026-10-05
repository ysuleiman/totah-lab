import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.RuleRegistry;
import java.nio.file.*;
import java.util.TreeMap;
public class LegacyManifestDigests {
    public static void main(String[] args)throws Exception {
        var hashes=new TreeMap<String,String>();
        RuleRegistry.bundled().manifests().forEach((id,m)->hashes.put(id,RuleRegistry.digest(m)));
        Files.write(Path.of(args[0]),SystemStateView.bytes(hashes),StandardOpenOption.CREATE_NEW);
    }
}
