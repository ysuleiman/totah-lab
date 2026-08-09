package totah.lab.hermes.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoteEndpointsTest {

    @Test
    void loadsEndpointsFromClasspathResource() {
        assertEquals("https", RemoteEndpoints.uri("rcsb.entry").getScheme());
        assertEquals("files.rcsb.org",
                RemoteEndpoints.uri("rcsb.download").getHost());
        assertEquals("biohub.ai", RemoteEndpoints.uri("biohub.base").getHost());
    }
}
