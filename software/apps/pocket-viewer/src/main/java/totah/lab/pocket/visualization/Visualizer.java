package totah.lab.pocket.visualization;

import totah.lab.gaia.pocket.Pocket;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class Visualizer {
    public Visualizer() {
    }

    public static void main(String[] args) throws Exception {
        PocketDataset dataset;
        Path inputDirectory = null;
        if (args.length > 0) {
            inputDirectory = Path.of(args[0])
                    .toAbsolutePath()
                    .normalize();
            dataset = new PocketDatasetLoader().load(inputDirectory);
        } else {
            URL resource = Objects.requireNonNull(
                    Visualizer.class.getResource("/Q6UX53/"),
                    "Missing bundled /Q6UX53/ sample");
            dataset = loadDataset(resource);
        }
        Pocket pocket = PocketRanking.preferredPocket(dataset.pockets());
        if (pocket == null) {
            throw new IOException(
                    "No pockets found"
                            + (inputDirectory == null
                            ? ""
                            : " in " + inputDirectory)
                            + ". Choose a folder containing fpocket or "
                            + "P2Rank/prank results.");
        }

        new PocketViewerApp(dataset, pocket)
                .startViewer();
    }

    private static boolean relaunchOnMacFirstThreadIfNeeded(
            String[] args) throws IOException, InterruptedException {
        if (!requiresMacFirstThreadRelaunch(
                System.getProperty("os.name"),
                Boolean.getBoolean("pocket.viewer.firstThread"))) {
            return false;
        }

        List<String> command = new ArrayList<>();
        command.add(Path.of(
                System.getProperty("java.home"),
                "bin",
                "java").toString());
        command.add("-XstartOnFirstThread");
        command.add("-Dpocket.viewer.firstThread=true");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Visualizer.class.getName());
        command.addAll(List.of(args));

        Process child = new ProcessBuilder(command)
                .inheritIO()
                .start();
        int exitCode = child.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "Pocket Viewer exited with code " + exitCode);
        }
        return true;
    }

    static boolean requiresMacFirstThreadRelaunch(
            String operatingSystem,
            boolean alreadyRelaunched) {
        boolean macOs = operatingSystem != null
                && operatingSystem.toLowerCase().contains("mac");
        return macOs && !alreadyRelaunched;
    }

    static PocketDataset loadDataset(URL resource) throws Exception {
        Objects.requireNonNull(resource, "resource");
        URI resourceUri = resource.toURI();
        if ("file".equalsIgnoreCase(resourceUri.getScheme())) {
            return new PocketDatasetLoader().load(Path.of(resourceUri));
        }

        if (!"jar".equalsIgnoreCase(resourceUri.getScheme())) {
            throw new IllegalArgumentException(
                    "Unsupported protein resource URI: " + resourceUri);
        }

        try (FileSystem resourceFileSystem =
                     FileSystems.newFileSystem(resourceUri, Map.of())) {
            Path temporaryDirectory =
                    copyToTemporaryDirectory(Path.of(resourceUri));
            try {
                return new PocketDatasetLoader().load(temporaryDirectory);
            } finally {
                deleteRecursively(temporaryDirectory.getParent());
            }
        }
    }

    private static Path copyToTemporaryDirectory(Path source)
            throws IOException {
        Path temporaryRoot = Files.createTempDirectory(
                "pocket-viewer-protein-");
        Path destination = temporaryRoot.resolve(
                source.getFileName().toString());
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(
                        source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        } catch (IOException exception) {
            deleteRecursively(temporaryRoot);
            throw exception;
        }
        return destination;
    }

    private static void deleteRecursively(Path directory)
            throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public static void show(BufferedImage image, String title) {
        Objects.requireNonNull(image, "image");
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException(
                    "Pocket Viewer requires a graphical environment");
        }

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(
                    title == null || title.isBlank()
                            ? "Pocket Viewer"
                            : title);
            frame.setDefaultCloseOperation(
                    WindowConstants.DISPOSE_ON_CLOSE);
            frame.setContentPane(new JScrollPane(
                    new JLabel(new ImageIcon(image))));
            frame.setSize(
                    Math.min(image.getWidth(), 1000),
                    Math.min(image.getHeight(), 800));
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    static int[] argbPixels(BufferedImage image) {
        Objects.requireNonNull(image, "image");
        int width = image.getWidth();
        return image.getRGB(
                0,
                0,
                width,
                image.getHeight(),
                null,
                0,
                width);
    }
}
