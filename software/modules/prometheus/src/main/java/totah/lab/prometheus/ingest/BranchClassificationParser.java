package totah.lab.prometheus.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Recovers a branch's final classification string from its decision artifacts —
 * parsed, never assumed. Search order: JSON files (keys {@code classification},
 * {@code final_classification}, {@code verdict}, {@code decision} with an
 * ALL_CAPS value), then Markdown reports (first backtick-quoted ALL_CAPS token,
 * then bold, then the first bare ALL_CAPS token of at least two words).
 */
final class BranchClassificationParser {

    private static final List<String> CLASSIFICATION_KEYS = List.of(
            "classification", "final_classification", "verdict", "decision");

    private static final Pattern BACKTICKED = Pattern.compile("`([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)`");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)\\*\\*");
    private static final Pattern BARE_TOKEN = Pattern.compile("\\b([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)\\b");

    private BranchClassificationParser() {
    }

    /** The recovered classification plus the file it was recovered from. */
    record RecoveredClassification(String classification, Path reportPath, String summary) {
    }

    /**
     * Finds the classification for the branch rooted at {@code branchDir}.
     * JSON decision files are searched before Markdown reports; files within
     * each group are processed in name order for determinism.
     */
    static Optional<RecoveredClassification> find(Path branchDir) throws IOException {
        Objects.requireNonNull(branchDir, "branchDir");
        List<Path> jsonFiles = new ArrayList<>();
        List<Path> markdownFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.list(branchDir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                if (name.endsWith(".json")) {
                    jsonFiles.add(path);
                } else if (name.endsWith(".md")) {
                    markdownFiles.add(path);
                }
            });
        }
        jsonFiles.sort(Comparator.comparing(p -> p.getFileName().toString()));
        markdownFiles.sort(Comparator.comparing(p -> p.getFileName().toString()));

        for (Path json : jsonFiles) {
            JsonNode tree;
            try {
                tree = JsonArtifacts.readTree(json);
            } catch (IOException e) {
                continue;
            }
            Optional<String> classification = classificationFromJson(tree);
            if (classification.isPresent()) {
                String summary = firstText(tree, "summary", "rationale", "notes").orElse("");
                return Optional.of(new RecoveredClassification(classification.get(), json, summary));
            }
        }
        for (Path markdown : markdownFiles) {
            String content = Files.readString(markdown);
            Optional<String> classification = firstMatch(BACKTICKED, content)
                    .or(() -> firstMatch(BOLD, content))
                    .or(() -> firstMatch(BARE_TOKEN, content));
            if (classification.isPresent()) {
                return Optional.of(new RecoveredClassification(
                        classification.get(), markdown, summaryAfter(content, classification.get())));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> classificationFromJson(JsonNode tree) {
        for (String key : CLASSIFICATION_KEYS) {
            String value = JsonArtifacts.asTextOrNull(tree, key);
            if (value != null && BARE_TOKEN.matcher(value).matches()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> firstText(JsonNode tree, String... keys) {
        for (String key : keys) {
            String value = JsonArtifacts.asTextOrNull(tree, key);
            if (value != null && !value.isBlank()) {
                return Optional.of(value.strip());
            }
        }
        return Optional.empty();
    }

    private static Optional<String> firstMatch(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /**
     * Recovers the classification token from a single Markdown report:
     * first backtick-quoted ALL_CAPS token, then bold, then first bare token.
     */
    static Optional<String> fromMarkdown(Path markdownFile) throws IOException {
        Objects.requireNonNull(markdownFile, "markdownFile");
        String content = Files.readString(markdownFile);
        return firstMatch(BACKTICKED, content)
                .or(() -> firstMatch(BOLD, content))
                .or(() -> firstMatch(BARE_TOKEN, content));
    }

    /** First non-empty line after the classification token, capped at 300 characters. */
    private static String summaryAfter(String content, String classification) {
        int at = content.indexOf(classification);
        if (at < 0) {
            return "";
        }
        String rest = content.substring(at + classification.length());
        for (String line : rest.split("\\R")) {
            String stripped = line.strip();
            if (!stripped.isEmpty() && !stripped.startsWith("#")
                    && !stripped.equals("`") && !stripped.equals("**")) {
                return stripped.length() > 300 ? stripped.substring(0, 300) : stripped;
            }
        }
        return "";
    }
}
