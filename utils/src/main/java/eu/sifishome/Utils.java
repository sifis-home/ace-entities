package eu.sifishome;

import org.eclipse.californium.core.CoapClient;
import picocli.CommandLine;
import se.sics.ace.AceException;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;


public class Utils {

    public static String validateUri(String srvUri, CommandLine.Model.CommandSpec spec)
            throws CommandLine.ParameterException {

        try {
            if (!srvUri.contains("://")) {
                srvUri = "coap://" + srvUri;
            }
            URI uri = new URI(srvUri);
            if (uri.getHost() == null || uri.getPort() == -1) {
                throw new URISyntaxException(uri.toString(),
                        "URI must have host and port parts");
            }
            return uri.toString();
        } catch (URISyntaxException ex) {
            // validation failed
            throw new CommandLine.ParameterException(spec.commandLine(),
                    String.format("Server address not valid:\n > '%s'\n", srvUri));
        }
    }

    public static int validatePort(String portStr, CommandLine.Model.CommandSpec spec)
            throws CommandLine.ParameterException {
        int port = Integer.parseInt(portStr);
        if (port < 1 || port > 65535) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    String.format("Port number not valid:\n > '%s'\n", port));
        }
        return port;
    }

    public static byte[] hexStringToByteArray(String str, CommandLine.Model.CommandSpec spec)
            throws CommandLine.ParameterException {

        String s = str.replace("0x", "");
        try {
            Integer.parseInt(s, 16);
        } catch (NumberFormatException nfe) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    String.format("Hexadecimal value is not valid:\n > '%s'", str));
        }

        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static boolean createDir(File dir) throws AceException {
        if (dir.exists()) {
            Utils.deleteDir(dir);
        }
        if (!dir.mkdir()) {
            throw new AceException("Unable to create policies directory");
        }
        return true;
    }

    public static boolean deleteDir(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDir(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    public static String getResourcePath(Class<?> clazz) {

        URL input = clazz.getProtectionDomain().getCodeSource().getLocation();

        try {
            File myfile = new File(input.toURI());
            File dir = myfile.getParentFile(); // strip off .jar file
            return dir.getAbsolutePath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readContent(InputStream input) {
        return new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
    }

    public static InputStream accessFile(Class<?> clazz, String fileName) {

        // this is the path within the jar file
        InputStream input = clazz.getResourceAsStream(
                File.separator + "resources" + File.separator + fileName);
        if (input == null) {
            // this is how we load file within editor
            input = clazz.getClassLoader().getResourceAsStream(fileName);
        }

        return input;
    }

    /**
     * Determine if a CoAP server is reachable by pinging it.
     *
     * @param entity   the (common) name of the entity to ping
     * @param srvUri   the URI of the server to ping
     * @param timeout  the time (in milliseconds) after which the ping gives up
     * @param attempts the number of failing pings before returning
     * @return true if the server is reachable (ping was successful), false otherwise
     */
    public static boolean isServerReachable(String entity, String srvUri, Long timeout, int attempts) {

        CoapClient checker = new CoapClient(srvUri);

        for (int i = 0; i < attempts; i++) {
            System.out.println("Attempt to reach " + entity + " at: " + srvUri + " ...");
            if (checker.ping(timeout)) {
                System.out.println(entity + " at " + srvUri + " is available.");
                checker.shutdown();
                return true;
            }
        }
        System.out.println("Unable to reach "+ entity + " at " + srvUri + ".");
        checker.shutdown();
        return false;
    }
}
