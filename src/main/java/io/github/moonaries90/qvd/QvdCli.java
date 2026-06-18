package io.github.moonaries90.qvd;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * QVD 命令行工具：读取一个 .qvd 文件，打印结构信息或导出 CSV。
 *
 * <pre>
 *   java -jar java-qvd.jar &lt;file.qvd&gt;            打印列、字段定义和记录数（默认）
 *   java -jar java-qvd.jar &lt;file.qvd&gt; --csv      把全部行作为 CSV 写到标准输出
 *   java -jar java-qvd.jar &lt;file.qvd&gt; --head N   只导出前 N 行 CSV
 * </pre>
 */
public final class QvdCli {

    private QvdCli() {
    }

    public static void main(String[] args) {
        try {
            run(args, System.out);
        } catch (CliException e) {
            System.err.println("错误: " + e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("读取 QVD 失败: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] args, PrintStream out) throws IOException {
        if (args.length == 0 || isHelp(args[0])) {
            printUsage(out);
            return;
        }

        Path file = Paths.get(args[0]);
        if (!Files.isRegularFile(file)) {
            throw new CliException("文件不存在或不是普通文件: " + file);
        }

        Mode mode = Mode.INFO;
        int headLimit = -1;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--csv".equals(arg)) {
                mode = Mode.CSV;
            } else if ("--head".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new CliException("--head 需要一个行数参数");
                }
                mode = Mode.CSV;
                headLimit = parsePositiveInt(args[++i]);
            } else if (isHelp(arg)) {
                printUsage(out);
                return;
            } else {
                throw new CliException("未知参数: " + arg);
            }
        }

        QvdTable table = QvdParser.parse(file);
        if (mode == Mode.CSV) {
            writeCsv(table, headLimit, out);
        } else {
            printInfo(table, out);
        }
    }

    private static void printInfo(QvdTable table, PrintStream out) {
        out.println("记录数 (NoOfRecords): " + table.getRowCount());
        out.println("行数 (rows):          " + table.getRows().size());
        out.println("记录字节宽度:         " + table.getRecordByteSize());
        out.println("列数:                 " + table.getColumns().size());
        out.println();
        out.println("字段定义:");
        out.printf("  %-24s %8s %8s %6s %10s %10s %10s%n",
                "name", "bitOff", "bitW", "bias", "symbols", "offset", "length");
        for (QvdField field : table.getFieldDefs()) {
            out.printf("  %-24s %8d %8d %6d %10d %10d %10d%n",
                    field.getFieldName(),
                    field.getBitOffset(),
                    field.getBitWidth(),
                    field.getBias(),
                    field.getNoOfSymbols(),
                    field.getOffset(),
                    field.getLength());
        }
    }

    private static void writeCsv(QvdTable table, int headLimit, PrintStream out) throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writeCsvRow(writer, table.getColumns());
        List<List<Object>> rows = table.getRows();
        int limit = headLimit < 0 ? rows.size() : Math.min(headLimit, rows.size());
        for (int i = 0; i < limit; i++) {
            writeCsvRow(writer, rows.get(i));
        }
        writer.flush();
    }

    private static void writeCsvRow(Writer writer, List<?> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(csvCell(values.get(i)));
        }
        writer.write('\n');
    }

    private static String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text;
        if (value instanceof Double) {
            text = formatDouble((Double) value);
        } else {
            text = value.toString();
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private static String formatDouble(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static int parsePositiveInt(String raw) {
        int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new CliException("行数必须是整数: " + raw);
        }
        if (parsed < 0) {
            throw new CliException("行数不能为负: " + raw);
        }
        return parsed;
    }

    private static boolean isHelp(String arg) {
        return "-h".equals(arg) || "--help".equals(arg);
    }

    private static void printUsage(PrintStream out) {
        out.println("用法: java -jar java-qvd.jar <file.qvd> [选项]");
        out.println();
        out.println("选项:");
        out.println("  (无)         打印列、字段定义和记录数");
        out.println("  --csv        把全部行作为 CSV 写到标准输出");
        out.println("  --head N     只导出前 N 行 CSV");
        out.println("  -h, --help   显示本帮助");
    }

    private enum Mode {
        INFO,
        CSV
    }

    private static final class CliException extends RuntimeException {
        CliException(String message) {
            super(message);
        }
    }
}
