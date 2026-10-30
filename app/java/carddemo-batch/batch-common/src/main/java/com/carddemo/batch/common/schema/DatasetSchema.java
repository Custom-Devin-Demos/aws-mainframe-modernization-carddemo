package com.carddemo.batch.common.schema;

import com.carddemo.batch.common.record.FieldSpec;
import com.carddemo.batch.common.record.RecordLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;

/**
 * Schema of one output dataset. Serialises to the Spark {@code StructType} JSON that the
 * Databricks loader passes to {@code StructType.fromJson}, and to the Avro schema used to write Parquet.
 */
public final class DatasetSchema {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final String name;
    private final String description;
    private final List<Column> columns;

    public DatasetSchema(String name, String description, List<Column> columns) {
        this.name = name;
        this.description = description;
        this.columns = List.copyOf(columns);
    }

    /**
     * Schema straight from a copybook layout, FILLER dropped. PIC X and unsigned PIC 9 without
     * decimals become STRING (codes/ids keep leading zeros); signed or scaled numerics become DECIMAL.
     */
    public static DatasetSchema fromLayout(String name, String description, RecordLayout layout) {
        List<Column> cols = new ArrayList<>();
        int offset = 0;
        for (FieldSpec f : layout.fields()) {
            if (!f.isFiller()) {
                Map<String, Object> md = new LinkedHashMap<>();
                md.put("cobol_field", f.cobolName());
                md.put("copybook", layout.copybook());
                md.put("pic", f.picture());
                md.put("offset", offset + 1);
                md.put("length", f.length());
                ColumnType type = f.kind() == FieldSpec.Kind.DECIMAL ? ColumnType.decimal(f.precision(), f.scale()) : ColumnType.STRING;
                cols.add(new Column(f.columnName(), type, false, f.cobolName(), md));
            }
            offset += f.length();
        }
        return new DatasetSchema(name, description, cols);
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public List<Column> columns() {
        return columns;
    }

    public Column column(String colName) {
        return columns.stream().filter(c -> c.name().equals(colName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no column " + colName + " in " + name));
    }

    /** Spark StructType JSON, deterministic key ordering, trailing newline. */
    public String toSparkJson() {
        ObjectNode root = JSON.createObjectNode();
        root.put("type", "struct");
        ArrayNode fields = root.putArray("fields");
        for (Column c : columns) {
            ObjectNode f = fields.addObject();
            f.put("name", c.name());
            f.put("type", c.type().sparkType());
            f.put("nullable", c.nullable());
            Map<String, Object> md = new LinkedHashMap<>(c.metadata());
            if (c.description() != null) {
                md.put("comment", c.description());
            }
            f.set("metadata", JSON.valueToTree(md));
        }
        try {
            return JSON.writeValueAsString(root) + "\n";
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Schema toAvroSchema() {
        SchemaBuilder.FieldAssembler<Schema> fa = SchemaBuilder.record(name).namespace("com.carddemo.batch").fields();
        for (Column c : columns) {
            Schema s = switch (c.type().kind()) {
                case STRING -> Schema.create(Schema.Type.STRING);
                case INT -> Schema.create(Schema.Type.INT);
                case LONG -> Schema.create(Schema.Type.LONG);
                case BOOLEAN -> Schema.create(Schema.Type.BOOLEAN);
                case DECIMAL -> LogicalTypes.decimal(c.type().precision(), c.type().scale()).addToSchema(Schema.create(Schema.Type.BYTES));
            };
            if (c.nullable()) {
                fa = fa.name(c.name()).type(Schema.createUnion(Schema.create(Schema.Type.NULL), s)).withDefault(null);
            } else {
                fa = fa.name(c.name()).type(s).noDefault();
            }
        }
        return fa.endRecord();
    }
}
