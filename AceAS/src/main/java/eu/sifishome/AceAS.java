package eu.sifishome;

import COSE.*;
import it.cnr.iit.ucs.properties.components.PipProperties;
import it.cnr.iit.xacml.Category;
import it.cnr.iit.xacml.DataType;
import org.eclipse.californium.core.coap.CoAP;
import se.sics.ace.*;
import se.sics.ace.as.PDP;
import se.sics.ace.as.TrlConfig;
import se.sics.ace.as.logging.DhtLogger;
import se.sics.ace.coap.as.CoapDBConnector;
import se.sics.ace.coap.as.OscoreAS;
import se.sics.ace.examples.KissPDP;
import se.sics.ace.examples.KissTime;

import se.sics.ace.ucs.UcsHelper;
import se.sics.ace.ucs.properties.UcsPapProperties;
import se.sics.ace.ucs.properties.UcsPipReaderProperties;

import java.io.*;
//import java.security.Provider;
//import java.security.Security;
import java.util.*;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ArgGroup;

import eu.sifishome.peers.Client;
import eu.sifishome.peers.ResourceServer;

/**
 * Authorization Server to test with AceClient and AceRS
 *
 * @author Marco Rasori
 *
 */
@Command(name = "a-server",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Runs the ACE Authorization Server.\n" +
                "The default PDP is the UCS")
public class AceAS implements Callable<Integer> {

    private final static String DEFAULT_CLIENT_NAME = "ClientA";
    private final static String DEFAULT_RESOURCE_SERVER_NAME = "rs1";
    private final static String DEFAULT_CLIENT_SCOPE = "r_temp r_helloWorld";
    private final static String DEFAULT_RESOURCE_SERVER_SCOPE = "r_temp r_helloWorld";
    private final static String DEFAULT_CLIENT_AUD = "rs1";
    // the AS automatically adds an audience with the resource server name
    private final static String DEFAULT_RESOURCE_SERVER_AUD = DEFAULT_RESOURCE_SERVER_NAME;
    private final static String DEFAULT_CLIENT_SENDER_ID = "0x22";
    private final static String DEFAULT_RESOURCE_SERVER_SENDER_ID = "0x11";
    private final static String DEFAULT_CLIENT_MASTER_SECRET =
            "ClientA-AS-MS---";
    private final static String DEFAULT_RESOURCE_SERVER_MASTER_SECRET =
            "RS1-AS-MS-------"; //16-byte long
    private final static String DEFAULT_RESOURCE_SERVER_TOKEN_PSK =
            "RS1-AS-Default-PSK-for-tokens---"; //32-byte long

    private final static String DEFAULT_RESOURCES = "Temp HelloWorld";

    @Option(names = {"-d", "--dht"},
            required = false,
            description = "Enable DHT logging")
    public boolean isDhtLoggingEnabled = false;

    @Option(names = {"-K", "--Kisspdp"},
            required = false,
            description = "Use the KissPDP as PDP.\n" +
                    "(default: UCS)\n")
    private boolean isKissPDP;

    @Option(names = {"-N", "--numberOfAttributes"},
            required = false,
            description = "Number of mutable attributes of the policies containing 'r_temp' and 'r_helloWorld' " +
                    "subscopes.")
    public int numAttributes = 1;

    @Option(names = {"-Y", "--resources"},
            required = false,
            description = "List of resources managed by the AS. Possible values: 'Brightness', 'HelloWorld', " +
                    "'Humidity', 'Temp', and 'Volume'.\n" +
                    "(default: '" + DEFAULT_RESOURCES + "')")
    String resources;

    static class Opt {
        @Option(names = {"-n", "--name"},
                required = true,
                description = "The peer name.\n" +
                        "(default: '" + DEFAULT_CLIENT_NAME + "' for the Client;\n" +
                        "          '" + DEFAULT_RESOURCE_SERVER_NAME + "' for the Resource Server)")
        String name;

        @Option(names = {"-s", "--scope"},
                required = true,
                description = "The scope.\n" +
                        "(default: '" + DEFAULT_CLIENT_SCOPE + "' for the Client;\n" +
                        "          '" + DEFAULT_RESOURCE_SERVER_SCOPE + "' for the Resource Server)")
        List<String> scope;

