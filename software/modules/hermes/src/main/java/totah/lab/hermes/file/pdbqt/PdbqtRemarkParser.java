package totah.lab.hermes.file.pdbqt;

import java.util.List;
import java.util.Optional;

public interface PdbqtRemarkParser<T> {
    Optional<T> parse(List<String> remarks);
}