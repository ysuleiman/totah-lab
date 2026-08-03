package totah.lab.hermes.http;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

public final class HttpClientFactory {

    private HttpClientFactory() {
    }

    public static HttpClient create(Duration connectTimeout) {
        Objects.requireNonNull(connectTimeout, "connectTimeout");

        if (connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "connectTimeout must be positive"
            );
        }

        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }
}