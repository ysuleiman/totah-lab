package totah.lab.hermes.biohub;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

interface BiohubHttpTransport {

    Response post(
            URI uri,
            String bearerToken,
            Duration timeout,
            String jsonBody
    ) throws IOException, InterruptedException;

    record Response(int statusCode, String body) {
    }
}
