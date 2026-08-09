package totah.lab.web.assembly;

import org.springframework.stereotype.Component;
import totah.lab.hermes.file.mmcif.UniProtSequenceReference;
import totah.lab.hermes.file.mmcif.reader.MmcifUniProtSequenceReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Component
public final class MmcifExperimentalTargetSequenceSourceLoader
        implements ExperimentalTargetSequenceSourceLoader {
    private final MmcifUniProtSequenceReader reader =
            new MmcifUniProtSequenceReader();

    @Override
    public List<UniProtSequenceReference> load(Path entryMmcif)
            throws IOException {
        return reader.read(entryMmcif);
    }
}