        @Option(names = {"-u", "--aud"},
                required = true,
                description = "The audience.\n" +
                        "(default: '" + DEFAULT_CLIENT_AUD + "' for the Client;\n" +
                        "          '" + DEFAULT_RESOURCE_SERVER_AUD + "' for the Resource Server)")
        List<String> aud;

        @Option(names = {"-x", "--senderId"},
                required = true,
                description = "The Sender ID in HEX used for " +
                        "the OSCORE Security Context with the Authorization Server.\n" +
                        "(default: '" + DEFAULT_CLIENT_SENDER_ID + "' for the Client;\n" +
                        "          '" + DEFAULT_RESOURCE_SERVER_SENDER_ID + "' for the Resource Server)")
        String sId;

        @Option(names = {"-m", "--mastersecret"},
                required = true,
                description = "The symmetric pre-shared key between " +
                        "the Authorization Server and the peer. It is the master " +
                        "secret used for the OSCORE Security Context.\n" +
                        "(default: '" + DEFAULT_CLIENT_MASTER_SECRET + "' for the Client;\n" +
                        "          '" + DEFAULT_RESOURCE_SERVER_MASTER_SECRET + "' for the Resource Server)")
        String key;

        @Option(names = {"-k", "--key"},
                required = false,
                defaultValue = DEFAULT_RESOURCE_SERVER_TOKEN_PSK,
                description = "The symmetric pre-shared key between the Resource " +
                        "Server and the Authorization Server. It is used by the " +
                        "Authorization Server to protect the tokens for this " +
                        "Resource Server.\n" +
                        "(default: ${DEFAULT-VALUE})\n")
        String tokenKey;
    }

    static class Peer {
        @Option(names = {"-C", "--Client"},
                required = true,
                description = "Add a new Client")
        boolean isClient;

        @Option(names = {"-R", "--Resourceserver"},
                required = true,
                description = "Add a new Resource Server")
        boolean isResourceServer;
    }
    static class Args {
        @ArgGroup(exclusive = true, multiplicity = "1")
        Peer peer;

        @ArgGroup(exclusive = false, multiplicity = "1")
        Opt opt;
    }

    @ArgGroup(exclusive = false, multiplicity = "0..*")
    List<AceAS.Args> args;

    static OneKey myAsymmKey;

    private static CoapDBConnector db = null;
    private static OscoreAS as = null;
    private static PDP pdp;

    private final static Map<String, String> peerNamesToIdentities = new HashMap<>();
    private final static Map<String, String> peerIdentitiesToNames = new HashMap<>();
    private final static Map<String, String> myIdentities = new HashMap<>();
    private final static byte[] idContext = new byte[] {0x44};

    static String asName = "AS";
    private final String asIdentity = buildOscoreIdentity(new byte[] {0x33}, idContext);

    private static Timer timer;

    private static String attributeFilesPath = "src/main/resources/attributes/";

    //--- MAIN
    public static void main(String[] args) {

        int exitCode = new CommandLine(new AceAS()).execute(args);
        if (exitCode != 0) {
            as.stop();
            System.exit(exitCode);
        }
    }



    @Override
    public Integer call() throws Exception {

        parseNumAttributes();
        parseResources();

        DBHelper.setUpDB();
        db = DBHelper.getCoapDBConnector();

        setupPDP();

        parseInputs();

        KissTime time = new KissTime();

        TrlConfig trlConfig = new TrlConfig("trl", 3, null, true);

        as = new OscoreAS(asName, db, pdp, time, myAsymmKey,"token", "introspect", trlConfig,
                CoAP.DEFAULT_COAP_PORT, null, false, (short)1, true,
                peerNamesToIdentities, peerIdentitiesToNames, myIdentities);

        as.start();
        System.out.println("Server starting");
        //as.stop();

        timer = new Timer();
        timer.schedule(new AttributeChanger("thermometer-reachable.txt", "changedValue"),
                30000 + (int)(Math.random() * 30000));

        return 0;
    }

    /**
     * Stops the server
     * @throws Exception
     */
    public static void stop() throws Exception {
        as.stop();
        pdp.close();
        DBHelper.tearDownDB();
        System.out.println("Server stopped");
    }


