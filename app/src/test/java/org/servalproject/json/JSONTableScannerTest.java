package org.servalproject.json;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JSONTableScannerTest {

    public static class WrappedValue {
        final String value;

        public WrappedValue(String value) {
            this.value = value;
        }
    }

    @Test
    public void narrowAllowsObjectClassForCustomColumnConstruction() throws Exception {
        Object value = JSONTokeniser.narrow("hello", Object.class, JSONTokeniser.Narrow.NO_NULL);
        assertEquals("hello", value);
    }

    @Test
    public void consumeRowArrayBuildsUnsupportedColumnTypes() throws Exception {
        JSONTableScanner scanner = new JSONTableScanner()
                .addColumn("value", WrappedValue.class);

        JSONTokeniser headerJson = new JSONTokeniser(new ByteArrayInputStream(
                "[\"value\"]".getBytes(StandardCharsets.UTF_8)));
        scanner.consumeHeaderArray(headerJson);

        JSONTokeniser rowJson = new JSONTokeniser(new ByteArrayInputStream(
                "[\"hello\"]".getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> row = scanner.consumeRowArray(rowJson);

        Object value = row.get("value");
        assertTrue(value instanceof WrappedValue);
        assertEquals("hello", ((WrappedValue) value).value);
    }
}


