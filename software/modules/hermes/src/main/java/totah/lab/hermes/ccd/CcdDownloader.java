package totah.lab.hermes.ccd;

import totah.lab.hermes.http.RemoteEndpoints;
import totah.lab.hermes.http.HttpClientFactory;
import totah.lab.hermes.http.HttpTransport;
import totah.lab.hermes.http.JdkHttpTransport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Downloads CCD chemistry files from RCSB for one component:
 * the CCD definition ({@code <COMP>.cif}) and the ideal-coordinates SDF
 * ({@code <COMP>_ideal.sdf}). Ideal coordinates describe CCD chemistry
 * only; they are never a substitute for bound experimental coordinates.
 *
 * <p>Downloads are idempotent: a target file that already exists with
 * size &gt; 0 is skipped and reported {@link FetchStatus#ALREADY_PRESENT}.
 * HTTP 404 is reported {@link FetchStatus#NOT_FOUND} (not a failure);
 * other HTTP or network errors are reported {@link FetchStatus#FAILED}
 * after one retry on {@link IOException}. Local file-system errors are
 * thrown as checked {@link IOException}s.
 */
public final class CcdDownloader implements CcdClient {

    private static final URI BASE_URI = RemoteEndpoints.uri("rcsb.ligand");

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final Fetcher fetcher;

    /** Creates a downloader backed by the JDK {@link HttpClient}. */
    public CcdDownloader() {
        this(defaultFetcher());
    }

    /** Creates a downloader with an injected fetch function (for tests). */
    public CcdDownloader(Fetcher fetcher) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
    }

    /**
     * Downloads (or finds already present) both CCD files for a component
     * into {@code ligandsRoot/<COMP>/}.
     */
    public ComponentDownload downloadComponent(
            String componentId,
            Path ligandsRoot) throws IOException {

        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(ligandsRoot, "ligandsRoot");
        componentId = componentId.trim().toUpperCase(Locale.ROOT);
        if (!componentId.matches("[A-Z0-9]{1,10}")) {
            throw new IllegalArgumentException("Invalid CCD component ID: " + componentId);
        }

        Path directory = ligandsRoot.resolve(componentId);
        Files.createDirectories(directory);

        Path ccdCif = directory.resolve(componentId + ".cif");
        Path idealSdf = directory.resolve(componentId + "_ideal.sdf");

        FetchStatus ccdCifStatus = fetch(
                BASE_URI.resolve(componentId + ".cif").toString(), ccdCif);
        FetchStatus idealSdfStatus = fetch(
                BASE_URI.resolve(componentId + "_ideal.sdf").toString(), idealSdf);

        return new ComponentDownload(
                componentId,
                ccdCifStatus,
                idealSdfStatus,
                present(ccdCifStatus) ? ccdCif : null,
                present(idealSdfStatus) ? idealSdf : null);
    }

    private static boolean present(FetchStatus status) {
        return status == FetchStatus.DOWNLOADED
                || status == FetchStatus.ALREADY_PRESENT;
    }

    private FetchStatus fetch(String url, Path target) throws IOException {
        if (isValid(target)) {
            return FetchStatus.ALREADY_PRESENT;
        }
        Files.deleteIfExists(target);
        return fetcher.fetch(url, target);
    }

    private static Fetcher defaultFetcher() {
        HttpTransport transport = new JdkHttpTransport(
                HttpClientFactory.create(TIMEOUT));
        return (url, target) -> fetchHttp(transport, url, target);
    }

    private static FetchStatus fetchHttp(
            HttpTransport transport, String url, Path target)
            throws IOException {

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "Totah-Lab-Hermes/1.0")
                .GET()
                .build();

        HttpResponse<byte[]> response;
        try {
            response = send(transport, request);
        } catch (IOException firstFailure) {
            try {
                response = send(transport, request);
            } catch (IOException secondFailure) {
                return FetchStatus.FAILED;
            }
        }

        int status = response.statusCode();
        if (status == 404) {
            return FetchStatus.NOT_FOUND;
        }
        if (status != 200) {
            return FetchStatus.FAILED;
        }
        Path temporary = Files.createTempFile(
                target.toAbsolutePath().getParent(), target.getFileName().toString(), ".part");
        try {
            Files.write(temporary, response.body());
            if (!isValid(temporary, target.getFileName().toString())) {
                return FetchStatus.FAILED;
            }
            moveIntoPlace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return FetchStatus.DOWNLOADED;
    }

    private static HttpResponse<byte[]> send(
            HttpTransport transport, HttpRequest request)
            throws IOException {
        try {
            return transport.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching "
                    + request.uri(), exception);
        }
    }

    private static boolean isValid(Path path) throws IOException {
        return isValid(path, path.getFileName().toString());
    }

    private static boolean isValid(Path path, String expectedName) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) == 0) {
            return false;
        }
        String content = Files.readString(path);
        if (expectedName.toLowerCase().endsWith(".sdf")) {
            return content.contains("V2000") && content.contains("M  END");
        }
        if (expectedName.toLowerCase().endsWith(".cif")) {
            return content.lines().map(String::trim).anyMatch(line -> line.startsWith("data_"));
        }
        return false;
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Result of one fetch attempt for one file. */
    public enum FetchStatus {
        DOWNLOADED,
        ALREADY_PRESENT,
        NOT_FOUND,
        FAILED
    }

    /** Fetches one URL into one target file. */
    @FunctionalInterface
    public interface Fetcher {
        FetchStatus fetch(String url, Path target) throws IOException;
    }

    /** Per-component download outcome with the resulting local paths. */
    public record ComponentDownload(
            String componentId,
            FetchStatus ccdCifStatus,
            FetchStatus idealSdfStatus,
            Path ccdCif,
            Path idealSdf
    ) {
        public ComponentDownload {
            Objects.requireNonNull(componentId, "componentId");
            Objects.requireNonNull(ccdCifStatus, "ccdCifStatus");
            Objects.requireNonNull(idealSdfStatus, "idealSdfStatus");
        }
    }
}
