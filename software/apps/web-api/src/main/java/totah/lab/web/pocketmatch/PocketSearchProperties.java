package totah.lab.web.pocketmatch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the pocket-search retrieval channels that are
 * unioned into Stage 1 (prefix {@code pocket.search}).
 *
 * <pre>
 * pocket.search.global-shape.enabled=true
 * pocket.search.global-shape.limit=500
 * pocket.search.include-chosen-references=true
 * </pre>
 *
 * The experimental PocketMatch channel is configured separately by
 * {@link PocketMatchProperties} ({@code pocket.search.pocket-match.*},
 * disabled by default).
 */
@Component
@ConfigurationProperties(prefix = "pocket.search")
public class PocketSearchProperties {

    private final GlobalShape globalShape = new GlobalShape();

    /**
     * Injects the receptor's chosen pockets as guaranteed-evaluation
     * candidates (no score or rank bonus, never auto-positive).
     */
    private boolean includeChosenReferences = true;

    public GlobalShape getGlobalShape() {
        return globalShape;
    }

    public boolean isIncludeChosenReferences() {
        return includeChosenReferences;
    }

    public void setIncludeChosenReferences(
            boolean includeChosenReferences
    ) {
        this.includeChosenReferences = includeChosenReferences;
    }

    public static class GlobalShape {

        /**
         * Enables the global (whole-structure) shape retrieval
         * channel — the production Stage 1 path.
         */
        private boolean enabled = true;

        /**
         * Maximum number of global-shape candidates unioned into
         * Stage 1.
         */
        private int limit = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }
}
