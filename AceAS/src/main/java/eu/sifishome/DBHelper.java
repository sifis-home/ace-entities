package eu.sifishome;

import se.sics.ace.AceException;
import se.sics.ace.coap.as.CoapDBConnector;
import se.sics.ace.examples.MySQLDBAdapter;
//import se.sics.ace.examples.PostgreSQLDBAdapter;
import se.sics.ace.examples.SQLConnector;
import se.sics.ace.examples.SQLDBAdapter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Helper class to set up databases for tests.
 *
 * @author Sebastian Echeverria and Marco Tiloca and Marco Rasori
 *
 */
public class DBHelper
{
    /**
     * Easy place to change which DB adapter wants to be used for all tests.
     */
    private static final SQLDBAdapter dbAdapter = new MySQLDBAdapter(); //PostgreSQLDBAdapter();

    private static final String testUsername = "testuser";
    private static final String testPassword = "testpwd";
    private static final String testDBName = "testdb";

    private static String dbAdminUser = null;
    private static String dbAdminPwd = null;

    /**
     * Sets up the DB using the current default adapter.
     * 
     * @throws AceException if acting on the database fails
     * @throws IOException if loading admin information fails
     */
    public static void setUpDB() throws AceException, IOException
    {
        // First load the DB admin username and password from an external file.
        loadAdminLoginInformation();
        
        // Set parameters for the DB.
        dbAdapter.setParams(testUsername, testPassword, testDBName, null);

        // In case database and/or user already existed.
        SQLConnector.wipeDatabase(dbAdapter, dbAdminUser, dbAdminPwd);

        // Create the DB and user for the tests.
        SQLConnector.createUser(dbAdapter, dbAdminUser, dbAdminPwd);
        SQLConnector.createDB(dbAdapter, dbAdminUser, dbAdminPwd);
    }

    /**
     * @return  the SQLConnector instance
     * @throws SQLException if an error occurs retrieving the database instance
     */
    public static SQLConnector getSQLConnector() throws SQLException
    {
        // Get a connection to the test DB.
        return SQLConnector.getInstance(dbAdapter);
    }

    /**
     * @return the CoapDBConnector instance
     * @throws SQLException if an error occurs when retrieving the CoapDBConnector istance
     */
    public static CoapDBConnector getCoapDBConnector() throws SQLException
    {
        // Get a connection to the test DB.
        return CoapDBConnector.getInstance(dbAdapter);
    }

    /**
     * Destroy the test DB with the default adapter.
     * @throws AceException if an error occurs when wiping the database
     */
    public static void tearDownDB() throws AceException
    {
        dbAdapter.setParams(testUsername, testPassword, testDBName, null);
        SQLConnector.wipeDatabase(dbAdapter, dbAdminUser, dbAdminPwd);
    }

    /**
     * Loads the admin username and password form an external file.
     * @throws IOException if an error occurs when retrieving the file with the credentials for the database
     */
    private static void loadAdminLoginInformation() throws IOException
    {
        try (BufferedReader br = new BufferedReader(new FileReader(
                Utils.getResourcePath(AceAS.class) + File.separator + "db.pwd"))) {
            int readLines = 0;
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null && readLines < 2) {
                sb.delete(0, sb.length());
                sb.append(line);
                sb.append(System.lineSeparator());

                if (readLines == 0) {
                    dbAdminUser = sb.toString().replace(System.getProperty("line.separator"), "");
                }
                if (readLines == 1) {
                    dbAdminPwd = sb.toString().replace(System.getProperty("line.separator"), "");
                }
                readLines++;
                line = br.readLine();
            }
        }
    }
}
