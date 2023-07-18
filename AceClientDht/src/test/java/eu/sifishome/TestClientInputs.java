package eu.sifishome;

import org.eclipse.californium.core.CoapServer;
import org.junit.Test;
import picocli.CommandLine;

import static org.junit.Assert.assertEquals;

public class TestClientInputs {

    @Test
    public void testSuccessStartClient() throws Exception {

        // this is necessary for the Client, otherwise it waits
        // until it finds that the AS is reachable.
        // The check the RS performs is a ping at the specified port
        CoapServer mockedAS = new CoapServer(5683);
        mockedAS.start();

        int exitCode = new CommandLine(new AceClientDht()).execute("-a", "localhost:5683");
        assertEquals(0, exitCode);
        mockedAS.stop();
    }

    @Test
    public void testSuccessStartClientWithObserve() throws Exception {

        // this is necessary for the Client, otherwise it waits
        // until it finds that the AS is reachable.
        // The check the RS performs is a ping at the specified port
        CoapServer mockedAS = new CoapServer(5683);
        mockedAS.start();

        int exitCode = new CommandLine(new AceClientDht()).execute("-o");
        assertEquals(0, exitCode);
        mockedAS.stop();
    }

    @Test
    public void testSuccessStartClientWithPolling() throws Exception {

        // this is necessary for the Client, otherwise it waits
        // until it finds that the AS is reachable.
        // The check the RS performs is a ping at the specified port
        CoapServer mockedAS = new CoapServer(5683);
        mockedAS.start();

        int exitCode = new CommandLine(new AceClientDht()).execute("-p", "-e", "2");
        assertEquals(0, exitCode);
        mockedAS.stop();
    }
}
