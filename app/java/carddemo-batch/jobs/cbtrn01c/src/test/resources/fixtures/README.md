# CBTRN01C parity fixtures

Every `expected/` file except `read-error/` comes from running the **original** `app/cbl/CBTRN01C.cbl` under
GnuCOBOL 3.1.2; none was produced by the Java job. Regenerate with `tools/cobol/gen_fixtures.sh` (see
`tools/cobol/README.md`); the script overwrites every `input/` built from samples and every GnuCOBOL `expected/`.

Per scenario, `expected/` holds:

| File | Derivation |
|---|---|
| `sysout.txt` | byte-exact DISPLAY output of the GnuCOBOL run (`run_scenario.sh`, stdout). |
| `sysout.csv` | `sysout_to_expected.py`: one row per `sysout.txt` line, `line_no`, `line_text` right-trimmed. |
| `daily_tran_verification.csv` | `sysout_to_expected.py`: one row per XREF lookup block parsed from `sysout.txt`. A block starts at a DISPLAYed DALYTRAN record, or, for the EOF re-lookup, at an XREF outcome line without one (`eof_reprocessed=true`, previous record's number and ids). Ids come from the displayed record (offsets of CVTRA06Y), `xref_acct_id`/`xref_cust_id` from the `ACCOUNT ID :`/`CUSTOMER ID:` lines, statuses from the INVALID/SUCCESSFUL lines. |
| `result.properties` | `sysout_to_expected.py`: `return_code` (GnuCOBOL exit status) or `abend_code` (from the `CEE3ABD` stub's stderr), and counters counted from the same SYSOUT lines. |

Scenarios:

| Directory | Inputs | Covers |
|---|---|---|
| `sample-data/` | `app/data/ASCII` dailytran/custdata/cardxref/carddata/acctdata (read by the test via `SampleData.file`); `input/transact.txt` is an empty TRANFILE (no sample unload exists; never read). | 300 records, all verified; 301 lookups (EOF re-lookup of the last record). |
| `xref-not-found/` | `make_edge_inputs.py`: 3 sample records, cards of #2 and #3 replaced by `9999999999999999` / `0000000000000000` (not in xref). | BR-04; unknown card as the last record, so the skip message repeats at EOF. |
| `account-not-found/` | 2 sample records; ACCTFILE holds only the first record's account. | BR-05/06; missing account as the last record, repeated at EOF. |
| `empty-dalytran/` | empty DALYTRAN, 5 xref/account records. | One lookup of the initial (blank) DALYTRAN-RECORD. |
| `mixed-outcomes/` | 5 sample records: verified, unknown card, missing account, verified, verified. | All three outcomes interleaved, EOF re-lookup verifies. |
| `open-failure/<DD>/` | `mixed-outcomes` inputs with that one DD pointed at a nonexistent file (GnuCOBOL status 35). No `input/`: the tests remove the DD from the `mixed-outcomes` inputs. | OPEN abend for each of the six DDs (`sysout.txt`, `result.properties` only; nothing is published on abend). |
| `read-error/` | **hand-derived**: `input/dailytran.txt` = first `mixed-outcomes` record + one 351-byte record. | READ failure abend. GnuCOBOL's LINE/RECORD SEQUENTIAL handling of an over-long record does not match a z/OS QSAM length error, so `expected/` was written by hand from `1000-DALYTRAN-GET-NEXT`/`Z-DISPLAY-IO-STATUS`/`Z-ABEND-PROGRAM`: the first record's normal block (copied from `mixed-outcomes/expected/sysout.txt` lines 2-7), then `ERROR READING DAILY TRANSACTION FILE`, `FILE STATUS IS: NNNN0004` (record-length status 04, the Java mapping documented in `CBTRN01C.md` §4), `ABENDING PROGRAM`, abend 999. |
