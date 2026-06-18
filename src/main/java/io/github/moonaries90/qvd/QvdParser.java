package io.github.moonaries90.qvd;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 纯 JDK QVD 解析器：XML 头、符号表、记录位流三段解析。
 */
public final class QvdParser {

    private static final byte[] HEADER_END = "</QvdTableHeader>".getBytes(StandardCharsets.UTF_8);

    private QvdParser() {
    }

    public static QvdTable parse(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        InputStream inputStream = Files.newInputStream(path);
        try {
            return parse(inputStream);
        } finally {
            inputStream.close();
        }
    }

    public static QvdTable parse(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        return parse(readAll(inputStream));
    }

    public static QvdTable parse(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        HeaderSlice headerSlice = findHeader(bytes);
        Header header = parseHeader(headerSlice.xml);
        List<List<Object>> symbolsByField = readSymbolTables(bytes, headerSlice.symbolStart, header.fields);
        int indexStart = indexStart(headerSlice.symbolStart, header.fields);
        validateIndexSection(bytes, indexStart, header.recordByteSize, header.noOfRecords);

        List<List<Object>> rows = new ArrayList<List<Object>>(header.noOfRecords);
        for (int rowIndex = 0; rowIndex < header.noOfRecords; rowIndex++) {
            int rowStart = indexStart + rowIndex * header.recordByteSize;
            List<Object> row = new ArrayList<Object>(header.fields.size());
            for (int fieldIndex = 0; fieldIndex < header.fields.size(); fieldIndex++) {
                QvdField field = header.fields.get(fieldIndex);
                long symbolIndex = readSymbolIndex(bytes, rowStart, header.recordByteSize, field);
                if (symbolIndex < 0 || symbolIndex >= field.getNoOfSymbols()) {
                    row.add(null);
                } else {
                    row.add(symbolsByField.get(fieldIndex).get((int) symbolIndex));
                }
            }
            rows.add(row);
        }

        return new QvdTable(columns(header.fields), rows, header.fields, header.noOfRecords, header.recordByteSize);
    }

    private static HeaderSlice findHeader(byte[] bytes) throws IOException {
        int headerEnd = find(bytes, HEADER_END);
        if (headerEnd < 0) {
            throw new IOException("QVD 文件缺少 </QvdTableHeader> 头结束标记");
        }
        headerEnd += HEADER_END.length;
        int symbolStart = headerEnd;
        while (symbolStart < bytes.length
                && (bytes[symbolStart] == 0x0D || bytes[symbolStart] == 0x0A || bytes[symbolStart] == 0x00)) {
            symbolStart++;
        }
        String xml = new String(bytes, 0, headerEnd, StandardCharsets.UTF_8);
        if (!xml.isEmpty() && xml.charAt(0) == '\uFEFF') {
            xml = xml.substring(1);
        }
        return new HeaderSlice(xml, symbolStart);
    }

    private static int find(byte[] bytes, byte[] target) {
        for (int i = 0; i <= bytes.length - target.length; i++) {
            boolean matched = true;
            for (int j = 0; j < target.length; j++) {
                if (bytes[i + j] != target[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }

    private static Header parseHeader(String xml) throws IOException {
        Document document = parseXml(xml);
        Element root = document.getDocumentElement();
        int recordByteSize = intChild(root, "RecordByteSize");
        int noOfRecords = intChild(root, "NoOfRecords");
        NodeList fieldNodes = root.getElementsByTagName("QvdFieldHeader");
        List<QvdField> fields = new ArrayList<QvdField>(fieldNodes.getLength());
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element field = (Element) fieldNodes.item(i);
            fields.add(new QvdField(
                    childText(field, "FieldName"),
                    intChild(field, "BitOffset"),
                    intChild(field, "BitWidth"),
                    intChild(field, "Bias"),
                    intChild(field, "NoOfSymbols"),
                    intChild(field, "Offset"),
                    intChild(field, "Length")));
        }
        return new Header(recordByteSize, noOfRecords, fields);
    }

    private static Document parseXml(String xml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("QVD XML 头解析失败", e);
        }
    }

    private static void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
            // 本地可信 QVD 解析不依赖该安全特性；不支持时继续使用 JDK 默认实现。
        }
    }

