package dev.bwdesigngroup.flint.common.protocol.methods;

/** Parameters for the authenticate method. */
public class AuthenticateParams {
    private String secret;
    private String clientName;
    private String clientVersion;

    public AuthenticateParams() {}

    public AuthenticateParams(String secret, String clientName, String clientVersion) {
        this.secret = secret;
        this.clientName = clientName;
        this.clientVersion = clientVersion;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }
}
