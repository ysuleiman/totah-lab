package totah.lab.daedalus.docking;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketGridBoxTest {

    @Test
    void sphereBoxUsesCentroidExtentRadiiAndPadding() {
        PocketGridBox box = PocketGridBox.fromPoints(
                List.of(new Point3D(0, 0, 0), new Point3D(10, 0, 0)),
                List.of(2.0, 4.0),
                8.0
        );

        assertEquals(new Point3D(5.0, 0.0, 0.0), box.center());
        // x: (10 + 4) - (0 - 2) + 2 * 8 = 32
        assertEquals(32.0, box.size().x(), 1e-9);
        // y/z: (0 + 4) - (0 - 4) + 16 = 24
        assertEquals(24.0, box.size().y(), 1e-9);
        assertEquals(24.0, box.size().z(), 1e-9);
    }

    @Test
    void atomFallbackUsesZeroRadii() {
        PocketGridBox box = PocketGridBox.fromPoints(
                List.of(new Point3D(1, 2, 3), new Point3D(4, -2, 0)),
                null,
                0.5
        );

        assertEquals(new Point3D(2.5, 0.0, 1.5), box.center());
        assertEquals(new Point3D(4.0, 5.0, 4.0), box.size());
    }

    @Test
    void convertsToVinaOptions() {
        PocketGridBox box = PocketGridBox.fromPoints(
                List.of(new Point3D(1, 2, 3)),
                null,
                8.0
        );

        VinaDockingOptions options = box.toVinaOptions();
        assertEquals(1.0, options.centerX(), 1e-9);
        assertEquals(3.0, options.centerZ(), 1e-9);
        assertEquals(16.0, options.sizeX(), 1e-9);
    }

    @Test
    void rejectsEmptyPointsAndBadArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                PocketGridBox.fromPoints(List.of(), null, 8.0));
        assertThrows(IllegalArgumentException.class, () ->
                PocketGridBox.fromPoints(
                        List.of(new Point3D(0, 0, 0)), null, -1.0));
        assertThrows(IllegalArgumentException.class, () ->
                PocketGridBox.fromPoints(
                        List.of(new Point3D(0, 0, 0)),
                        List.of(1.0, 2.0), 8.0));
    }

    @Test
    void missingPocketFailsClearly() throws Exception {
        PocketGridBoxLoader loader = new PocketGridBoxLoader(
                new PocketGridBoxLoader.DatabaseConfig(
                        "jdbc:unused", "unused", "unused"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> loader.load(fakeConnection(List.of()), 99L, 8.0));
        assertTrue(failure.getMessage().contains("Pocket not found"));
    }

    @Test
    void pocketWithoutSpheresOrAtomsFailsClearly() throws Exception {
        PocketGridBoxLoader loader = new PocketGridBoxLoader(
                new PocketGridBoxLoader.DatabaseConfig(
                        "jdbc:unused", "unused", "unused"));

        // First query (pocket row) returns FPOCKET; sphere and atom
        // queries return nothing.
        Connection connection = fakeConnection(List.of(
                new Object[][]{{"FPOCKET"}},
                new Object[][]{},
                new Object[][]{}
        ));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> loader.load(connection, 7L, 8.0));
        assertTrue(failure.getMessage().contains("no alpha spheres"));
        assertTrue(failure.getMessage().contains("FPOCKET"));
    }

    @Test
    void loaderReadsSpheresBeforeAtoms() throws Exception {
        PocketGridBoxLoader loader = new PocketGridBoxLoader(
                new PocketGridBoxLoader.DatabaseConfig(
                        "jdbc:unused", "unused", "unused"));

        Connection connection = fakeConnection(List.of(
                new Object[][]{{"FPOCKET"}},
                new Object[][]{{0.0, 0.0, 0.0, 2.0},
                        {10.0, 0.0, 0.0, 2.0}},
                new Object[][]{}
        ));

        PocketGridBox box = loader.load(connection, 7L, 1.0);
        assertEquals(new Point3D(5.0, 0.0, 0.0), box.center());
        // x: (10 + 2) - (0 - 2) + 2 * 1 = 16
        assertEquals(16.0, box.size().x(), 1e-9);
    }

    /*
     * JDBC fakes via JDK proxies (Mockito cannot instrument JDK
     * interfaces on this JDK): each prepared statement answers with the
     * next queued row set.
     */
    private static Connection fakeConnection(List<Object[][]> resultSets) {
        Deque<Object[][]> queue = new ArrayDeque<>(resultSets);
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getName().equals("prepareStatement")) {
                Object[][] rows = queue.isEmpty()
                        ? new Object[][]{}
                        : queue.removeFirst();
                return fakeStatement(rows);
            }
            if (method.getName().equals("close")) {
                return null;
            }
            throw new UnsupportedOperationException(
                    "Unexpected call: " + method.getName());
        };
        return (Connection) Proxy.newProxyInstance(
                PocketGridBoxTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler);
    }

    private static PreparedStatement fakeStatement(Object[][] rows) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PocketGridBoxTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "setLong", "setDouble", "setString" -> null;
                    case "executeQuery" -> fakeResultSet(rows);
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(
                            "Unexpected call: " + method.getName());
                });
    }

    private static ResultSet fakeResultSet(Object[][] rows) {
        return (ResultSet) Proxy.newProxyInstance(
                PocketGridBoxTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new InvocationHandler() {
                    private int cursor = -1;

                    @Override
                    public Object invoke(
                            Object proxy,
                            java.lang.reflect.Method method,
                            Object[] arguments) {
                        return switch (method.getName()) {
                            case "next" -> ++cursor < rows.length;
                            case "getString" ->
                                    (String) rows[cursor][(Integer) arguments[0] - 1];
                            case "getDouble" ->
                                    (Double) rows[cursor][(Integer) arguments[0] - 1];
                            case "close" -> null;
                            default -> throw new UnsupportedOperationException(
                                    "Unexpected call: " + method.getName());
                        };
                    }
                });
    }
}
