package totah.lab.web.pocketmatch;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketMatchCandidateProviderTest {

    private final AtomicInteger dataSourceCalls = new AtomicInteger();
    private final DataSource dataSource = trackingDataSource();
    private final PocketMatchProperties properties =
            new PocketMatchProperties();
    private final PocketMatchSignatureLoader signatureLoader =
            new PocketMatchSignatureLoader(
                    dataSource,
                    System.getProperty("java.io.tmpdir"),
                    ""
            );
    private final PocketMatchCandidateProvider provider =
            new PocketMatchCandidateProvider(properties, signatureLoader);

    @Test
    void disabledChannelReturnsNoCandidatesWithoutTouchingTheDatabase() {
        assertTrue(provider.topCandidates(32L).isEmpty());
        assertEquals(0, dataSourceCalls.get());
    }

    @Test
    void enabledChannelWithoutSignatureStoreReturnsNoCandidates() {
        properties.setEnabled(true);
        properties.setSignatureStore(
                "build/nonexistent-pocket-match-store.bin"
        );

        assertTrue(provider.topCandidates(32L).isEmpty());
        assertEquals(0, dataSourceCalls.get());
    }

    @Test
    void exposesTheConfigurationGate() {
        assertEquals(false, provider.isEnabled());
        properties.setEnabled(true);
        assertEquals(true, provider.isEnabled());
    }

    /*
     * Mockito's inline mock maker cannot instrument JDK interfaces on
     * this JDK, so the disabled-path tests use a counting proxy.
     */
    private DataSource trackingDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, arguments) -> {
                    dataSourceCalls.incrementAndGet();
                    throw new UnsupportedOperationException(
                            "DataSource must not be used in this test"
                    );
                }
        );
    }
}
