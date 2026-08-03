package totah.lab.daedalus.docking.importer;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class ChemflowDockingImporterTest {

    @Test
    void posesAndContactsUseTheSameCompoundCriterion() {
        String joinPredicate = "id::text = p.pose_metadata->>'compound_id'";

        assertThat(ChemflowDockingImporter.POSE_SELECT)
                .contains("JOIN compounds")
                .contains(joinPredicate);
        // Contacts must restrict to poses with a resolvable compound, exactly
        // like the pose import, instead of accepting any non-null metadata id.
        assertThat(ChemflowDockingImporter.CONTACT_SELECT)
                .contains("JOIN compounds comp ON comp." + joinPredicate)
                .doesNotContain("pose_metadata->>'compound_id' IS NOT NULL");
    }

    @Test
    void streamsSourceReadsWithAutoCommitDisabled() throws Exception {
        List<String> events = new ArrayList<>();
        ResultSet emptyRows = emptyResultSet();
        Queue<Statement> sourceStatements = new ArrayDeque<>(List.of(
                recordingStatement(events, "run", emptyRows),
                recordingStatement(events, "pose", emptyRows),
                recordingStatement(events, "contact", emptyRows)));
        PreparedStatement destinationWrite = (PreparedStatement) proxy(
                PreparedStatement.class,
                (instance, method, args) -> switch (method.getName()) {
                    case "executeQuery" -> emptyRows;
                    case "executeBatch" -> new int[0];
                    case "executeUpdate" -> 0;
                    default -> defaultValue(method.getReturnType());
                });
        Connection source = (Connection) proxy(
                Connection.class,
                (instance, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> true;
                    case "setAutoCommit" -> {
                        events.add("source.setAutoCommit(" + args[0] + ")");
                        yield null;
                    }
                    case "createStatement" -> sourceStatements.remove();
                    default -> defaultValue(method.getReturnType());
                });
        Connection destination = (Connection) proxy(
                Connection.class,
                (instance, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> true;
                    case "createStatement" ->
                            recordingStatement(events, "destination", emptyRows);
                    case "prepareStatement" -> destinationWrite;
                    default -> defaultValue(method.getReturnType());
                });

        ChemflowImportResult result = new ChemflowDockingImporter(
                Path.of("artifact-root")).importData(source, destination);

        assertThat(result.runs()).isZero();
        // PostgreSQL only honors fetch size when auto-commit is off, and only
        // when the fetch size is set before the query executes.
        assertThat(events).containsSubsequence(
                "source.setAutoCommit(false)",
                "pose.setFetchSize(2000)",
                "pose.executeQuery",
                "contact.setFetchSize(2000)",
                "contact.executeQuery",
                "source.setAutoCommit(true)");
    }

    private static ResultSet emptyResultSet() {
        return (ResultSet) proxy(
                ResultSet.class,
                (instance, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Statement recordingStatement(
            List<String> events, String name, ResultSet rows) {
        return (Statement) proxy(
                Statement.class,
                (instance, method, args) -> switch (method.getName()) {
                    case "setFetchSize" -> {
                        events.add(name + ".setFetchSize(" + args[0] + ")");
                        yield null;
                    }
                    case "executeQuery" -> {
                        events.add(name + ".executeQuery");
                        yield rows;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(
                ChemflowDockingImporterTest.class.getClassLoader(),
                new Class<?>[]{type},
                handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0;
        }
        return null;
    }
}
