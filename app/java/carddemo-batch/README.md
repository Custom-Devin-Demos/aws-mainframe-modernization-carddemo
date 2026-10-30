# CardDemo batch on AWS (Java 17)

Java replacements for the CardDemo COBOL batch programs. Each one runs as a container on AWS Batch (Fargate),
is orchestrated by Step Functions, reads mainframe-format files from S3, and writes Parquet/CSV outputs with
schema files for Databricks. Design: [`docs/modernization/batch-on-aws-design.md`](../../../docs/modernization/batch-on-aws-design.md).

```bash
cd app/java/carddemo-batch
./mvnw -B verify                                  # build all modules and run unit and parity tests
./mvnw -B package -pl jobs/cbtrn02c -am           # build one job's shaded jar
java -jar jobs/cbtrn02c/target/cbtrn02c-job.jar \
  --input DALYTRAN=../../data/ASCII/dailytran.txt ... \
  --output-root /tmp/carddemo-out --run-date 2022-07-06 --run-id local
```

| Module | Purpose |
|---|---|
| `batch-common` | Copybook layouts, zoned-decimal codec, fixed-width/KSDS replacements, Parquet/CSV writers, schema files, S3 storage, launcher |
| `batch-testkit` | Parity-test helpers (`JobHarness`, `ParityAssertions`, `SampleData`, `SchemaContract`) |
| `jobs/<program>` | One module per migrated program |
