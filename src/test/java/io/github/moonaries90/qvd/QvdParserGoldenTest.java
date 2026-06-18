package io.github.moonaries90.qvd;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class QvdParserGoldenTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void parseProductAaRatioQvdMatchesGolden() throws Exception {
        // The golden JSON embeds real column names and sample rows from proprietary data, so it is
        // gitignored too. Absent on a clean checkout -> skip rather than error.
        Map<String, Object> golden = readOptional("golden/qvd_parse_golden.json");
        assumeTrue(golden != null, "missing local fixture golden/qvd_parse_golden.json");
        String qvdResource = String.valueOf(golden.get("file"));
        QvdTable table;
        InputStream qvdInputStream = QvdParserGoldenTest.class.getClassLoader()
                .getResourceAsStream(qvdResource);
        // The .qvd fixture is binary, proprietary data kept out of git (see .gitignore).
        // On a clean checkout it is absent; skip rather than fail so the build stays green.
        assumeTrue(qvdInputStream != null, "missing local fixture " + qvdResource);
        try {
            table = QvdParser.parse(qvdInputStream);
        } finally {
            qvdInputStream.close();
        }

        assertEquals(((Number) golden.get("no_of_records")).intValue(), table.getRowCount(), "rowCount");
        assertEquals(((Number) golden.get("record_byte_size")).intValue(),
                table.getRecordByteSize(), "recordByteSize");
        assertEquals(golden.get("columns"), table.getColumns(), "columns");
        assertEquals(((Number) golden.get("row_count")).intValue(), table.getRows().size(), "rows.size");

        assertFieldDefs((List<Map<String, Object>>) golden.get("field_defs"), table.getFieldDefs());
        assertSampleRows((List<Map<String, Object>>) golden.get("sample_rows"), table);
        assertDistinctCounts((Map<String, Object>) golden.get("distinct_counts"), table);
    }

    private static void assertFieldDefs(List<Map<String, Object>> expected, List<QvdField> actual) {
        assertEquals(expected.size(), actual.size(), "field_defs.size");
        for (int i = 0; i < expected.size(); i++) {
            Map<String, Object> field = expected.get(i);
            QvdField actualField = actual.get(i);
            String path = "field_defs[" + i + "]";
            assertEquals(field.get("field_name"), actualField.getFieldName(), path + ".fieldName");
            assertEquals(number(field.get("bit_offset")), actualField.getBitOffset(), path + ".bitOffset");
            assertEquals(number(field.get("bit_width")), actualField.getBitWidth(), path + ".bitWidth");
            assertEquals(number(field.get("bias")), actualField.getBias(), path + ".bias");
            assertEquals(number(field.get("no_of_symbols")), actualField.getNoOfSymbols(), path + ".noOfSymbols");
            assertEquals(number(field.get("offset")), actualField.getOffset(), path + ".offset");
            assertEquals(number(field.get("length")), actualField.getLength(), path + ".length");
        }
    }

    private static void assertSampleRows(List<Map<String, Object>> expected, QvdTable table) {
        assertEquals(100, expected.size(), "sample_rows.size");
        for (Map<String, Object> sampleRow : expected) {
            int rowIndex = number(sampleRow.get("index"));
            List<Object> expectedValues = values(sampleRow.get("values"));
            List<Object> actualValues = table.getRows().get(rowIndex);
            assertEquals(expectedValues.size(), actualValues.size(), "sample_rows[" + rowIndex + "].size");
            for (int i = 0; i < expectedValues.size(); i++) {
                assertCell(expectedValues.get(i), actualValues.get(i), "sample_rows[" + rowIndex + "][" + i + "]");
            }
        }
    }

    private static void assertDistinctCounts(Map<String, Object> expected, QvdTable table) {
        for (int columnIndex = 0; columnIndex < table.getColumns().size(); columnIndex++) {
            String column = table.getColumns().get(columnIndex);
            Set<Object> distinct = new HashSet<Object>();
            for (List<Object> row : table.getRows()) {
                distinct.add(row.get(columnIndex));
            }
            assertEquals(number(expected.get(column)), distinct.size(), column + ".distinct");
        }
    }

    private static void assertCell(Object expected, Object actual, String path) {
        if (expected == null) {
            assertNull(actual, path);
            return;
        }
        if (expected instanceof Number) {
            assertNotNull(actual, path);
            assertTrue(actual instanceof Number, path + ".type");
            if (expected instanceof Float || expected instanceof Double) {
                assertTrue(actual instanceof Double, path + ".doubleType");
                assertEquals(((Number) expected).doubleValue(), ((Number) actual).doubleValue(), 0.000000001D, path);
            } else {
                assertTrue(actual instanceof Long, path + ".longType");
                assertEquals(((Number) expected).longValue(), ((Number) actual).longValue(), path);
            }
            return;
        }
        assertEquals(expected, actual, path);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> values(Object value) {
        return (List<Object>) value;
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static Map<String, Object> readOptional(String resource) throws Exception {
        InputStream inputStream = QvdParserGoldenTest.class.getClassLoader().getResourceAsStream(resource);
        if (inputStream == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(inputStream, new TypeReference<Map<String, Object>>() {
            });
        } finally {
            inputStream.close();
        }
    }
}
