package totah.lab.hermes.component;

import java.io.IOException;

/** Reusable API for bound-component inventory and CCD acquisition. */
public interface ComponentInventoryService {

    ComponentInventoryResult build(ComponentInventoryRequest request)
            throws IOException;
}
