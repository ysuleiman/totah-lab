package totah.lab.hermes.file.api;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface FileValidator<R> { R validate(Path path) throws IOException; }
