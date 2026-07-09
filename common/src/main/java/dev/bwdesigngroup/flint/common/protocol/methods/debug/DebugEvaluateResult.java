package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;

/** Result for the debug.evaluate method. */
public class DebugEvaluateResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private String result;
    private String type;
    private int variablesReference;
    private Integer namedVariables;
    private Integer indexedVariables;

    public DebugEvaluateResult() {}

    public DebugEvaluateResult(String result, String type) {
        this.result = result;
        this.type = type;
        this.variablesReference = 0;
    }

    public DebugEvaluateResult(String result, String type, int variablesReference) {
        this.result = result;
        this.type = type;
        this.variablesReference = variablesReference;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
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
