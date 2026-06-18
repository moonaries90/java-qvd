package io.github.moonaries90.qvd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * QVD 解析后的内存表，cell 类型限定为 String、Long、Double 或 null。
 */
public final class QvdTable {

    private final List<String> columns;
    private final List<List<Object>> rows;
    private final List<QvdField> fieldDefs;
    private final int rowCount;
    private final int recordByteSize;

    public QvdTable(List<String> columns,
                    List<List<Object>> rows,
                    List<QvdField> fieldDefs,
                    int rowCount,
                    int recordByteSize) {
        this.columns = Collections.unmodifiableList(new ArrayList<String>(columns));
        this.rows = Collections.unmodifiableList(rows);
        this.fieldDefs = Collections.unmodifiableList(new ArrayList<QvdField>(fieldDefs));
        this.rowCount = rowCount;
        this.recordByteSize = recordByteSize;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public List<QvdField> getFieldDefs() {
        return fieldDefs;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getRecordByteSize() {
        return recordByteSize;
    }
}
