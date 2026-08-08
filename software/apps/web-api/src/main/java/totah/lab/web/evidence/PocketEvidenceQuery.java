package totah.lab.web.evidence;

import java.io.IOException;

public interface PocketEvidenceQuery {
    PocketEvidenceView get(long pocketId) throws IOException;
}
