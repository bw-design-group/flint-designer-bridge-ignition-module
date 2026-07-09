package dev.bwdesigngroup.flint.common.protocol.methods.tags;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Result for the tags.getProviders method. Returns a list of available tag providers. */
public class TagProvidersResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<TagProviderInfo> providers;

    public TagProvidersResult() {
        this.providers = new ArrayList<>();
    }

    public TagProvidersResult(List<TagProviderInfo> providers) {
        this.providers = providers != null ? providers : new ArrayList<>();
    }

    public List<TagProviderInfo> getProviders() {
        return providers;
    }

    public void setProviders(List<TagProviderInfo> providers) {
        this.providers = providers;
    }

    /** Information about a tag provider. */
    public static class TagProviderInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String type;

        public TagProviderInfo() {}

        public TagProviderInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
