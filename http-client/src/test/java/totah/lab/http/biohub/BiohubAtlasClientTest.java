package totah.lab.http.biohub;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiohubAtlasClientTest {

    private static final String HASH = "973eb56c8acaa2458cd7beae3af41781";
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void retrievesClusterAndDownloadsSequenceArchive() throws Exception {
        byte[] archive = zip("sequences.fasta", ">" + HASH + "|A|test\nACDE\n");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/esm/protein/api/v1alpha1/clusters/" + HASH,
                exchange -> {
                    byte[] body = ("{\"protein_name\":\"METTL7A\","
                            + "\"cluster_size\":1,\"member_protein_hashes\":[\""
                            + HASH + "\"]}").getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.createContext("/esm/protein/api/v1alpha1/proteins/batch",
                exchange -> {
                    exchange.sendResponseHeaders(200, archive.length);
                    exchange.getResponseBody().write(archive);
                    exchange.close();
                });
        server.start();
        BiohubAtlasClient client = new BiohubAtlasClient(URI.create(
                "http://localhost:" + server.getAddress().getPort()
        ));

        var cluster = client.getCluster(HASH);
        byte[] downloaded = client.downloadSequenceArchive(List.of(HASH));

        assertEquals("METTL7A", cluster.representativeName());
        assertEquals(List.of(HASH), cluster.memberHashes());
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(downloaded))) {
            assertEquals("sequences.fasta", zip.getNextEntry().getName());
            assertTrue(new String(zip.readAllBytes(), StandardCharsets.UTF_8)
                    .contains("ACDE"));
        }
    }

    private byte[] zip(String name, String content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
