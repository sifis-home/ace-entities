package eu.sifishome;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.upokecenter.cbor.CBORObject;
import eu.sifishome.json.incoming.JsonIn;
import eu.sifishome.json.outgoing.JsonOut;
import eu.sifishome.json.outgoing.OutValue;
import eu.sifishome.json.outgoing.RequestPubMessage;
import jakarta.websocket.*;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.californium.oscore.OSCoreCtx;
import org.eclipse.californium.oscore.OSCoreCtxDB;
import org.eclipse.californium.oscore.OSException;
import org.glassfish.tyrus.client.ClientManager;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;
import se.sics.ace.*;
import se.sics.ace.client.GetToken;
import se.sics.ace.coap.client.BasicTrlStore;
import se.sics.ace.coap.client.OSCOREProfileRequests;
import se.sics.ace.coap.client.TrlResponses;
import se.sics.ace.examples.KissTime;
import se.sics.ace.rs.AsRequestCreationHints;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Client to test with AceRS and AceAS
 * This client implements the connection to the DHT through websockets
 *
 * @author Marco Rasori
 */

@Command(name = "client",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Runs an ACE Client.")
@ClientEndpoint
public class AceClientDht implements Callable<Integer> {

    private final static String DEFAULT_ASURI = "localhost:" + CoAP.DEFAULT_COAP_PORT;
    private final static int DEFAULT_RS_PORT = 5685;
    private final static String DEFAULT_RSURI = "localhost:" + DEFAULT_RS_PORT;
    private final static int DEFAULT_MAX_DENIAL = Integer.MAX_VALUE;
    private final static String DEFAULT_AUD = "rs1";
    private final static String DEFAULT_SCOPE = "r_temp r_helloWorld";
    private final static int DEFAULT_POLLING_INTERVAL = 10;
    private final static String DEFAULT_TRL_ADDR = "/trl";
    private final static int DEFAULT_REQUEST_INTERVAL = 1;
    private final static String DEFAULT_SENDER_ID = "0x22";
    private final static String DEFAULT_MASTER_SECRET = "ClientA-AS-MS---";
    private final static String DEFAULT_DHT_ADDRESS = "ws://localhost:3000/ws";
    private final static String DEFAULT_INCOMING_TOPIC = "command_ace_ucs";
    private final static String DEFAULT_OUTGOING_TOPIC = "output_ace_ucs";
    @Spec
    CommandSpec spec;

    static class DhtArgs {
        @Option(names = {"-D", "--dht"},
                required = true,
                description = "Enable DHT.\n")
        boolean isDhtEnabled = false;

        @Option(names = {"-w", "--websocketuri"},
                required = false,
                defaultValue = DEFAULT_DHT_ADDRESS,
                description = "The URI of the websocket where the DHT process is listening.\n" +
                        "(default: ${DEFAULT-VALUE})\n")
        String dhtUri;

        @Option(names = {"-I", "--incomingtopic"},
                required = false,
                defaultValue = DEFAULT_INCOMING_TOPIC,
                description = "The topic this client is subscribed to.\n" +
                        "(default: ${DEFAULT-VALUE})\n")
        private String incomingTopic;

        @Option(names = {"-O", "--outgoingtopic"},
                required = false,
                defaultValue = DEFAULT_OUTGOING_TOPIC,
                description = "The topic this client published on.\n" +
                        "(default: ${DEFAULT-VALUE})\n")
        private String outgoingTopic;
    }

    @Option(names = {"-a", "--asuri"},
            required = false,
            defaultValue = DEFAULT_ASURI,
            description = "The URI of the Authorization Server.\n" +
                    "Hostname and port MUST be specified.\n" +
                    "(default: ${DEFAULT-VALUE})\n")
    private String asUri;

    @Option(names = {"-r", "--rsuri"},
            required = false,
            defaultValue = DEFAULT_RSURI,
            description = "The URI of the Resource Server.\n" +
                    "Hostname and port MUST be specified.\n" +
                    "(default: ${DEFAULT-VALUE})\n")
    private List<String> rsUri;

    @Option(names = {"-s", "--scope"},
            required = false,
            defaultValue = DEFAULT_SCOPE,
            description = "The scope for which the Client asks the token.\n" +
                    "(default: ${DEFAULT-VALUE})\n")
    private List<String> scope;

    @Option(names = {"-u", "--audience"},
            required = false,
            defaultValue = DEFAULT_AUD,
            description = "The audience for which the Client asks the token.\n" +
                    "(default: ${DEFAULT-VALUE})\n")
    private List<String> aud;
    // fixme: we could avoid assigning a default scope and audience.
    //        The Client could not specify them in the request to the AS,
    //        and the AS will use the default audience and scope that it
    //        has for the Client.

    @Option(names = {"-d", "--denials"},
            required = false,
            defaultValue = "" + DEFAULT_MAX_DENIAL,
            description = "The maximum number of 4.01 Unauthorized responses " +
                    "(from the same Resource Server) that the Client is " +
                    "willing to receive before assuming that -- for some reason -- " +
                    "the Resource Server removed its OSCORE Security Context " +
                    "with the Client.\n" +
                    "When this number is reached, the Client asks the Authorization " +
                    "Server a new token with the same audience and scope.\n" +
                    "(default: ${DEFAULT-VALUE})\n")
    private int denials;

    @Option(names = {"-q", "--requestinterval"},
            required = false,
            defaultValue = "" + DEFAULT_REQUEST_INTERVAL,
            description = "The time interval (in seconds) between two requests " +
                    "to protected resources (at the same Resource Server).\n" +
                    "This interval is independent of the number of resources " +
                    "the Client requests to the Resource Server.\n" +
                    "(default: ${DEFAULT-VALUE})\n")
    private int requestInterval;

    @Option(names = {"-m", "--mastersecret"},
            required = false,
            defaultValue = "" + DEFAULT_MASTER_SECRET,
            description = "The symmetric pre-shared key between the Client " +
                    "and the Authorization Server. It is the master secret " +
                    "used for the OSCORE Security Context.\n" +
                    "(default: ${DEFAULT-VALUE})\n")
    private String key;

    @Option(names = {"-x", "--senderid"},
            required = false,
            defaultValue = "" + DEFAULT_SENDER_ID,
            description = "The Sender ID in HEX used for " +
                    "the OSCORE Security Context with the Authorization Server.\n" +
                    "(default: ${DEFAULT-VALUE})\n")
    private String senderId;

    static class PollingArgs {
        @Option(names = {"-p", "--polling"},
                required = true,
                description = "The Client polls the trl endpoint at the AS.\n")
        boolean polling;

        @Option(names = {"-e", "--interval"},
                required = false,
                defaultValue = "" + DEFAULT_POLLING_INTERVAL,
                description = "The time interval (in seconds) between two polling " +
                        "requests to the trl endpoint.\n" +
                        "(default: ${DEFAULT-VALUE})\n")
        int interval;
    }

    static class ObserveArgs {
        @Option(names = {"-o", "--observe"},
                required = true,
                description = "The Client observes the trl endpoint at the AS.\n")
        boolean observe;
    }

    static class TrlAddrArg {
        @Option(names = {"-t", "--trladdress"},
                required = false,
                defaultValue = DEFAULT_TRL_ADDR,
                description = "The address of the trl endpoint, e.g., '/trl'.\n" +
                        "If query parameters are specified, e.g., '/trl?pmax=10&diff=3', " +
                        "the mode is automatically assumed to be 'diff-query'.\n" +
                        "If no query parameters are specified, the mode is assumed to " +
                        "be 'full query'.\n" +
                        "(default: ${DEFAULT-VALUE})\n")
        String trlAddress;
    }

    static class NotificationArgs {
        @ArgGroup(exclusive = false, multiplicity = "1")
        PollingArgs pollingArgs;
        @ArgGroup(exclusive = false, multiplicity = "1")
        ObserveArgs observeArgs;
    }

    static class Args {
        @ArgGroup(exclusive = true, multiplicity = "1")
        NotificationArgs notification;
        @ArgGroup(exclusive = false)
        TrlAddrArg trlAddrArg;
        @ArgGroup(exclusive = false)
        DhtArgs dhtArg;
    }

    @ArgGroup(exclusive = false)
    Args args;

    /**
     * Symmetric key shared with the authorization server and used for the OSCORE context
     */
    private static byte[] key128;

    private static OSCoreCtx ctx;
    private static OSCoreCtxDB ctxDB;

    private static final List<Set<Integer>> usedRecipientIds = new ArrayList<>();

    private final static int MAX_UNFRAGMENTED_SIZE = 4096;

    private static final byte[] idContext = new byte[]{0x44};
    private byte[] sId;

    private boolean isPolling = false;
    private boolean isObserve = false;

    private int pollingInterval;

    private String trlAddr;

    private final Map<String, TokenInfo> validTokensMap = new HashMap<>();

    private CoapClient client4AS;

    private boolean isDhtEnabled;
    private String dhtAddr;
    private String incomingTopic;
    private String outgoingTopic;
    private ScheduledExecutorService executorService = null;

    private final Set<Timer> expTasks = new HashSet<>();


    //--- MAIN
    public static void main(String[] args) {

        int exitCode = new CommandLine(new AceClientDht()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }


    @Override
    public Integer call() throws Exception {

        parseInputs();

        Utils.waitForServer("Authorization Server", asUri, 2000L);

        // initialize OSCORE context
        ctx = new OSCoreCtx(key128, true, null,
                sId, // client identity
                new byte[]{0x33}, // AS identity
                null, null, null, idContext, MAX_UNFRAGMENTED_SIZE);

        ctxDB = new org.eclipse.californium.oscore.HashMapCtxDB();

        for (int i = 0; i < 4; i++) {
            // Empty sets of assigned Sender IDs; one set for each possible Sender ID size in bytes.
            // The set with index 0 refers to Sender IDs with size 1 byte
            usedRecipientIds.add(new HashSet<>());
        }

        client4AS = OSCOREProfileRequests.buildClient(asUri, ctx, ctxDB);

        if (isPolling || isObserve) {
            TrlStore trlStore = new BasicTrlStore();

            if (isObserve) {
                // 1. Make Observe request to the /trl endpoint
                ClientCoapHandler handler = new ClientCoapHandler(trlStore);
                CoapObserveRelation relation =
                        OSCOREProfileRequests.makeObserveRequest(
                                client4AS, asUri + trlAddr, handler);
            }

            if (isPolling) {
                // 1. Make poll request to the /trl endpoint
                executorService = Executors
                        .newSingleThreadScheduledExecutor();
                executorService.scheduleAtFixedRate(
                        new Poller(client4AS, asUri + trlAddr, trlStore),
                        pollingInterval, pollingInterval, TimeUnit.SECONDS);
            }
        }

        for (int i = 0; i < rsUri.size(); i++) {
            Integer requester = new Requester(
                    client4AS, rsUri.get(i), aud.get(i), scope.get(i), executorService)
                    .call();
            if (requester == 0)
                return 0;
        }

        return 0;
    }

    class Poller implements Runnable {

        CoapClient client4AS;
        String trlUri;
        TrlStore trlStore;

        public Poller(CoapClient client4AS, String trlUri, TrlStore trlStore) {
            this.client4AS = client4AS;
            this.trlUri = trlUri;
            this.trlStore = trlStore;
        }

        @Override
        public void run() {
            try {
                System.out.println("Now polling:" + new Timestamp(System.currentTimeMillis()));
                CoapResponse responseTrl =
                        OSCOREProfileRequests.makePollRequest(
                                client4AS, trlUri);
                TrlResponses.processResponse(responseTrl, trlStore);
                purgeRevokedTokens(trlStore);
            } catch (AceException e) {
                e.printStackTrace();
            }
        }
    }


    public class ClientCoapHandler implements CoapHandler {

        private final TrlStore trlStore;

        public ClientCoapHandler(TrlStore trlStore) {
            this.trlStore = trlStore;
        }

        @Override
        public void onLoad(CoapResponse response) {
            try {
                TrlResponses.processResponse(response, trlStore);
                purgeRevokedTokens(trlStore);
            } catch (AssertionError | AceException error) {
                System.out.println("Assert:" + error);
            }
            System.out.println("NOTIFICATION: " + response.advanced());
        }

        @Override
        public void onError() {
            System.err.println("OBSERVE FAILED");
        }
    }


    public void purgeRevokedTokens(TrlStore trlStore) {

        Set<String> trl = trlStore.getLocalTrl();
        Set<String> intersection = new HashSet<>(validTokensMap.keySet());
        intersection.retainAll(trl);

        synchronized (validTokensMap) {
            for (String th : intersection) {
                validTokensMap.remove(th);
            }
            validTokensMap.notifyAll();
        }
    }


    class Requester implements Callable {

        CoapClient client4AS;
        final CoapClient client4RS;
        String aud;
        String scope;
        String rsAddr;
        int denialsCount = 0;
        /*
         * executorService for the Poller.
         */
        ScheduledExecutorService es;

        public Requester(CoapClient client4AS, String rsAddr, String aud, String scope, ScheduledExecutorService es) {
            this.client4AS = client4AS;
            this.rsAddr = rsAddr;
            this.aud = aud;
            this.scope = scope;
            this.client4RS = new CoapClient(rsAddr);
            this.es = es;
        }

        @Override
        public Integer call() throws Exception {

            TimeProvider time = new KissTime();

            while (true) {

                // 1. Get the token
                String tokenHash;
                try {
                    tokenHash = getTokenIfNotPresent(aud, scope);
                } catch (AceException e) {
                    //System.out.println("Token not issued: " + e.getMessage());
                    shutdown();
                    System.out.println("Quitting.");
                    return -1;
                }

                // FIXME (when ACE library implements this)
                // since the AS-to-Client response, at the moment, does not include neither EXI nor EXP,
                // we use a hardcoded value.

                // Flow: try to extract EXP.
                //       If it works, set timeToExpire = EXP - currentTime;
                //       If it fails, extract EXI and set timeToExpire = EXI;
                //       Post the token.
                long timeToExpire = 40000L;

                Timer timer = new Timer();
                timer.schedule(new ExpirationTask(tokenHash, validTokensMap), timeToExpire);
                expTasks.add(timer);

                Utils.waitForServer("Resource Server", rsAddr, 2000L);

                // 2. Post the token
                try {
                    postTokenIfNotPosted(tokenHash, rsAddr);
                } catch (AceException e) {
                    //System.out.println("Token not posted: " +e.getMessage());
                    //client4AS.shutdown();
                    client4RS.shutdown();
                    shutdown();
                    System.out.println("Quitting.");
                    return -1;
                }

                OSCOREProfileRequests.setClient(client4RS, ctxDB);

                // 3. Make GET requests to access the resources
                List<String> resources = new ArrayList<>(
                        Arrays.asList(validTokensMap.get(tokenHash).getScope().split(" ")));

                resources.replaceAll(s1 -> s1.substring(s1.indexOf("_") + 1));

                int i = 0;
                while (denialsCount < denials && validTokensMap.containsKey(tokenHash)) {
                    boolean isSuccess = getResource(client4RS, rsAddr + "/" + resources.get(i));

                    if (!isSuccess) {
                        denialsCount++;
                        if (denialsCount == denials) {
                            System.out.println("Too many denials.");
                            validTokensMap.remove(tokenHash); // assume the token is not valid anymore
                            break;
                        }
                    }
                    i = (i + 1) % resources.size();

                    // wait 'requestInterval' before making another request,
                    // or wake up and ignore the remaining time if the
                    // current tokenhash has been removed from the variable
                    // validTokensMap
                    long curTime = time.getCurrentTime();
                    long deadline = curTime + requestInterval * 1000L;

                    while (time.getCurrentTime() < deadline) {
                        long timeout = deadline - time.getCurrentTime();
                        synchronized (validTokensMap) {
                            validTokensMap.wait(timeout);
                            if (!validTokensMap.containsKey(tokenHash)) {
                                System.out.println("Learnt that the token is not valid anymore");
                                break;
                            }
                        }
                    }
                }

                System.out.println("Trying to get a new Access Token from the AS...");
                denialsCount = 0;
            }
        }
    }


    public Response getToken(CoapClient client4AS, String aud, String scope) throws AceException, OSException {
        CBORObject params = GetToken.getClientCredentialsRequest(
                CBORObject.FromObject(aud), CBORObject.FromObject(scope), null);

        Response asRes = OSCOREProfileRequests.getToken(
                client4AS, asUri + "/token", params);

        if (asRes.getCode().isServerError() || asRes.getCode().isClientError()) {
            throw new AceException("Failure response received from the AS: Token not issued");
        }
        return asRes;
    }


    public boolean postToken(String rsUri, Response asRes, Map<Short, CBORObject> map) throws AceException, OSException {
        // 2. Post the Access Token to the /authz-info endpoint at the RS
        if (map.containsKey(Constants.CNF)) {
            Response rsRes = OSCOREProfileRequests.postToken(
                    rsUri + "/authz-info", asRes, ctxDB, usedRecipientIds);
            System.out.println("\nResponse from RS (token post)");
            System.out.println("Response Code:       " + rsRes.getCode());

            if (rsRes.getCode().isServerError() || rsRes.getCode().isClientError()) {
                throw new AceException("Failure response received from the RS (Posting new token)");
            }
        } else {
            CoapResponse rsRes = OSCOREProfileRequests.postTokenUpdate(
                    rsUri + "/authz-info", asRes, ctxDB);
            System.out.println("\nResponse from RS (token update post)");
            System.out.println("Response Code:       " + rsRes.getCode());

            if (rsRes.getCode().isServerError() || rsRes.getCode().isClientError()) {
                throw new AceException("Failure response received from the RS (posting token update)");
            }
        }
        return true;
    }


    public boolean getResource(CoapClient client, String resourceUri)
            throws ConnectorException, IOException {

        CoapResponse res = doGetRequest(client, resourceUri);
        System.out.println("\nResponse Code:       " + res.getCode() + " - " + res.advanced().getCode().name());

        if (res.getCode().isSuccess()) {
            System.out.println("Response Message:    " + res.getResponseText() + "\n");
        } else if (res.getCode().isServerError() || res.getCode().isClientError()) {

            if (res.getOptions().getContentFormat() == Constants.APPLICATION_ACE_CBOR) {
                // print AS Request Creation Hints
                System.out.println("Response Message:    " +
                        AsRequestCreationHints.parseHints(CBORObject.DecodeFromBytes(res.getPayload())) + "\n");
            }
            // increase the counter only if UNAUTHZ is received
            return !res.getCode().equals(CoAP.ResponseCode.UNAUTHORIZED);
        }
        return true;
    }


    public CoapResponse doGetRequest(CoapClient client, String resourceUri)
            throws ConnectorException, IOException {

        client.setURI(resourceUri);

        Request request = new Request(CoAP.Code.GET);
        request.getOptions().setOscore(new byte[0]);
        return client.advanced(request);
    }

    // post method. Need to modify the Requester. What do I post, a random value?
//    public CoapResponse doPostRequest(CoapClient client, String resourceUri, String payload)
//            throws ConnectorException, IOException {
//
//        client.setURI(resourceUri);
//
//        Request request = new Request(CoAP.Code.POST);
//        request.getOptions().setOscore(new byte[0]);
//        request.getOptions().setContentFormat(Constants.APPLICATION_ACE_CBOR);
//        CBORObject payloadCbor  = CBORObject.FromObject(payload);
//        request.setPayload(payloadCbor.EncodeToBytes());
//        return client.advanced(request);
//    }

    private void parseInputs() throws ParameterException {

        if (scope.size() > 1 || rsUri.size() > 1 || aud.size() > 1) {
            if (scope.size() != rsUri.size() || rsUri.size() != aud.size()) {
                throw new ParameterException(spec.commandLine(),
                        "\nWhen specifying more than one --aud, --scope, or --rsuri, \n" +
                                "the complete list of triplets must be given.\n" +
                                "If one occurrence of --aud, --scope, or --rsuri is found, that value is used \n" +
                                "for the given arguments, and default value is used for the other arguments.\n\n" +
                                "Example: --aud rs1 --scope \"scope1\" \n    is valid.\n" +
                                "The default value for --rsuri will be used.\n\n" +
                                "Example: --aud rs1 --scope \"scope1\" --aud rs2 \n    is NOT valid.\n" +
                                "If two --aud are specified, two --scope and two --rsuri must be specified.\n" +
                                "The first occurrence of each argument composes a triplets.\n");
            }
        }

        // check asUri and prepend the protocol if needed
        asUri = Utils.validateUri(asUri, spec);
        // check rsUri and prepend the protocol if needed
        for (int i = 0; i < rsUri.size(); i++) {
            rsUri.set(i, Utils.validateUri(rsUri.get(i), spec));
        }

        // convert senderId input from hex string to byte array
        sId = Utils.hexStringToByteArray(senderId, spec);

        // convert the OSCORE master secret from string to byte array
        key128 = key.getBytes(Constants.charset);

        // parse revoked tokens notification type
        try {
            isObserve = this.args.notification.observeArgs.observe;
        } catch (NullPointerException e) {
            isObserve = false;
        }
        try {
            isPolling = this.args.notification.pollingArgs.polling;
        } catch (NullPointerException e) {
            isPolling = false;
        }
        try {
            pollingInterval = this.args.notification.pollingArgs.interval;
        } catch (NullPointerException e) {
            pollingInterval = DEFAULT_POLLING_INTERVAL;
        }
        try {
            trlAddr = this.args.trlAddrArg.trlAddress;
        } catch (NullPointerException e) {
            trlAddr = DEFAULT_TRL_ADDR;
        }

        // parse DHT arguments
        try {
            isDhtEnabled = this.args.dhtArg.isDhtEnabled;
            if (isDhtEnabled) {
                try {
                    dhtAddr = this.args.dhtArg.dhtUri;
                } catch (NullPointerException e) {
                    dhtAddr = DEFAULT_DHT_ADDRESS;
                }
                try {
                    incomingTopic = this.args.dhtArg.incomingTopic;
                } catch (NullPointerException e) {
                    incomingTopic = DEFAULT_INCOMING_TOPIC;
                }
                try {
                    outgoingTopic = this.args.dhtArg.outgoingTopic;
                } catch (NullPointerException e) {
                    outgoingTopic = DEFAULT_OUTGOING_TOPIC;
                }
            }
        } catch (NullPointerException e) {
            isDhtEnabled = false;
            dhtAddr = DEFAULT_DHT_ADDRESS;
            incomingTopic = DEFAULT_INCOMING_TOPIC;
            outgoingTopic = DEFAULT_OUTGOING_TOPIC;
        }
        if (isDhtEnabled) {
            ClientManager dhtClient = ClientManager.createClient();
            try {
                URI uri = new URI(dhtAddr);
                dhtClient.asyncConnectToServer(this, uri);
            } catch (DeploymentException | URISyntaxException e) {
                System.err.println("Error: Failed to connect to DHT");
                e.printStackTrace();
            }
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("[DHT] - Connected " + session.getId());
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("[DHT] - Session " + session.getId() + " closed because " + closeReason);
    }


    @OnMessage
    public String onMessage(String message, Session session) throws AceException, OSException, ConnectorException, IOException {

        // Parse incoming JSON string from DHT
        JsonIn parsed;
        String topicField;
        try {
            parsed = new Gson().fromJson(message, JsonIn.class);
            topicField = parsed.getVolatile().getValue().getTopic();
        } catch (JsonSyntaxException | NullPointerException e) {
//            System.out.println("[DHT] - Unable to parse JSON. " +
//                    "Its JSON schema either differs from the expected one, " +
//                    "or the JSON is malformed.");
            return null;
        }

        // Check if the topic name matches
        if (!topicField.equals(incomingTopic)) {
            System.out.println("[DHT] - Message discarded. " +
                    "The topics does not match (\"" + topicField + "\" != \"" + incomingTopic + "\")");
            return null;
        }
        System.out.println("[DHT] - The topic matches the one the Client is interested in (\"" + incomingTopic + "\")");

        String scopeField = parsed.getVolatile().getValue().getMessage().getScope();
        String audienceField = parsed.getVolatile().getValue().getMessage().getAudience();
        String addressField = parsed.getVolatile().getValue().getMessage().getAddress();

        String tokenHash;
        try {
            // 1. get the token
            tokenHash = getTokenIfNotPresent(audienceField, scopeField);

            Utils.waitForServer("Resource Server", addressField, 2000L);

            // 2. post the token
            postTokenIfNotPosted(tokenHash, addressField);
        } catch (AceException e) {
            System.out.println(e.getMessage());
            return null;
        }

        TokenInfo tokenInfo = validTokensMap.get(tokenHash);

        CoapClient client4RS = new CoapClient(addressField);
        OSCOREProfileRequests.setClient(client4RS, ctxDB);

        // extract the resources from the scope
        List<String> resources = new ArrayList<>(
                Arrays.asList(tokenInfo.getScope().split(" ")));
        resources.replaceAll(s1 -> s1.substring(s1.indexOf("_") + 1));

        // make a request for each resource and publish the result on the DHT
        List<String> responses = new ArrayList<>();
        for (String res : resources) {
            String resourceUri = addressField + "/" + res;
            CoapResponse response =
                    doGetRequest(client4RS, resourceUri);
            responses.add("Response from " + resourceUri + " : [" + response.toString() + "]");
        }

        // build a single string containing all the responses
        String responseString = String.join(", ", responses);

        // Build outgoing JSON to DHT
        JsonOut outgoing = new JsonOut();
        RequestPubMessage pubMsg = new RequestPubMessage();
        OutValue outVal = new OutValue();
        outVal.setTopic(outgoingTopic);
        outVal.setMessage(responseString);
        pubMsg.setValue(outVal);
        outgoing.setRequestPubMessage(pubMsg);

        String jsonOut = new GsonBuilder().disableHtmlEscaping().create().toJson(outgoing);

        System.out.println("[DHT] - Outgoing JSON: " + jsonOut);

        // publish the json on the DHT
        return jsonOut;
    }

    /**
     * Ask the Access Token to the AS.
     * If the Access Token is obtained, compute its tokenhash,
     * and save information about it in a TokenInfo structure.
     * Also, save in the map validTokensMap the tokenHash and the TokenInfo.
     *
     * @param aud   the audience asked
     * @param scope the scope asked
     * @return the tokenhash of the issued token
     * @throws AceException if some error occurs requesting the token,
     *                      computing the hash, or extracting the parameters
     *                      from the AS response
     */
    public String getTokenAndUpdateValidTokens(String aud, String scope) throws AceException {

        // get the token
        Response asRes;
        try {
            asRes = getToken(client4AS, aud, scope);
        } catch (AceException | OSException e) {
            throw new AceException("Error getting token: " + e.getMessage());
        }

        // extract the payload of the response
        CBORObject resAs = CBORObject.DecodeFromBytes(asRes.getPayload());

        // convert the CBOR object into a map
        Map<Short, CBORObject> map = Constants.getParams(resAs);

        // print the response
        System.out.println("\nResponse from AS");
        System.out.println("Response Code:       " + asRes.getCode());

        // compute the tokenhash
        String tokenHash = Util.computeTokenHash(map.get(Constants.ACCESS_TOKEN));

        // extract the scope for which this token has been issued
        String allowedScopes =
                map.get(Constants.SCOPE) == null ? scope : map.get(Constants.SCOPE).AsString();

        // Create a TokenInfo and add it to the validTokensMap
        validTokensMap.put(tokenHash, new TokenInfo(aud, allowedScopes, asRes, false));
        return tokenHash;
    }

    /**
     * Check if a token for the given audience and scope is already present among
     * the valid tokens. If not, ask for a new token to the AS.
     * In either case, return the tokenHash of the token, which can be used as key
     * in the validTokensMap to retrieve additional information.
     *
     * @param audience the audience
     * @param scope    the scope
     * @return the tokenhash of a token valid for that audience and scope
     * @throws AceException if an error occurs getting the token
     */
    private String getTokenIfNotPresent(String audience, String scope) throws AceException {
        boolean isTokenPresent = false;
        String tokenHash = null;

        for (Map.Entry<String, TokenInfo> pair : validTokensMap.entrySet()) {
            if (pair.getValue().getScope().contains(scope)
                    && pair.getValue().getAudience().contains(audience)) {
                isTokenPresent = true;
                tokenHash = pair.getKey();
                System.out.println("TOKEN ALREADY PRESENT");
                break;
            }
            System.out.println("TOKEN NOT PRESENT");
        }

        if (!isTokenPresent) {
            // get a new token
            tokenHash = getTokenAndUpdateValidTokens(audience, scope);
        }
        return tokenHash;
    }

    /**
     * Check if the token identified by the provided tokenhash has been already posted.
     * If so, do nothing. Otherwise, post the token at the provided address
     *
     * @param tokenHash the tokenhash of the token to check and optionally post
     * @param address   the address of the resource server
     * @throws AceException if an error occurs retrieving the actual token or posting the token
     */
    private void postTokenIfNotPosted(String tokenHash, String address) throws AceException {
        TokenInfo tokenInfo = validTokensMap.get(tokenHash);
        boolean isTokenPosted = tokenInfo.isPosted();

        if (!isTokenPosted) {
            // post the token
            Map<Short, CBORObject> map;
            try {
                map = Constants.getParams(
                        CBORObject.DecodeFromBytes(tokenInfo.getAsResponse().getPayload()));
            } catch (AceException e) {
                throw new AceException("Error retrieving the token from valid tokens: " + e.getMessage());
            }
            try {
                tokenInfo.setPosted(
                        postToken(address, tokenInfo.getAsResponse(), map));
            } catch (OSException e) {
                throw new AceException("Error posting the token: " + e.getMessage());
            }
        }
    }

    private void shutdown() {
        if (client4AS != null)
            client4AS.shutdown();
        if (executorService != null)
            executorService.shutdown();
        for (Timer timer : expTasks) {
            timer.cancel();
        }
    }
}

