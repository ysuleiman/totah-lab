package totah.lab.prometheus.ingest;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Paths that must never be descended or parsed while ingesting the
 * mettl7-phase2 archive: vendored literature-comparator sources, Python
 * environments, bytecode caches and the QUBEKit feasibility tree.
 */
public final class ArchiveExclusions {

    private static final String[] EXCLUDED_SEGMENTS = {
            "literature-comparator-sources",
            ".conda",
            ".venv",
            "__pycache__",
            // vendored QUBEKit reference tree under execution-unit-05P-qubekit-feasibility
            "QUBEKit"
    };

    private ArchiveExclusions() {
    }

    /** True when {@code path} lies inside an excluded tree. */
    public static boolean isExcluded(Path path) {
        Objects.requireNonNull(path, "path");
        for (Path segment : path) {
            String name = segment.toString();
            for (String excluded : EXCLUDED_SEGMENTS) {
                if (name.equals(excluded) || name.startsWith(".conda")) {
                    return true;
                }
            }
        }
        return false;
    }
}
