# GnuCOBOL harness for CBACT04C

Used by `tools/generate.sh` to produce the golden files under
`src/test/resources/fixtures/*/expected/` by *running the COBOL program*.

| File | Purpose |
|---|---|
| `CBACT04C-LS.cbl` | Copy of `app/cbl/CBACT04C.cbl` with VSAM KSDS access swapped for LINE SEQUENTIAL files + in-memory keyed lookups. |
| `CBACT04C-LS.diff` | `diff -u -Z app/cbl/CBACT04C.cbl CBACT04C-LS.cbl` — the authoritative list of changes. |
| `CBACT04D.cbl` | Driver that rebuilds the z/OS `EXTERNAL-PARMS` (halfword length + `PARM-DATE`) from the command line and `CALL`s `CBACT04C`. Emulates `PARM='2022071800'` from `app/jcl/INTCALC.jcl` STEP15. |
| `CEE3ABD.cbl` | Stub for the LE abend service so `9999-ABEND-PROGRAM` terminates with RC 99 instead of an unresolved symbol. |

`app/cbl/CBACT04C.cbl` itself is never modified.

## Why a patched copy

The `cobc` on the dev image (`GnuCOBOL 4.0-early-dev`) reports `indexed file handler : disabled`,
so the original `ORGANIZATION IS INDEXED` SELECTs (including the XREF alternate key
`FD-XREF-ACCT-ID`) cannot be compiled or run as-is. Option (b) from the task was taken.

## Exactly what changed (see `CBACT04C-LS.diff`)

Every changed/added line is tagged with `*LS` in columns 1-7 unless it is an edited statement.

1. **FILE-CONTROL**: all five SELECTs become `ORGANIZATION IS LINE SEQUENTIAL` (record key /
   alternate key / access mode clauses dropped). A sixth file `ACCTOUT-FILE` (`ASSIGN TO ACCTOUT`,
   300-byte record) is added to receive the updated account records, since a line-sequential file
   cannot be `REWRITE`n in place.
2. **WORKING-STORAGE**: added `ACCTOUT-STATUS`, `LS-EOF`, `LS-IDX`, and three tables
   (`LS-XREF-TABLE`, `LS-ACCT-TABLE`, `LS-DISC-TABLE`, 20 000 entries each) holding the full
   content of XREFFILE, ACCTFILE and DISCGRP.
3. **0100/0200/0300-*-OPEN**: after a successful `OPEN INPUT`, the whole file is read into its
   table (`LS-LOAD-*`). `0300-ACCTFILE-OPEN` opens `ACCOUNT-FILE` as `INPUT` (was `I-O`) and
   additionally opens `ACCTOUT-FILE` for `OUTPUT`.
4. **1050-UPDATE-ACCOUNT**: `REWRITE FD-ACCTFILE-REC FROM ACCOUNT-RECORD` ->
   `PERFORM LS-REWRITE-ACCOUNT` (stores `ACCOUNT-RECORD` back into the slot found by the last
   `LS-READ-ACCOUNT`, status `'00'`; `'43'` if there was no prior successful read). The arithmetic
   (`ADD WS-TOTAL-INT TO ACCT-CURR-BAL`, zeroing the cycle credit/debit) is untouched.
5. **1100-GET-ACCT-DATA / 1110-GET-XREF-DATA / 1200-GET-INTEREST-RATE / 1200-A-GET-DEFAULT-INT-RATE**:
   the `READ ... INTO ... [KEY IS ...] [INVALID KEY DISPLAY ...] END-READ` statement is replaced by
   `PERFORM LS-READ-<file>` followed (where the original had `INVALID KEY`) by
   `IF <status> = '23' DISPLAY <same messages> END-IF`. The LS paragraphs set the same FILE STATUS
   the program tests for: `'00'` found, `'23'` not found. All status checks, `APPL-RESULT` handling,
   the `'23' -> MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID` fallback and the abend paths are unchanged.
6. **9300-ACCTFILE-CLOSE**: `PERFORM LS-UNLOAD-ACCOUNTS` (writes `LS-ACCT-TABLE` in input order
   to `ACCTOUT-FILE` and closes it) before closing `ACCOUNT-FILE`.
7. **New paragraphs** `LS-LOAD-XREF`, `LS-LOAD-DISCGRP`, `LS-LOAD-ACCOUNTS`, `LS-READ-XREF`
   (alternate-key read: first record whose `XREF-ACCT-ID` matches), `LS-READ-ACCOUNT`,
   `LS-REWRITE-ACCOUNT`, `LS-READ-DISCGRP`, `LS-UNLOAD-ACCOUNTS` appended after
   `9999-ABEND-PROGRAM`.

Untouched, byte-for-byte (verify with the diff): the main loop, `1000-TCATBALF-GET-NEXT`,
`1300-COMPUTE-INTEREST`, `1300-B-WRITE-TX`, `1400-COMPUTE-FEES`, `Z-GET-DB2-FORMAT-TIMESTAMP`,
`9910-DISPLAY-IO-STATUS`, all copybooks.

## KSDS emulation assumptions

* TCATBALF is read sequentially; a KSDS delivers records in ascending key order, so
  `build_fixture_inputs.py` sorts `tcatbal.txt` by `TRAN-CAT-KEY` (bytes 1-17) before the run
  (and the other files by their primary key, which does not affect results).
* Keys are unique in the sample data; `LS-READ-*` returns the first match in file order.
* Line-sequential input records shorter than the FD (the shipped `cardxref.txt` has 36-byte
  lines) are space-padded by GnuCOBOL; `build_fixture_inputs.py` pads them explicitly so the
  fixture inputs are exactly 50/50/300/50 bytes per line.
* `COB_LS_FIXED=1` is set at run time so GnuCOBOL writes line-sequential output records at
  their full fixed length (350 / 300 bytes) instead of trimming trailing spaces.

## Compile flags

```
cobc -c -std=ibm-strict -fsign=EBCDIC -I app/cpy  CBACT04C-LS.cbl
cobc -c -std=ibm-strict -fsign=EBCDIC             CEE3ABD.cbl
cobc -x -std=default  CBACT04D.cbl CBACT04C.o CEE3ABD.o
```

`-fsign=EBCDIC` makes zoned-decimal (`PIC S9 ... DISPLAY`) fields use the mainframe overpunch
characters in ASCII (`{`=+0, `A`-`I`=+1..+9, `}`=-0, `J`-`R`=-1..-9), matching the shipped ASCII
data and the fixtures. `CBACT04D` needs `ACCEPT ... FROM COMMAND-LINE`, which `ibm-strict`
rejects, hence the separate dialect for the driver only.

## Behaviour of the original program worth knowing

* The `ELSE PERFORM 1050-UPDATE-ACCOUNT` branch in the main loop is unreachable
  (`PERFORM UNTIL END-OF-FILE = 'Y'` exits before it), so **the last account in TCATBALF is never
  posted**: its interest transactions are written to TRANSACT but `ACCT-CURR-BAL` is not updated.
  The fixtures reproduce this (see `single-account` and account 7 of `nonzero-balances`).
* `1050-UPDATE-ACCOUNT` runs for every account *change*, including accounts whose total interest
  is zero, so the rewritten record is byte-identical to the input for those.
* `1200-A-GET-DEFAULT-INT-RATE` abends (`CEE3ABD`, U999) if the `DEFAULT` group lacks the
  type/category too; none of the scenarios trigger it.
* `Z-GET-DB2-FORMAT-TIMESTAMP` uses `CURRENT-DATE`; `TRAN-ORIG-TS`/`TRAN-PROC-TS` are
  nondeterministic (see `fixtures/README.md`).
