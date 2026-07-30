package totah.lab.pipeline;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class PipelineContext extends HashMap<String, Object> {

    private final Path workingDirectory;
    private final Path runDirectory;
    public PipelineContext(Path workingDirectory, Path runDirectory) {
        this(workingDirectory, runDirectory, new HashMap<>());
    }

    public PipelineContext(Path workingDirectory, Path runDirectory, Map<String, Object> config) {
        super();
        if (config != null) {
            putAll(config);
        }
        this.workingDirectory = workingDirectory;
        this.runDirectory = runDirectory;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }
    public Path getRunDirectory() {
        return runDirectory;
    }

    /**
     * Fluent put — returns this for chaining.
     */
    public PipelineContext with(String key, Object value) {
        put(key, value);
        return this;
    }

    public PipelineContext withAll(Map<String, Object> map) {
        if (map != null) {
            putAll(map);
        }
        return this;
    }

    /**
     * Type-safe getter with default.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        Object value = get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Required getter — throws if missing.
     */
    @SuppressWarnings("unchecked")
    public <T> T require(String key) {
        Object value = get(key);
        if (value == null) {
            throw new IllegalStateException("Missing required context key: " + key);
        }
        return (T) value;
    }

    /**
     * Static factory from map.
     */
    public static PipelineContext from(Path workingDirectory, Path runDirectory, Map<String, Object> config) {
        return new PipelineContext(workingDirectory, runDirectory, config);
    }
}