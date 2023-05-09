package eu.sifishome.json.outgoing;

public class JsonOut {

    private RequestPubMessage RequestPubMessage;

    public JsonOut() {

    }

    public void setRequestPubMessage(RequestPubMessage requestPubMessage) {
        RequestPubMessage = requestPubMessage;
    }

    public RequestPubMessage getRequestPubMessage() {
        return RequestPubMessage;
    }

}