    private void parseInputs() throws AceException {

        List<Args> inputClients;
        try {
            inputClients = new ArrayList<>(this.args);
            inputClients.removeIf(c -> c.peer.isResourceServer);
        } catch (NullPointerException e) {
            inputClients = new ArrayList<>();
        }

        List<Args> inputResourceServers;
        try {
            inputResourceServers = new ArrayList<>(this.args);
            inputResourceServers.removeIf(r -> r.peer.isClient);
        } catch (NullPointerException e) {
            inputResourceServers = new ArrayList<>();
        }

        List<Client> clients = new ArrayList<>();
        if (inputClients.isEmpty()) {
            clients.add(new Client(DEFAULT_CLIENT_NAME, new ArrayList<String>(){{add(DEFAULT_CLIENT_SCOPE);}},
                    new ArrayList<String>(){{add(DEFAULT_CLIENT_AUD);}}, DEFAULT_CLIENT_SENDER_ID,
                    DEFAULT_CLIENT_MASTER_SECRET));
        }
        for (Args c : inputClients) {
            clients.add(new Client( c.opt.name, c.opt.scope, c.opt.aud,
                    c.opt.sId, c.opt.key));
        }
        for(Client c : clients) {
            setupClient(c);
        }

        List<ResourceServer> resourceServers = new ArrayList<>();
        if (inputResourceServers.isEmpty()) {
            resourceServers.add(new ResourceServer(DEFAULT_RESOURCE_SERVER_NAME,
                    DEFAULT_RESOURCE_SERVER_SCOPE, DEFAULT_RESOURCE_SERVER_AUD,
                    DEFAULT_RESOURCE_SERVER_SENDER_ID, DEFAULT_RESOURCE_SERVER_MASTER_SECRET,
                    DEFAULT_RESOURCE_SERVER_TOKEN_PSK));
        }
        for (Args r: inputResourceServers) {

            // check that all the options have been specified

            // if the resource server name is specified within the audience, remove it
            // (The AS automatically adds an audience with the resource server name for the resource server)
            if (r.opt.aud.get(0).contains(r.opt.name)) {
                r.opt.aud.set(0, r.opt.aud.get(0).replaceAll(r.opt.name, ""));
                r.opt.aud.set(0, r.opt.aud.get(0).trim().replaceAll(" +", " "));
            }
            resourceServers.add(new ResourceServer(r.opt.name, r.opt.scope.get(0), r.opt.aud.get(0),
                    r.opt.sId, r.opt.key, r.opt.tokenKey));
        }
        for (ResourceServer r: resourceServers) {
            setupResourceServer(r);
        }

        if (isDhtLoggingEnabled) {
            // Possibly set up connection to the DHT for sending logging statements
            System.out.println("Connecting to the DHT for logging.");
            DhtLogger.setLogging(true);
            DhtLogger.establishConnection();
        }

    }

    private void parseNumAttributes() {
        if (numAttributes < 1) {
            System.out.println("Number of attributes " + numAttributes + " not supported.\n" +
                    "Setting it to the minimum allowed, i.e., 1");
            numAttributes = 1;
        }
        if (numAttributes > 50) {
            System.out.println("Number of attributes " + numAttributes + " not supported.\n" +
                    "Setting it to the maximum allowed, i.e., 50");
            numAttributes = 50;
        }
    }

    private void parseResources() {
        if (resources == null || resources.isEmpty()) {
            resources = DEFAULT_RESOURCES;
        }
        else {
            // validate input
        }
    }


    private void setupResourceServer(ResourceServer r) throws AceException {

        db.addRS(r.getName(), r.getProfiles(), r.getScopeSet(), r.getAudSet(),
                r.getKeyTypes(), r.getTokenTypes(), r.getCose(), r.getExpiration(),
                r.getSharedPsk(), r.getTokenPsk(), null);
        addIdentity(r.getName(), r.getsId());
        pdp.addIntrospectAccess(r.getName());
    }
    private void setupClient(Client c) throws AceException {

        db.addClient(c.getName(), c.getProfiles(), null, null,
                c.getKeyTypes(), c.getSharedPsk(), null);
        addIdentity(c.getName(), c.getsId());
        pdp.addTokenAccess(c.getName());

        for (int i = 0; i < c.getScope().size(); i++) {
            String scope = c.getScope().get(i);
            List<String> scopes = new ArrayList<>(Arrays.asList(scope.split(" ")));
            for (String subScope : scopes) {
                if (pdp instanceof UcsHelper) {
                    String res = subScope.substring(subScope.indexOf("_") + 1);
                    String policySuffix = "";
                    if (res.equals("temp") && numAttributes > 1) {
                        policySuffix = "_" + numAttributes + "_attributes";
                    }
//                    if (res.equals("helloWorld") && numAttributes > 1) {
//                        policySuffix = "_" + numAttributes + "_attributes";
//                    }
                    ((UcsHelper) pdp).addAccess(c.getName(), c.getAud().get(i), subScope,
                            String.valueOf(AceAS.class.getClassLoader().getResource("policy-templates/policy_template_" + res + policySuffix)).replace("file:", ""));
                }
                else {
                    pdp.addAccess(c.getName(), c.getAud().get(i), subScope);
                }
            }
        }
    }


