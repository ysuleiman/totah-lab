package totah.lab.io;

import totah.lab.pocket.FPocketParser;
import totah.lab.gaia.pocket.Pocket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FPocketAdapter implements Adapter<Path, Pocket> {

    public FPocketAdapter(){
    }
    @Override
    public boolean supports(Path path) {
        return Files.exists(path.resolve("fpocket"));
    }

    @Override
    public List<Pocket> parse(Path path) throws IOException {
        // supports() accepts the target directory, but the info file and the
        // pockets/ subfolder live inside its fpocket/ child directory
        return FPocketParser.parse(path.resolve("fpocket"));
    }
}