    private static int intChild(Element element, String tagName) throws IOException {
        String text = childText(element, tagName);
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IOException("QVD XML 字段不是整数: " + tagName + "=" + text, e);
        }
    }

    private static String childText(Element element, String tagName) throws IOException {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && tagName.equals(node.getNodeName())) {
                return node.getTextContent().trim();
            }
        }
        throw new IOException("QVD XML 缺少字段: " + tagName);
    }

    private static List<List<Object>> readSymbolTables(byte[] bytes, int symbolStart, List<QvdField> fields)
            throws IOException {
        List<List<Object>> symbolsByField = new ArrayList<List<Object>>(fields.size());
        for (QvdField field : fields) {
            symbolsByField.add(readSymbols(bytes, symbolStart, field));
        }
        return symbolsByField;
    }

    private static List<Object> readSymbols(byte[] bytes, int symbolStart, QvdField field) throws IOException {
        int start = checkedAdd(symbolStart, field.getOffset(), "符号表起点溢出: " + field.getFieldName());
        int end = checkedAdd(start, field.getLength(), "符号表长度溢出: " + field.getFieldName());
        if (start < 0 || end > bytes.length || start > end) {
            throw new IOException("字段符号表越界: " + field.getFieldName());
        }

        List<Object> symbols = new ArrayList<Object>(field.getNoOfSymbols());
        int cursor = start;
        while (symbols.size() < field.getNoOfSymbols()) {
            if (cursor >= end) {
                throw new IOException("字段符号表过早结束: " + field.getFieldName());
            }
            int type = bytes[cursor++] & 0xFF;
            switch (type) {
                case 0x01:
                    requireBytes(cursor, 4, end, field, "int32");
                    symbols.add(Long.valueOf(ByteBuffer.wrap(bytes, cursor, 4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .getInt()));
                    cursor += 4;
                    break;
                case 0x02:
                    requireBytes(cursor, 8, end, field, "float64");
                    symbols.add(Double.valueOf(ByteBuffer.wrap(bytes, cursor, 8)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .getDouble()));
                    cursor += 8;
                    break;
                case 0x04:
                    cursor = readStringSymbol(bytes, cursor, end, symbols, field);
                    break;
                case 0x05:
                    // dual int 的数值只用于 Qlik 内部排序；解析结果按 Spec 保留显示串。
                    requireBytes(cursor, 4, end, field, "dual_int");
                    cursor = readStringSymbol(bytes, cursor + 4, end, symbols, field);
                    break;
                case 0x06:
                    // dual float 同样取显示串，日期保持 yyyy/MM/dd raw 形式，留给下一阶段规整。
                    requireBytes(cursor, 8, end, field, "dual_float");
                    cursor = readStringSymbol(bytes, cursor + 8, end, symbols, field);
                    break;
                default:
                    throw new IOException("未知 QVD 符号类型 0x"
                            + Integer.toHexString(type) + ": " + field.getFieldName());
            }
        }
        return symbols;
    }

    private static int readStringSymbol(byte[] bytes,
                                        int start,
                                        int end,
                                        List<Object> symbols,
                                        QvdField field) throws IOException {
        int nul = start;
        while (nul < end && bytes[nul] != 0x00) {
            nul++;
        }
        if (nul >= end) {
            throw new IOException("字段字符串符号缺少 NUL 结束符: " + field.getFieldName());
        }
        symbols.add(new String(bytes, start, nul - start, StandardCharsets.UTF_8));
        return nul + 1;
    }

    private static void requireBytes(int cursor, int length, int end, QvdField field, String type) throws IOException {
        if (cursor + length > end) {
            throw new IOException("字段符号表 " + type + " 越界: " + field.getFieldName());
        }
    }

    private static int indexStart(int symbolStart, List<QvdField> fields) throws IOException {
        int binaryLength = 0;
        for (QvdField field : fields) {
            int fieldEnd = checkedAdd(field.getOffset(), field.getLength(), "字段符号区长度溢出: " + field.getFieldName());
            if (fieldEnd > binaryLength) {
                binaryLength = fieldEnd;
            }
        }
        return checkedAdd(symbolStart, binaryLength, "记录位流起点溢出");
    }

    private static void validateIndexSection(byte[] bytes, int indexStart, int recordByteSize, int noOfRecords)
            throws IOException {
        long required = (long) indexStart + (long) recordByteSize * (long) noOfRecords;
        if (recordByteSize < 0 || noOfRecords < 0 || indexStart < 0 || required > bytes.length) {
            throw new IOException("QVD 记录位流长度不足");
        }
    }

    private static long readSymbolIndex(byte[] bytes, int rowStart, int recordByteSize, QvdField field)
            throws IOException {
        long rawValue = 0L;
        for (int pos = 0; pos < field.getBitWidth(); pos++) {
            int absBit = field.getBitOffset() + pos;
            int byteInRecord = absBit / 8;
            int bitInByte = absBit % 8;
            if (byteInRecord >= recordByteSize) {
                throw new IOException("字段位偏移超过记录长度: " + field.getFieldName());
            }
            int value = bytes[rowStart + byteInRecord] & 0xFF;
            if (((value >> bitInByte) & 1) == 1) {
                rawValue |= 1L << pos;
            }
        }
        // Bias 会把 QVD 的保留 raw index 映射为 NULL：负数或越界均按空值处理。
        return rawValue + field.getBias();
    }

    private static List<String> columns(List<QvdField> fields) {
        List<String> columns = new ArrayList<String>(fields.size());
        for (QvdField field : fields) {
            columns.add(field.getFieldName());
        }
        return columns;
    }

    private static byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private static int checkedAdd(int left, int right, String message) throws IOException {
        long value = (long) left + (long) right;
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IOException(message);
        }
        return (int) value;
    }

    private static final class HeaderSlice {
        private final String xml;
        private final int symbolStart;

        private HeaderSlice(String xml, int symbolStart) {
            this.xml = xml;
            this.symbolStart = symbolStart;
        }
    }

    private static final class Header {
        private final int recordByteSize;
        private final int noOfRecords;
        private final List<QvdField> fields;

        private Header(int recordByteSize, int noOfRecords, List<QvdField> fields) {
            this.recordByteSize = recordByteSize;
            this.noOfRecords = noOfRecords;
            this.fields = fields;
        }
    }
}