    private void addIdentity(String peerName, byte[] peerIdentity) {

        String peerIdentityStr = buildOscoreIdentity(peerIdentity, idContext);
        peerNamesToIdentities.put(peerName, peerIdentityStr);
        peerIdentitiesToNames.put(peerIdentityStr, peerName);
        myIdentities.put(peerName, asIdentity);
    }

    private String buildOscoreIdentity(byte[] senderId, byte[] contextId) {

        if (senderId == null)
            return null;
        String identity = "";

        if (contextId != null) {
            identity += Base64.getEncoder().encodeToString(contextId);
            identity += ":";
        }
        identity += Base64.getEncoder().encodeToString(senderId);

        return identity;

    }


    private void setupPDP() throws AceException {

        if (isKissPDP) {
            pdp = new KissPDP(db);
        } else {

            restoreAttributesValue();

            List<String> allowedResources = new ArrayList<>(Arrays.asList(resources.split(" ")));

            UcsPipReaderProperties pipReader = new UcsPipReaderProperties();
            List<PipProperties> pipPropertiesList= new ArrayList<>();

            if (allowedResources.contains("Temp")) {
                pipReader.addAttribute(
                        "urn:oasis:names:tc:xacml:3.0:environment:thermometer-reachable",
                        Category.ENVIRONMENT.toString(),
                        DataType.STRING.toString(),
                        String.valueOf(AceAS.class.getClassLoader().getResource("attributes/thermometer-reachable.txt")).replace("file:", ""));
                pipReader.setRefreshRate(10L);
                pipPropertiesList.add(pipReader);

                //add additional PIPs as specified by the -N option
                for (int i = 2; i <= numAttributes; i++) {
                    pipPropertiesList.add(preparePIPReader("attribute-temp" + i));
                }
            }

            if (allowedResources.contains("Humidity")) {
                // used only for warm up, if the Client uses the -W option
                pipReader = new UcsPipReaderProperties();
                pipReader.addAttribute(
                        "urn:oasis:names:tc:xacml:3.0:environment:hygrometer-reachable",
                        Category.ENVIRONMENT.toString(),
                        DataType.STRING.toString(),
                        String.valueOf(AceAS.class.getClassLoader().getResource("attributes/hygrometer-reachable.txt")).replace("file:", ""));
                pipPropertiesList.add(pipReader);
            }

            if (allowedResources.contains("Brightness")) {
                pipReader = new UcsPipReaderProperties();
                pipReader.addAttribute(
                        "urn:oasis:names:tc:xacml:3.0:environment:screen-reachable",
                        Category.ENVIRONMENT.toString(),
                        DataType.STRING.toString(),
                        String.valueOf(AceAS.class.getClassLoader().getResource("attributes/screen-reachable.txt")).replace("file:", ""));
                pipPropertiesList.add(pipReader);
            }

            if (allowedResources.contains("Volume")) {
                pipReader = new UcsPipReaderProperties();
                pipReader.addAttribute(
                        "urn:oasis:names:tc:xacml:3.0:environment:speaker-reachable",
                        Category.ENVIRONMENT.toString(),
                        DataType.STRING.toString(),
                        String.valueOf(AceAS.class.getClassLoader().getResource("attributes/speaker-reachable.txt")).replace("file:", ""));
                pipPropertiesList.add(pipReader);
            }

            if (allowedResources.contains("HelloWorld")) {
                pipReader = new UcsPipReaderProperties();
                pipReader.addAttribute(
                        "urn:oasis:names:tc:xacml:3.0:environment:welcome-led-panel",
                        Category.ENVIRONMENT.toString(),
                        DataType.STRING.toString(),
                        String.valueOf(AceAS.class.getClassLoader().getResource("attributes/welcome-led-panel.txt")).replace("file:", ""));
                pipPropertiesList.add(pipReader);
            }

            // code to add a PIPReader monitoring the role of the subject
//            pipReader = new UcsPipReaderProperties();
//            pipReader.addAttribute(
//                    "urn:oasis:names:tc:xacml:1.0:subject:role",
//                    Category.SUBJECT.toString(),
//                    DataType.STRING.toString(),
//                    String.valueOf(AceAS.class.getClassLoader().getResource("attributes/role.txt")).replace("file:", ""));
//            pipPropertiesList.add(pipReader);



//            for (int i = 2; i <= numAttributes; i++) {
//                pipPropertiesList.add(preparePIPReader("attribute-helloWorld" + i));
//            }

            UcsPapProperties papProperties =
                    new UcsPapProperties(String.valueOf(AceAS.class.getClassLoader().getResource("policies/")).replace("file:", ""));;

            pdp = new UcsHelper(db, pipPropertiesList, papProperties);
        }
    }

