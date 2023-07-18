package eu.sifishome;

import org.junit.Before;
import org.junit.Test;
import picocli.CommandLine;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TestASInputs {

    String user;
    String password;
    String dbUri;
    @Before
    public void loadDbCredentials() throws IOException {
        ArrayList<String> credentials = new ArrayList<>();
        FileReader fileReader = new FileReader("db.pwd");
        try (BufferedReader br = new BufferedReader(fileReader)) {
            while (br.ready()) {
                credentials.add(br.readLine());
            }
        }
        user = credentials.get(0);
        password = credentials.get(1);
        dbUri = "jdbc:mysql://" + user + ":" + password + "@localhost:3306";
    }

    @Test
    public void testFailSqlDbInput() {
        int exitCode = new CommandLine(new AceAS()).execute("-d", "wrong-db-url");
        assertNotEquals(0, exitCode);
    }

    // this test fails because the db password is not empty
    @Test
    public void testFailSqlDbInputWithExplicitCredentialsEmptyPassWord() {
        int exitCode = new CommandLine(new AceAS())
                .execute("-d", "jdbc:mysql://" + user + ":" + "@localhost:3306");
        assertNotEquals(0, exitCode);
    }

    @Test
    public void testFailSqlDbInputWithExplicitCredentialsTooManyColons() {
        int exitCode = new CommandLine(new AceAS())
                .execute("-d", "jdbc:mysql://" + user + ":" + password + ":wrong" + "@localhost:3306");
        assertNotEquals(0, exitCode);
    }

    @Test
    public void testFailSqlDbInputWithExplicitCredentialsEmptyUsername() {
        int exitCode = new CommandLine(new AceAS())
                .execute("-d", "jdbc:mysql://" + ":" + password + "@localhost:3306");
        assertNotEquals(0, exitCode);
    }

    @Test
    public void testSuccessSqlDbInputWithExplicitCredentials() throws Exception {
        int exitCode = new CommandLine(new AceAS()).execute("-d", dbUri);
        assertEquals(0, exitCode);
        AceAS.stop();
    }

    @Test
    public void testSuccessSqlDbInputLoadCredentialsFromFile() throws Exception {

        File dbFile = new File("target/db.pwd");
        assert (dbFile.delete() || !dbFile.exists());

        // copy the db.pwd file in the location expected by the DBHelper
        Path copied = Paths.get("target/db.pwd");
        Path originalPath = Paths.get("db.pwd");
        Files.copy(originalPath, copied, StandardCopyOption.REPLACE_EXISTING);

        int exitcode = new CommandLine(new AceAS()).execute("-d", "jdbc:mysql://localhost:3306");
        assertEquals(0, exitcode);
        AceAS.stop();
    }

    @Test
    public void testSuccessWebSocket() {
        String dhtAddr = "ws://localhost:3000/ws";
        if (Utils.isDhtReachable(dhtAddr, 2000, 2)) {
            int exitCode = new CommandLine(new AceAS()).execute("-d", dbUri, "-D", "-w", dhtAddr);
            assertNotEquals(0, exitCode);
        } else {
            System.out.println("Test skipped since the DHT is not reachable");
        }
    }

    @Test
    public void testSuccessClient() throws Exception {
        int exitCode = new CommandLine(new AceAS())
                .execute("-d", dbUri, "-C",
                        "-n", "clientA",
                        "-s", "r_helloWorld r_temp rw_humidity w_volume",
                        "-u", "rs1",
                        "-x", "0x22",
                        "-m", "ClientA-AS-MS---");
        assertEquals(0, exitCode);
        AceAS.stop();
    }

    @Test
    public void testSuccessKissPDP() throws Exception {
        int exitCode = new CommandLine(new AceAS())
                .execute("-d", dbUri, "-K");
        assertEquals(0, exitCode);
        AceAS.stop();
    }

    @Test
    public void testSuccessResources() throws Exception {
        int exitCode = new CommandLine(new AceAS())
                .execute("-d", dbUri, "-Y", "Brightness HelloWorld Humidity Temp Volume");
        assertEquals(0, exitCode);
        AceAS.stop();
    }

    @Test
    public void testSuccessResourceServer() throws Exception {
        int exitCode = new CommandLine(new AceAS())
                .execute("-d", dbUri, "-R",
                        "-n", "rs1",
                        "-s", "r_temp r_helloWorld rw_humidity w_volume",
                        "-u", "rs1",
                        "-x", "0x11",
                        "-m", "RS1-AS-MS-------",
                        "-k", "RS1-AS-Default-PSK-for-tokens---");
        assertEquals(0, exitCode);
        AceAS.stop();
    }

    @Test
    public void testSuccessNumAttributes() throws Exception {
        int exitCode = new CommandLine(new AceAS())
                .execute("-d", dbUri, "-N", "10");
        assertEquals(0, exitCode);
        AceAS.stop();
    }
}
