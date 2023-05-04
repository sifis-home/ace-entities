package eu.sifishome;

import java.util.Map;
import java.util.TimerTask;

public class ExpirationTask extends TimerTask {

    private final String tokenHash;

    private final Map<String, TokenInfo> validTokensMap;
    public ExpirationTask(String tokenHash, Map<String, TokenInfo> validTokensMap) {
        this.tokenHash = tokenHash;
        this.validTokensMap = validTokensMap;
    }

    @Override
    public void run() {

        System.out.println("Token expired. ");

        synchronized (validTokensMap) {
            validTokensMap.remove(tokenHash);
            validTokensMap.notifyAll();
        }
        // remove the token from validTokensMap

    }
}
