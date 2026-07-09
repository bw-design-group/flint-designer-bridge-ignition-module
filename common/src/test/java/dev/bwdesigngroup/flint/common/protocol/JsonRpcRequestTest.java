package dev.bwdesigngroup.flint.common.protocol;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JsonRpcRequest")
class JsonRpcRequestTest {

    @Nested
    @DisplayName("Parameterized constructor")
    class ParameterizedConstructor {

        @Test
        @DisplayName("sets jsonrpc to '2.0'")
        void setsJsonrpcVersion() {
            JsonRpcRequest request = new JsonRpcRequest("test", null, 1);
            assertEquals("2.0", request.getJsonrpc());
        }

        @Test
        @DisplayName("sets the method")
        void setsMethod() {
            JsonRpcRequest request = new JsonRpcRequest("executeScript", null, 1);
            assertEquals("executeScript", request.getMethod());
        }

        @Test
        @DisplayName("sets params when provided")
        void setsParams() {
            JsonObject params = new JsonObject();
            params.addProperty("key", "value");
            JsonRpcRequest request = new JsonRpcRequest("test", params, 1);
            assertEquals(params, request.getParams());
        }

        @Test
        @DisplayName("sets null params when not provided")
        void setsNullParams() {
            JsonRpcRequest request = new JsonRpcRequest("test", null, 1);
            assertNull(request.getParams());
        }

        @Test
        @DisplayName("sets the id")
        void setsId() {
            JsonRpcRequest request = new JsonRpcRequest("test", null, 42);
            assertEquals(42, request.getId());
        }

        @Test
        @DisplayName("allows string id")
        void allowsStringId() {
            JsonRpcRequest request = new JsonRpcRequest("test", null, "request-1");
            assertEquals("request-1", request.getId());
        }

        @Test
        @DisplayName("allows null id for notifications")
        void allowsNullId() {
            JsonRpcRequest request = new JsonRpcRequest("test", null, null);
            assertNull(request.getId());
        }
    }

    @Nested
    @DisplayName("Default constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("creates an instance with all fields null")
        void createsInstanceWithNullFields() {
            JsonRpcRequest request = new JsonRpcRequest();
            assertNull(request.getJsonrpc());
            assertNull(request.getMethod());
            assertNull(request.getParams());
            assertNull(request.getId());
        }
    }

    @Nested
    @DisplayName("Setters and getters")
    class SettersAndGetters {

        @Test
        @DisplayName("setJsonrpc and getJsonrpc work correctly")
        void jsonrpcSetterGetter() {
            JsonRpcRequest request = new JsonRpcRequest();
            request.setJsonrpc("2.0");
            assertEquals("2.0", request.getJsonrpc());
        }

        @Test
        @DisplayName("setMethod and getMethod work correctly")
        void methodSetterGetter() {
            JsonRpcRequest request = new JsonRpcRequest();
            request.setMethod("ping");
            assertEquals("ping", request.getMethod());
        }

        @Test
        @DisplayName("setParams and getParams work correctly")
        void paramsSetterGetter() {
            JsonRpcRequest request = new JsonRpcRequest();
            JsonObject params = new JsonObject();
            params.addProperty("code", "print('hello')");
            request.setParams(params);
            assertEquals(params, request.getParams());
        }

        @Test
        @DisplayName("setId and getId work correctly")
        void idSetterGetter() {
            JsonRpcRequest request = new JsonRpcRequest();
            request.setId(99);
            assertEquals(99, request.getId());
        }
    }

    @Nested
    @DisplayName("isValid()")
    class IsValid {

        @Test
        @DisplayName("returns true when jsonrpc is '2.0' and method is non-null and non-empty")
        void validWhenJsonrpcIs2dot0AndMethodIsPresent() {
            JsonRpcRequest request = new JsonRpcRequest("executeScript", null, 1);
            assertTrue(request.isValid());
        }

        @Test
        @DisplayName("returns true when params is null and other fields are valid")
        void validWithNullParams() {
            JsonRpcRequest request = new JsonRpcRequest("ping", null, 1);
            assertTrue(request.isValid());
        }

        @Test
        @DisplayName("returns true when params is a JsonObject")
        void validWithJsonObjectParams() {
            JsonObject params = new JsonObject();
            params.addProperty("scope", "designer");
            JsonRpcRequest request = new JsonRpcRequest("executeScript", params, 1);
            assertTrue(request.isValid());
        }

