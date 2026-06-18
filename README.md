# java-qvd

A small, **pure-JDK** parser and command-line tool for reading Qlik **QVD** (QlikView Data) files.

The parser has **zero runtime dependencies** — only the Java standard library (`java.*`, `javax.xml`,
`org.w3c.dom`). JUnit 5 and Jackson are used for tests only and are never shipped in the artifact.

## Requirements

- Java 8 or newer
- Maven 3.6+ (to build)

## Build

```bash
mvn package            # compile, test, and produce target/java-qvd.jar
mvn package -DskipTests
```

Because there are no runtime dependencies, the resulting `target/java-qvd.jar` is directly runnable.

## CLI usage

```bash
java -jar target/java-qvd.jar <file.qvd>            # print schema + record count (default)
java -jar target/java-qvd.jar <file.qvd> --csv      # write all rows as CSV to stdout
java -jar target/java-qvd.jar <file.qvd> --head N   # write the first N rows as CSV
java -jar target/java-qvd.jar --help
```

Example (`info` mode, schema only — values shown are illustrative):

```
记录数 (NoOfRecords): 1000
行数 (rows):          1000
记录字节宽度:         8
列数:                 3

字段定义:
  name                       bitOff     bitW   bias    symbols     offset     length
  id                              0       10     -2        512          0       3072
  name                           10       12     -2       1024       3072      18000
  amount                         22        9     -2        400      21072       4800
```

CSV output is UTF-8. Cells are quoted when they contain a comma, quote, or newline; `null` cells are
written as empty fields.

## Library usage

```java
import io.github.moonaries90.qvd.QvdParser;
import io.github.moonaries90.qvd.QvdTable;
import java.nio.file.Paths;
import java.util.List;

QvdTable table = QvdParser.parse(Paths.get("data.qvd"));

table.getColumns();        // List<String> of field names
table.getRowCount();       // declared NoOfRecords
table.getRecordByteSize(); // bytes per record in the bit-stream

for (List<Object> row : table.getRows()) {
    // each cell is a String, Long, Double, or null
}
```

`QvdParser` also accepts an `InputStream` or a raw `byte[]`. All cell values are normalized to one of
`String`, `Long`, `Double`, or `null`.

## How it works

A QVD file is parsed in three stages (`QvdParser`):

1. **XML header** — the text up to `</QvdTableHeader>` is parsed as XML to read `RecordByteSize`,
   `NoOfRecords`, and the per-field `QvdFieldHeader` definitions.
2. **Symbol tables** — for each field, a typed list of distinct values: int32 (`0x01`), float64
   (`0x02`), string (`0x04`), dual-int (`0x05`), and dual-float (`0x06`). Dual types keep the
   display string.
3. **Record bit-stream** — each row is `RecordByteSize` bytes; for every field, `BitWidth` bits at
   `BitOffset` are read little-endian and `Bias` is added to get the symbol index. A negative or
   out-of-range index resolves to `null`.

The XML parser is configured to disable DTDs and external entities.

## Tests

`QvdParserGoldenTest` checks the parser against a golden snapshot (record count, columns, field
definitions, sample rows, and per-column distinct counts).

The test fixtures — the binary `.qvd` file and the golden JSON derived from it — are **not committed**
(they are listed in `.gitignore`). When they are absent, the test **skips** rather than fails, so a
clean checkout still builds green. To run it against your own data, place a `.qvd` file and a matching
golden JSON under `src/test/resources/`.

## License

[MIT](LICENSE)
