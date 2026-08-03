package totah.lab.hermes.file.pocket;

import totah.lab.gaia.pocket.Pocket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class P2RankAdapter implements Adapter<Path, Pocket> {

    private final P2RankJsonParser parser;

    public P2RankAdapter(){
        parser = new P2RankJsonParser();
    }
    @Override
    public boolean supports(Path path) {
        if (Files.isRegularFile(path)) {
            return path.getFileName().toString().equals("prediction.json");
        }
        return Files.isRegularFile(path.resolve("prediction.json"))
                || Files.isRegularFile(
                        path.resolve("prank").resolve("prediction.json"));
    }

    @Override
    public List<Pocket> parse(Path path) throws IOException {
        return parser.parse(path);
    }
}
