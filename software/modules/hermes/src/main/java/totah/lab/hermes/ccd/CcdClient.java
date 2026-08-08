package totah.lab.hermes.ccd;

import java.io.IOException;
import java.nio.file.Path;

/** Acquires authoritative CCD chemistry and optional idealized SDF files. */
public interface CcdClient {

    CcdDownloader.ComponentDownload downloadComponent(
            String componentId, Path componentsRoot) throws IOException;
}
