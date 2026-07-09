package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;
import java.util.List;

/** Result for the debug.getVariables method. */
public class DebugVariablesResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<Variable> variables;

    public DebugVariablesResult() {}

    public DebugVariablesResult(List<Variable> variables) {
        this.variables = variables;
    }

    public List<Variable> getVariables() {
        return variables;
    }

    public void setVariables(List<Variable> variables) {
        this.variables = variables;
    }

    /** A variable with its value. */
    public static class Variable implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String value;
        private String type;
        private int variablesReference;
        private Integer namedVariables;
        private Integer indexedVariables;

        public Variable() {}

        public Variable(String name, String value, String type) {
            this.name = name;
            this.value = value;
            this.type = type;
            this.variablesReference = 0;
        }

        public Variable(String name, String value, String type, int variablesReference) {
            this.name = name;
            this.value = value;
            this.type = type;
            this.variablesReference = variablesReference;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getVariablesReference() {
            return variablesReference;
        }

        public void setVariablesReference(int variablesReference) {
            this.variablesReference = variablesReference;
        }

        public Integer getNamedVariables() {
            return namedVariables;
        }

        public void setNamedVariables(Integer namedVariables) {
            this.namedVariables = namedVariables;
        }

        public Integer getIndexedVariables() {
            return indexedVariables;
        }

        public void setIndexedVariables(Integer indexedVariables) {
            this.indexedVariables = indexedVariables;
        }
    }
}