        @Test
        @DisplayName("returns true for notification (null id) with valid fields")
        void validForNotification() {
            JsonRpcRequest request = new JsonRpcRequest("update", null, null);
            assertTrue(request.isValid());
        }

        @Test
        @DisplayName("returns false when jsonrpc is null")
        void invalidWhenJsonrpcIsNull() {
            JsonRpcRequest request = new JsonRpcRequest();
            request.setMethod("test");
            assertFalse(request.isValid());
        }

        @Test
        @DisplayName("returns false when jsonrpc is not '2.0'")
        void invalidWhenJsonrpcIsNot2dot0() {
            JsonRpcRequest request = new JsonRpcRequest();
            request.setJsonrpc("1.0");
            request.setMethod("test");
            assertFalse(request.isValid());
        }

        @Test
        @DisplayName("returns false when method is null")
        void invalidWhenMethodIsNull() {
            JsonRpcRequest request = new JsonRpcRequest();
            request.setJsonrpc("2.0");
            assertFalse(request.isValid());
        }

        @Test
        @DisplayName("returns false when method is empty string")
        void invalidWhenMethodIsEmpty() {
            JsonRpcRequest request = new JsonRpcRequest("", null, 1);
            assertFalse(request.isValid());
        }

        @Test
        @DisplayName("returns false when both jsonrpc and method are null (default constructor)")
        void invalidForDefaultConstructor() {
            JsonRpcRequest request = new JsonRpcRequest();
            assertFalse(request.isValid());
        }

        @Test
        @DisplayName("returns false when jsonrpc is empty string")
        void invalidWhenJsonrpcIsEmpty() {
            JsonRpcRequest request = new JsonRpcRequest();
            request.setJsonrpc("");
            request.setMethod("test");
            assertFalse(request.isValid());
        }
    }

    @Nested
    @DisplayName("isNotification()")
    class IsNotification {

        @Test
        @DisplayName("returns true when id is null")
        void trueWhenIdIsNull() {
            JsonRpcRequest request = new JsonRpcRequest("update", null, null);
            assertTrue(request.isNotification());
        }

        @Test
        @DisplayName("returns false when id is an integer")
        void falseWhenIdIsInteger() {
            JsonRpcRequest request = new JsonRpcRequest("test", null, 1);
            assertFalse(request.isNotification());
        }

        @Test
        @DisplayName("returns false when id is a string")
        void falseWhenIdIsString() {
            JsonRpcRequest request = new JsonRpcRequest("test", null, "abc");
            assertFalse(request.isNotification());
        }

        @Test
        @DisplayName("returns false when id is zero")
        void falseWhenIdIsZero() {
            JsonRpcRequest request = new JsonRpcRequest("test", null, 0);
            assertFalse(request.isNotification());
        }

        @Test
        @DisplayName("returns true for default constructor (id is null by default)")
        void trueForDefaultConstructor() {
            JsonRpcRequest request = new JsonRpcRequest();
            assertTrue(request.isNotification());
        }

        @Test
        @DisplayName("changes when id is set to null via setter")
        void changesWhenIdSetToNull() {
            JsonRpcRequest request = new JsonRpcRequest("test", null, 1);
            assertFalse(request.isNotification());
            request.setId(null);
            assertTrue(request.isNotification());
        }
    }

    @Nested
    @DisplayName("Params with various JsonElement types")
    class ParamsVariants {

        @Test
        @DisplayName("accepts JsonObject params")
        void acceptsJsonObject() {
            JsonObject params = new JsonObject();
            params.addProperty("code", "system.tag.readBlocking(['path'])");
            params.addProperty("scope", "gateway");
            JsonRpcRequest request = new JsonRpcRequest("executeScript", params, 1);
            assertTrue(request.getParams().isJsonObject());
        }

        @Test
        @DisplayName("accepts JsonPrimitive params")
        void acceptsJsonPrimitive() {
            JsonElement params = new JsonPrimitive("simple-string");
            JsonRpcRequest request = new JsonRpcRequest("test", params, 1);
            assertTrue(request.getParams().isJsonPrimitive());
        }

        @Test
        @DisplayName("accepts JsonNull params")
        void acceptsJsonNull() {
            JsonRpcRequest request = new JsonRpcRequest("test", JsonNull.INSTANCE, 1);
            assertNotNull(request.getParams());
            assertTrue(request.getParams().isJsonNull());
        }
    }
}
