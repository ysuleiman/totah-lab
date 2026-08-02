package totah.lab.hermes.file.reader;

import java.io.IOException;
import java.nio.file.Path;

public interface FileReader<T> {

    T read(Path path) throws IOException;

    boolean supports(Path path);
}