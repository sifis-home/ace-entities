package eu.sifishome;


import org.junit.Before;
import org.junit.Test;
import se.sics.ace.AceException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TestDBHelper {

    @Before
    public void cleanDBHelper() throws IOException {
        DBHelper.restoreDefaultClassFields();

        // copy the db.pwd file in the location expected by the DBHelper
        Path copied = Paths.get("target/db.pwd");
        Path originalPath = Paths.get("db.pwd");
        Files.copy(originalPath, copied, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Test the DBHelper with default URI.
     */
    @Test
    public void testSucceedDefaultURI() throws AceException, IOException {
        DBHelper.setUpDB(null);
        DBHelper.tearDownDB();
    }

    @Test
    public void testFailBadScheme() throws AceException {
        assertThrows(IllegalArgumentException.class, () -> {
            DBHelper.setUpDB("http://fail:3000");
        });
        DBHelper.tearDownDB();
    }

    @Test
    public void testFailCredentialsTooManyColon() throws AceException {
        assertThrows(AceException.class, () -> {
            DBHelper.setUpDB("jdbc:mysql://user:pwd:foo@fail:3000");
        });
        DBHelper.tearDownDB();
    }

    @Test
    public void testFailCredentialsNoUsername() throws AceException {
        assertThrows(AceException.class, () -> {
            DBHelper.setUpDB("jdbc:mysql://:pwd@fail:3000");
        });
        DBHelper.tearDownDB();
    }

    @Test
    public void testWarningCredentialsNoPassword() throws IOException {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        try {
            DBHelper.setUpDB("jdbc:mysql://user@localhost:3306");
        }
        // exception is thrown afterward, because the user 'user' with no password
        // gets access denied when trying to wipe the database
        catch (AceException e) {
            // this exact output tells us that the password is indeed empty
            assertEquals("Warning: no password for user user was specified. Using an empty password...\n" +
                    "Credentials loaded from the provided database URI\n", outContent.toString());
        }

    }
}
