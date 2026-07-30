package totah.lab;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {

    private Database() {
    }

    public static Connection connect() throws SQLException {

        String url = "jdbc:postgresql://localhost:5432/totah_lab_db";
        String user = "postgres";
        String password = "admin";

        return DriverManager.getConnection(url, user, password);
    }
}
