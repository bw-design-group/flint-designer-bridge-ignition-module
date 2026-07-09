package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;
import java.util.List;

/** Result for the debug.getScopes method. */
public class DebugScopesResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<Scope> scopes;

    public DebugScopesResult() {}

    public DebugScopesResult(List<Scope> scopes) {
        this.scopes = scopes;
    }

    public List<Scope> getScopes() {
        return scopes;
    }

    public void setScopes(List<Scope> scopes) {
        this.scopes = scopes;
    }

    /** A scope within a stack frame. */
    public static class Scope implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private int variablesReference;
        private boolean expensive;

        public Scope() {}

        public Scope(String name, int variablesReference) {
            this.name = name;
            this.variablesReference = variablesReference;
            this.expensive = false;
        }

        public Scope(String name, int variablesReference, boolean expensive) {
            this.name = name;
            this.variablesReference = variablesReference;
            this.expensive = expensive;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getVariablesReference() {
            return variablesReference;
        }

        public void setVariablesReference(int variablesReference) {
            this.variablesReference = variablesReference;
        }

        public boolean isExpensive() {
            return expensive;
        }

        public void setExpensive(boolean expensive) {
            this.expensive = expensive;
        }
    }
}
