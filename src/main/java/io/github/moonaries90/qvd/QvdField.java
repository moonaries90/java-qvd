package io.github.moonaries90.qvd;

import java.util.Objects;

/**
 * QVD 字段头里参与解析记录位流和符号表的最小字段定义。
 */
public final class QvdField {

    private final String fieldName;
    private final int bitOffset;
    private final int bitWidth;
    private final int bias;
    private final int noOfSymbols;
    private final int offset;
    private final int length;

    public QvdField(String fieldName,
                    int bitOffset,
                    int bitWidth,
                    int bias,
                    int noOfSymbols,
                    int offset,
                    int length) {
        this.fieldName = fieldName;
        this.bitOffset = bitOffset;
        this.bitWidth = bitWidth;
        this.bias = bias;
        this.noOfSymbols = noOfSymbols;
        this.offset = offset;
        this.length = length;
    }

    public String getFieldName() {
        return fieldName;
    }

    public int getBitOffset() {
        return bitOffset;
    }

    public int getBitWidth() {
        return bitWidth;
    }

    public int getBias() {
        return bias;
    }

    public int getNoOfSymbols() {
        return noOfSymbols;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QvdField)) {
            return false;
        }
        QvdField qvdField = (QvdField) o;
        return bitOffset == qvdField.bitOffset
                && bitWidth == qvdField.bitWidth
                && bias == qvdField.bias
                && noOfSymbols == qvdField.noOfSymbols
                && offset == qvdField.offset
                && length == qvdField.length
                && Objects.equals(fieldName, qvdField.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, bitOffset, bitWidth, bias, noOfSymbols, offset, length);
    }

    @Override
    public String toString() {
        return "QvdField{"
                + "fieldName='" + fieldName + '\''
                + ", bitOffset=" + bitOffset
                + ", bitWidth=" + bitWidth
                + ", bias=" + bias
                + ", noOfSymbols=" + noOfSymbols
                + ", offset=" + offset
                + ", length=" + length
                + '}';
    }
}
