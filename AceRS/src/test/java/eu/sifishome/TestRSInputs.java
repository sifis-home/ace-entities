package eu.sifishome;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapServer;
import org.junit.Test;
import picocli.CommandLine;
import se.sics.ace.AceException;

import static java.lang.Thread.sleep;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestRSInputs {

    @Test
    public void testSuccessStartRS() throws Exception {

        // this is necessary for the RS, otherwise it waits
        // until it finds that the AS is reachable.
        // The check the RS performs is a ping at the specified port
        CoapServer mockedAS = new CoapServer(5683);
        mockedAS.start();

        int exitCode = new CommandLine(new AceRS()).execute("-a", "localhost:5683");
        assertEquals(0, exitCode);
        AceRS.stop();
        mockedAS.stop();
    }

    @Test
    public void testSuccessStartRSWithObserve() throws Exception {

        // this is necessary for the RS, otherwise it waits
        // until it finds that the AS is reachable.
        // The check the RS performs is a ping at the specified port
        CoapServer mockedAS = new CoapServer(5683);
        mockedAS.start();

        int exitCode = new CommandLine(new AceRS()).execute("-o");
        assertEquals(0, exitCode);
        AceRS.stop();
        mockedAS.stop();
    }

    @Test
    public void testSuccessStartRSWithPolling() throws Exception {

        // this is necessary for the RS, otherwise it waits
        // until it finds that the AS is reachable.
        // The check the RS performs is a ping at the specified port
        CoapServer mockedAS = new CoapServer(5683);
        mockedAS.start();

        int exitCode = new CommandLine(new AceRS()).execute("-p", "-e", "1");
        assertEquals(0, exitCode);
        AceRS.stop();
        mockedAS.stop();
    }

//    @Test
//    public void testSuccessStartRSWithIntrospection() throws Exception {
//
//        // this is necessary for the RS, otherwise it waits
//        // until it finds that the AS is reachable.
//        // The check the RS performs is a ping at the specified port
//        CoapServer mockedAS = new CoapServer(5683);
//        mockedAS.start();
//
//        int exitCode = new CommandLine(new AceRS()).execute("-i", "-y", "2");
//        assertEquals(0, exitCode);
//        AceRS.stop();
//        mockedAS.stop();
//    }

    @Test
    public void testSuccessStartRSWithResources() throws Exception {

        // this is necessary for the RS, otherwise it waits
        // until it finds that the AS is reachable.
        // The check the RS performs is a ping at the specified port
        CoapServer mockedAS = new CoapServer(5683);
        mockedAS.start();

        int exitCode = new CommandLine(new AceRS()).execute("-s", "r_temp r_helloWorld w_humidity w_volume r_brightness");
        assertEquals(0, exitCode);
        AceRS.stop();
        mockedAS.stop();
    }

    @Test
    public void testFailStartRSWithNonExistentResource() throws Exception {

        // this is necessary for the RS, otherwise it waits
        // until it finds that the AS is reachable.
        // The check the RS performs is a ping at the specified port
        CoapServer mockedAS = new CoapServer(5683);
        mockedAS.start();

        int exitCode = new CommandLine(new AceRS()).execute("-s", "r_wrong");
        assertNotEquals(0, exitCode);
        AceRS.stop();
        mockedAS.stop();
    }
}