# CBTRN03C parity fixtures

Every `expected/` file was produced by running the original `app/cbl/CBTRN03C.cbl` under GnuCOBOL 3.1.2
(`-fsign=EBCDIC`, BDB indexed files) with the harness in `tools/cobol/` (`./generate_fixtures.sh`), never by
the Java job. Per scenario:

* `transaction_report.txt`: the TRANREPT file written by the COBOL program, one 133-byte record per line.
* `transaction_report.csv`: `tools/cobol/run_scenario.py` splits each TRANREPT record into the
  `transaction_report` schema columns. `line_type` comes from the record text (CVTRA07Y literals),
  `page_no` counts `DALYREPT` headers, detail fields are cut at the CVTRA07Y offsets and right-trimmed, and
  `amount` is the inverse of the edited picture (all-space field = `0.00`), plain with scale 2.
* `sysout.txt`: the program's DISPLAY output. Only the `TRAN-AMT ` and `WS-PAGE-TOTAL` lines are
  rewritten from GnuCOBOL's pretty-display (`+000000050.47`) to the z/OS zoned image (`00000005047G`).
* `result.properties`: `return_code` (999 when `CEE3ABD` was called) and `abended`.

Inputs: `<dd>.txt` is a DD file (lower-case DD name); `<dd>.from` names a sample file (repo-relative,
read through `SampleData`); `params.properties` holds `--param` values. Missing files = DD not allocated.
TRANFILE is always the TRANSACT KSDS unload; the REPRO/SORT/INCLUDE step (`tools/cobol/presort.py` for the
COBOL run, `TransactSortStep` in Java) is applied to it with the SORT dates `SORT-START-DATE`/`SORT-END-DATE`,
defaulting to the DATEPARM dates (else 2022-01-01/2022-07-06).

| Scenario | Input | What it exercises |
| --- | --- | --- |
| `sample-data` | `tranfile.txt` = `app/data/ASCII/dailytran.txt` with TRAN-PROC-TS := TRAN-ORIG-TS (`tools/cobol/derive_tranfile.py`, design 7.2); sample CARDXREF/TRANTYPE/TRANCATG; DATEPARM `2022-01-01 2022-07-06` (the TRANREPT SYMNAMES dates) | 300 details, 17 pages, mid-account page breaks, account totals, EOF quirk |
| `presort-filter-order` | 9 records out of key order, dates on/around both bounds, blank TRAN-PROC-TS | REPRO order, inclusive INCLUDE, sort by card with EQUALS |
| `pagination` | 29 + 20 details on two cards | break at `MOD(WS-LINE-COUNTER,20)=0` inside an account; account total stepping over a multiple of 20 (no break) |
| `eof-last-account` | 2 + 3 details | last TRAN-AMT added again at EOF; no account total for the last card |
| `edited-amounts-overflow` | own TRANTYPE/TRANCATG with long descriptions; amounts 0, 0.05, -0.05, 1,000, -1,234,567.89, 2 x 999,999,999.99, 2 x -999,999,999.99 | edited pictures, X(15)/X(29) truncation, S9(09)V99 total truncation |
| `date-filter-next-sentence` | DATEPARM March 2022, SORT dates 2022 | in-program filter `NEXT SENTENCE` ends the loop with no totals |
| `no-selected-records` | nothing in the DATEPARM range | first READ is EOF; initial (space) TRAN-PROC-TS fails the filter, so no report lines at all |
| `empty-dateparm` | empty DATEPARM | status 10 on the DATEPARM READ: no report lines |
| `dateparm-from-params` | no DATEPARM DD, `START-DATE`/`END-DATE` params | DATEPARM record built from parameters (the harness writes the same 80-byte record) |
| `missing-dateparm-abend` | no DATEPARM DD, no params | `ERROR OPENING DATE PARM FILE`, status 35, abend |
| `invalid-xref` / `invalid-trantype` / `invalid-trancatg` | unknown card / type `09` / category `010009` | INVALID KEY, status 23, abend U999 |

Edge-case inputs are written by `tools/cobol/make_edge_inputs.py` (card numbers from `cardxref.txt`).
