"""Load one CardDemo batch output into a Delta table using its published schema file.

Usage (Databricks notebook / job):
    load_output(spark, "s3://bucket/carddemo", "cbtrn02c", "posted_transactions",
                "2022-07-06", run_id, "carddemo.cbtrn02c_posted_transactions")
"""
import json

from pyspark.sql.types import StructType


def load_output(spark, output_root, program, dataset, run_date, run_id, table):
    run_dir = f"{output_root}/curated/{program}/{dataset}/run_date={run_date}/run_id={run_id}"
    schema_json = "".join(r.value for r in spark.read.text(f"{run_dir}/_schema.json").collect())
    schema = StructType.fromJson(json.loads(schema_json))
    files = [f.path for f in dbutils.fs.ls(run_dir)]  # noqa: F821 (Databricks global)
    if any(p.endswith(".parquet") for p in files):
        df = spark.read.schema(schema).parquet(f"{run_dir}/part-00000.parquet")
    else:
        df = (spark.read.schema(schema)
              .option("header", "true").option("mode", "FAILFAST")
              .option("ignoreLeadingWhiteSpace", "false").option("ignoreTrailingWhiteSpace", "false")
              .csv(f"{run_dir}/part-00000.csv"))
    (df.selectExpr("*", f"DATE'{run_date}' AS run_date", f"'{run_id}' AS run_id")
       .write.format("delta").mode("overwrite")
       .option("replaceWhere", f"run_date = DATE'{run_date}' AND run_id = '{run_id}'")
       .partitionBy("run_date").saveAsTable(table))
