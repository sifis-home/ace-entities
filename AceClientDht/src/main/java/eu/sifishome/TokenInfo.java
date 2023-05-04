package eu.sifishome;

import org.eclipse.californium.core.coap.Response;

public class TokenInfo {

    private final String audience;
    private final String scope;
    private final Response asResponse;
    private boolean isPosted;

    public TokenInfo(String audience, String scope, Response asResponse, boolean isPosted) {
        this.audience = audience;
        this.scope = scope;
        this.asResponse = asResponse;
        this.isPosted = isPosted;
    }

    public String getAudience() {
        return audience;
    }

    public String getScope() {
        return scope;
    }

    public void setPosted(boolean posted) {
        isPosted = posted;
    }

    public boolean isPosted() {
        return isPosted;
    }

    public Response getAsResponse() {
        return asResponse;
    }
}