    private void restoreAttributesValue() {

        // restore the value of the attributes
        setAttributeValue(String.valueOf(AceAS.class.getClassLoader()
                .getResource("attributes/hygrometer-reachable.txt")).replace("file:", ""), "y");

        setAttributeValue(String.valueOf(AceAS.class.getClassLoader()
                .getResource("attributes/screen-reachable.txt")).replace("file:", ""), "y");

        setAttributeValue(String.valueOf(AceAS.class.getClassLoader()
                .getResource("attributes/speaker-reachable.txt")).replace("file:", ""), "y");

        setAttributeValue(String.valueOf(AceAS.class.getClassLoader()
                .getResource("attributes/thermometer-reachable.txt")).replace("file:", ""), "y");

        setAttributeValue(String.valueOf(AceAS.class.getClassLoader()
                .getResource("attributes/welcome-led-panel.txt")).replace("file:", ""), "Hi!");

//      setAttributeValue(String.valueOf(AceAS.class.getClassLoader().getResource(
//              "attributes/role.txt")), "ClientB maintainer\n" +
//              "ClientC developer\n" +
//              "ClientA maintainer\n" +
//              "ClientD admin");

        for (int i = 2; i <= numAttributes; i++) {
            setAttributeValue(String.valueOf(AceAS.class.getClassLoader()
                    .getResource("attributes/attribute-temp" + i + ".txt")).replace("file:", ""), "y");
        }

//        for (int i = 2; i <= numAttributes; i++) {
//            setAttributeValue(String.valueOf(AceAS.class.getClassLoader()
//            .getResource("attributes/attribute-helloWorld" + i + ".txt")).replace("file:", ""), "y");
//        }
    }

    public static UcsPipReaderProperties preparePIPReader(String attribute) {
        UcsPipReaderProperties pipReader = new UcsPipReaderProperties();
        pipReader.addAttribute(
                "urn:oasis:names:tc:xacml:3.0:environment:" + attribute,
                Category.ENVIRONMENT.toString(),
                DataType.STRING.toString(),
                String.valueOf(AceAS.class.getClassLoader().getResource("attributes/" + attribute + ".txt")).replace("file:", ""));
        return pipReader;
    }

    public static void setAttributeValue(String fileName, String value) {

        File file = new File(fileName);
        FileWriter fw = null;
        try {
            fw = new FileWriter(file);
            fw.write(value);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Change the attribute value within the file identified by fileName.
     * This triggers the possible revocation of tokens for the policies
     * that include that attribute.
     */
    public static class AttributeChanger extends TimerTask {

        private final String fileName;
        private final String value;

        public AttributeChanger(String fileName, String value) {
            this.fileName = fileName;
            this.value = value;
        }

        public void run() {
            File file = new File(String.valueOf(AceAS.class.getClassLoader()
                    .getResource("attributes/" + this.fileName)).replace("file:", ""));
            FileWriter fw = null;
            try {
                fw = new FileWriter(file);
                fw.write(this.value);
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
