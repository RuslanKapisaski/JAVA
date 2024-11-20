package orm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class MyConnector {
    private static Connection connection;
    private static final String jdbcString  = "jdbc:postgresql://localhost:5432/";

// ctrl + '/' - comment

    public static void createConnection(String user, String password, String dbName) throws SQLException {

        connection = DriverManager.getConnection(jdbcString+dbName, user, password);

    }
    public static Connection getConnection(){
        return connection;
    }


}