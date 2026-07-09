package dev.bwdesigngroup.flint.common.protocol.methods.browser;

import java.util.Collections;
import java.util.List;

/**
 * Result for the browser.getCdpInfo method. Contains CDP availability info and discovered targets.
 */
public class BrowserCdpInfoResult {
    private boolean available;
    private int port;
    private List<CdpTarget> targets;

    public BrowserCdpInfoResult() {}

    public BrowserCdpInfoResult(boolean available, int port, List<CdpTarget> targets) {
        this.available = available;
        this.port = port;
        this.targets = targets;
    }

    public static BrowserCdpInfoResult available(int port, List<CdpTarget> targets) {
        return new BrowserCdpInfoResult(true, port, targets);
    }

    public static BrowserCdpInfoResult unavailable() {
        return new BrowserCdpInfoResult(false, 0, Collections.emptyList());
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public List<CdpTarget> getTargets() {
        return targets;
    }

    public void setTargets(List<CdpTarget> targets) {
        this.targets = targets;
    }
}
