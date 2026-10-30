# COBTUPDT GnuCOBOL parity harness

Generates `src/test/resources/fixtures/*/expected/` from the **original** `app/app-transaction-type-db2/cbl/COBTUPDT.cbl`.
The Java job is never involved.

GnuCOBOL has no Db2 precompiler, so `EXEC SQL` cannot run as is. The harness replaces only the SQL statements and
keeps every COBOL paragraph, `EVALUATE`, `STRING`, `DISPLAY` and `RETURN-CODE` move of the program unchanged.

| File | Role |
|---|---|
| `precompile.py` | Minimal "precompiler". Copies `COBTUPDT.cbl` and rewrites its five `EXEC SQL` blocks: `INCLUDE SQLCA` becomes `01 SQLCA. 05 SQLCODE PIC S9(9) COMP-5`; `INCLUDE DCLTRTYP` is dropped (COBTUPDT never references it); the `INSERT`/`UPDATE`/`DELETE` statements become `CALL 'TTDBSHIM' USING '<I/U/D>' INPUT-REC-NUMBER INPUT-REC-DESC SQLCODE`. It fails if the SQL text or host variables differ from what it expects. |
| `TTDBSHIM.cbl` | Db2 stand-in over two BDB indexed files: `TTKSDS` (TRANSACTION_TYPE, key TR_TYPE CHAR(2), 50-byte description as stored from the CHAR(50) host variable) and `TCKSDS` (TRANSACTION_TYPE_CATEGORY, key type+category). SQLCODEs: INSERT `0` / `-803` (duplicate key); UPDATE `0` / `+100`; DELETE `0` / `+100` / `-532` (a category row still references the type, ON DELETE RESTRICT). Writes one `op,tr_type,sqlcode` line per call to `SQLTRACE`. |
| `TTLOAD.cbl` | Loads the 60-byte `trantype` (CVTRA03Y) and `trancatg` (CVTRA04Y) unloads into the two indexed files, i.e. the Db2 table state before the run. |
| `TTUNLD.cbl` | Sequential browse of `TTKSDS` after the run (the table as `TRANEXTR` would unload it). |
| `to_fixed.py` | ASCII lines to fixed-length records. INPFILE keeps empty lines as all-spaces 53-byte records (every line is an FB record); the table unloads skip blank lines like `FixedWidthFile`. |
| `to_canonical.py` | Turns the run into canonical fixtures: `transaction_type.csv` (TTUNLD output re-sorted by `TR_TYPE` in EBCDIC/CCSID 37 order, as Db2 for z/OS `ORDER BY` does), `maintenance_log.csv` (rebuilt from the DISPLAY lines and the SQL trace), `sysout.txt` (DISPLAY lines right-trimmed) and `run.properties` (RC + counters derived from the log). |
| `run_scenario.sh` | Runs all of the above for every scenario (or the ones named) and writes `expected/`. |

## Run

```bash
sudo apt-get install -y gnucobol3          # 3.1.2, BDB indexed files
app/java/carddemo-batch/jobs/cobtupdt/tools/cobol/run_scenario.sh            # all scenarios
app/java/carddemo-batch/jobs/cobtupdt/tools/cobol/run_scenario.sh sample-data
```

Work files go to `jobs/cobtupdt/target/cobol-harness/`. A scenario without `input/TRANTYPE.txt` /
`input/TRANCATG.txt` uses `app/data/ASCII/trantype.txt` / `trancatg.txt` (the `sample-data` scenario). The
`libcob: warning: implicit CLOSE` lines come from the shim files closed by `STOP RUN` and are harmless.

## What the shim does not model

* Only the SQLCODEs this table pair can produce: `0`, `+100`, `-803`, `-532`. Other negative SQLCODEs
  (connection, authorization, deadlock) cannot occur against a file snapshot; the COBOL `SQLCODE < 0` branch and
  its message format are still exercised by `-803` and `-532`.
* `-fsign=EBCDIC` is not needed: COBTUPDT has no signed zoned fields in its files (SQLCODE is binary and is
  only DISPLAYed through the edited `PIC ----9`).
* No `CEE3ABD` or `COBDATFT` stub is needed: COBTUPDT calls neither.
