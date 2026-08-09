package totah.lab.hermes.http;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Transport boundary shared by remote-service clients. */
@FunctionalInterface
public interface HttpTransport {

    <T> HttpResponse<T> send(
            HttpRequest request,
            HttpResponse.BodyHandler<T> bodyHandler
    ) throws IOException, InterruptedException;
}
