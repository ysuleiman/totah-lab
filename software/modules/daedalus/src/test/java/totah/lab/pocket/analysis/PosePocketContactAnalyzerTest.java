package totah.lab.pocket.analysis;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PosePocketContactAnalyzerTest {

    @Test
    void leavesTransactionBoundaryToTheCaller() throws Exception {
        AtomicBoolean committed = new AtomicBoolean(false);
        AtomicInteger batches = new AtomicInteger();

        ResultSet pocketRows = rows(List.of(Map.of(
                "id", 1L,
                "x", 0.0,
                "y", 0.0,
                "z", 0.0,
                "pocket_residue_id", 10L,
                "atom_name", "CA",
                "element", "C"
        )));
        ResultSet poseRows = rows(List.of(Map.of(
                "id", 100L,
                "x", 1.0,
                "y", 0.0,
                "z", 0.0,
                "pose_id", 7L
        )));
        Statement pocketStatement = statementReturning(pocketRows);
        Statement poseStatement = statementReturning(poseRows);
        PreparedStatement insert = proxy(PreparedStatement.class,
                (target, method, args) -> {
                    if (method.getName().equals("executeBatch")) {
                        batches.incrementAndGet();
                        return new int[0];
                    }
                    return defaultValue(method.getReturnType());
                });
        Statement[] statements = {pocketStatement, poseStatement};
        int[] statementIndex = {0};
        Connection connection = proxy(Connection.class,
                (target, method, args) -> switch (method.getName()) {
                    case "createStatement" -> statements[statementIndex[0]++];
                    case "prepareStatement" -> insert;
                    case "commit" -> {
                        committed.set(true);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                });

        new PosePocketContactAnalyzer().buildContacts(connection);

        assertThat(batches.get()).isEqualTo(1);
        assertThat(committed).isFalse();
    }

    private static ResultSet rows(List<Map<String, Object>> data) {
        int[] index = {-1};
        return proxy(ResultSet.class, (target, method, args) -> switch (method.getName()) {
            case "next" -> ++index[0] < data.size();
            case "getLong", "getDouble", "getString" ->
                    data.get(index[0]).get((String) args[0]);
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Statement statementReturning(ResultSet rows) {
        return proxy(Statement.class, (target, method, args) -> {
            if (method.getName().equals("executeQuery")) {
                return rows;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                PosePocketContactAnalyzerTest.class.getClassLoader(),
                new Class<?>[]{type},
                handler
        ));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
