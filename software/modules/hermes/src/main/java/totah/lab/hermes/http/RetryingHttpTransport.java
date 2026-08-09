package totah.lab.hermes.http;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Retries transient connection termination without interpreting HTTP status codes. */
public final class RetryingHttpTransport implements HttpTransport {

    private final HttpTransport delegate;
    private final int maxAttempts;
    private final Duration initialDelay;

    public RetryingHttpTransport(
            HttpTransport delegate,
            int maxAttempts,
            Duration initialDelay
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.initialDelay = Objects.requireNonNull(initialDelay, "initialDelay");
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must not be negative");
        }
    }

    @Override
    public <T> HttpResponse<T> send(
            HttpRequest request,
            HttpResponse.BodyHandler<T> bodyHandler
    ) throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.send(request, bodyHandler);
            } catch (IOException exception) {
                if (!isRetryable(exception)) {
                    throw exception;
                }
                lastFailure = exception;
                if (attempt < maxAttempts) {
                    sleep(initialDelay.multipliedBy(attempt));
                }
            }
        }
        throw Objects.requireNonNull(lastFailure, "retry ended without a failure");
    }

    private static void sleep(Duration delay) throws InterruptedException {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    private static boolean isRetryable(IOException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof EOFException || current instanceof SocketException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
