# CBTRN02C GnuCOBOL parity harness

Produces the expected parity fixtures by running the **unmodified** `app/cbl/CBTRN02C.cbl`, never the Java job.

Requirements: GnuCOBOL 3.1.2 with BDB indexed files (`sudo apt-get install -y gnucobol3`), gcc, python3.

```
cd app/java/carddemo-batch/jobs/cbtrn02c
python3 tools/cobol/make_edge_inputs.py                       # rewrites the edge-case input/ dirs
tools/cobol/run_cobol.sh <scenario>/input <scenario>/expected  # one scenario
# sample data:
mkdir -p /tmp/sample && cp ../../../../data/ASCII/{dailytran,cardxref,acctdata,tcatbal}.txt /tmp/sample/
tools/cobol/run_cobol.sh /tmp/sample src/test/resources/fixtures/sample-data/expected
```

| File | Role |
| --- | --- |
| `run_cobol.sh` | Compiles CBTRN02C with `-fsign=EBCDIC` (sign overpunch `{`/`}` as in the samples), builds the indexed files, runs the program with the JobHarness clock, unloads the outputs and writes canonical CSV, `sysout.txt` and `return_code.txt`. |
| `CEE3ABD.cbl` | Stub for the LE abend service: displays its arguments and `STOP RUN RETURNING 99`. |
| `IDXUTIL.cbl` | Loader/unloader: sequential fixed records ⇄ indexed files (XREF, ACCT, TCAT, TRAN) with the copybook keys; unload is in key order. |
| `fixedclock.c` | `LD_PRELOAD` shim freezing `time`/`gettimeofday`/`clock_gettime` at 2022-07-06T00:00:00Z (`FIXED_EPOCH`), so `FUNCTION CURRENT-DATE` matches `JobHarness.CLOCK` and TRAN-PROC-TS is deterministic. |
| `cobol_files.py` | `to-fixed`: LF/CRLF unload → fixed-length records. `to-csv`: fixed records → canonical CSV, with its own copy of the CVTRA05Y/CVTRA06Y/CVACT01Y/CVTRA01Y layouts and overpunch decoding (independent of the Java code). |
| `make_edge_inputs.py` | Writes the hand-built edge-case inputs and documents what each record exercises. |

Canonical CSV: header = schema column names, strings right-trimmed, decimals plain with full scale (`-25.50`),
`daily_rejects` = the 350-byte DALYTRAN image columns + `validation_fail_reason` (4 digits) +
`validation_fail_reason_desc` (trimmed). On abend (RC 99 from the stub) no CSVs are written, matching the Java job,
which publishes no datasets when it abends.

`COBDATFT` is not needed: CBTRN02C only uses `FUNCTION CURRENT-DATE`. `COB_CURRENT_DATE` was tried first but did
not give zero hundredths, hence the clock shim.
