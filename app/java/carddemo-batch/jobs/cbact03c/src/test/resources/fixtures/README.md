# CBACT03C test fixtures

Every scenario has `input/` (what the Java job reads as DD `XREFFILE`) and `expected/` (what the COBOL
program produces). **No expected file was produced by running the Java job.**

Expected files:

* `sysout.txt` — SYSOUT exactly as DISPLAYed (record images keep their trailing spaces).
* `sysout.csv` — `line_no,line_text` (`line_text` right-trimmed), only for runs that complete.
* `xref_listing.csv` — `xref_card_num,xref_cust_id,xref_acct_id`, one row per record READ, only for runs that complete.
* `run.properties` — `abended`, `abend_code`, `return_code`.

## Derivation

### GnuCOBOL golden runs (`tools/cobol/run-cobol.sh`)

The unmodified `app/cbl/CBACT03C.cbl` is compiled with GnuCOBOL 3.1.2 (BDB indexed files) plus a
`CEE3ABD` stub. `XREFLOAD.cbl` builds a real indexed file (key = card number) from the ASCII unload, the
program browses it, and `to_canonical.py` turns stdout/stderr/exit status into the files above
(`xref_listing.csv` is sliced from the DISPLAYed record images per CVACT03Y; the two images of each record
are checked to be identical).

| Scenario | Input | What it covers |
|---|---|---|
| `sample-data` | `app/data/ASCII/cardxref.txt` (read via `SampleData.file`, not copied) | 50 records, 102 SYSOUT lines, RC 0 |
| `unsorted-keys` | 6 records in scrambled order, keys starting with digits, `A` and `a` | KSDS browse is in key order (ASCII collation) |
| `unload-format` | CRLF + LF, blank and whitespace-only lines, a 25-byte short line, no final newline | unload parsing; short record space-padded |
| `raw-content` | letters/punctuation in PIC 9 fields, commas/quotes, FILLER text | no validation, FILLER displayed, CSV quoting |
| `empty-file` | empty unload | status 10 on the first READ: START + END only |
| `open-error-missing-file` | no file (`DD_XREFFILE` points to a missing path) | OPEN status 35 → abend U999 |
| `display-io-status` | `input/statuses.txt` (17 statuses) | `9910-DISPLAY-IO-STATUS`, run by `IOSTATDRV`, a driver generated from the original paragraph and its working storage by `gen_iostatdrv.py` |

### Derived by hand from the COBOL paragraph flow

These branches cannot be triggered in the unmodified program on GnuCOBOL with a loadable indexed file,
so the expected SYSOUT was written by hand following `CBACT03C.cbl` (see
`docs/modernization/business-rules/CBACT03C.md`); the status lines were taken from the
`display-io-status` golden CSV. Each `run.properties` carries a comment saying so.

| Scenario | How the Java test triggers it | Expected (COBOL flow) |
|---|---|---|
| `open-error-missing-dd` | no `XREFFILE` DD bound | same as `open-error-missing-file` (status 35 on z/OS and GnuCOBOL) |
| `read-error` | `input/fault.properties`: 3rd READ returns `30` (test-only `XrefFile` wrapper) | START, 2 records ×2, `ERROR READING XREFFILE`, `NNNN0030`, `ABENDING PROGRAM`, abend 999 |
| `close-error` | `input/fault.properties`: CLOSE returns `92` | START, 2 records ×2, `ERROR CLOSING XREFFILE`, `NNNN9050`, `ABENDING PROGRAM`, abend 999 |
| `duplicate-key` | unload with the same card number twice | Java-only: a KSDS cannot hold it; reported as OPEN status 30 → `ERROR OPENING XREFFILE`, `NNNN0030`, abend 999 |
| `overlong-record` | unload with a 55-byte record | same as `duplicate-key` (record exceeds the 50-byte max) |

Abended scenarios have no `sysout.csv`/`xref_listing.csv`: an abend publishes no dataset.
