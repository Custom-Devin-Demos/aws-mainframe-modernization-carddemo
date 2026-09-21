# Fixtures (Child C)

Golden input/expected data for parity tests of the CBACT04C reimplementation. The `expected/`
files were produced by **running the COBOL program** (`app/cbl/CBACT04C.cbl`, with its VSAM
access swapped for line-sequential files) under GnuCOBOL — see `tools/cobol/README.md` for the
harness and `tools/generate.sh` for the exact command sequence. Nothing in `expected/` was
hand-computed.

```
fixtures/<scenario>/
  input/tcatbal.txt      CVTRA01Y  50 bytes/line  TCATBALF (sorted by key = KSDS order)
  input/discgrp.txt      CVTRA02Y  50 bytes/line  DISCGRP
  input/acctdata.txt     CVACT01Y 300 bytes/line  ACCTFILE
  input/cardxref.txt     CVACT03Y  50 bytes/line  XREFFILE
  expected/systran.txt   CVTRA05Y 350 bytes/line  TRANSACT written by 1300-B-WRITE-TX
  expected/acctdata.txt  CVACT01Y 300 bytes/line  ACCTFILE after all 1050-UPDATE-ACCOUNT, input order
  expected/run.log       stdout of the run (DISPLAY lines: every TCATBAL record, DEFAULT-group fallbacks)
  README.md              scenario description
```

Regenerate everything with

```
cd java/interest-service && tools/generate.sh            # or: tools/generate.sh <scenario>
```

(`PARM_DATE=YYYYMMDDHH` overrides the JCL PARM, default `2022071800` = `app/jcl/INTCALC.jcl` STEP15.)
`tools/build_fixture_inputs.py` rebuilds the `input/` directories from `app/data/ASCII/` plus the
scenario edits, so the hand edits are reproducible too.

## Encoding

* ASCII, LF line endings, one fixed-width record per line, no separators. Trailing spaces are
  significant (records are padded to the copybook length).
* Signed `PIC S9(n)V99` fields are zoned decimal with the sign over-punched on the **last** digit,
  mainframe style: `{`=+0, `A`..`I`=+1..+9, `}`=-0, `J`..`R`=-1..-9. Unsigned digits are plain
  `0`-`9`. Two implied decimals, no decimal point. Examples: `0000000012E` = +1.25,
  `0000000520Q` = -52.08, `0000000100}` = -10.00, `0000000000{` = +0.00.
* Unsigned `PIC 9(n)` fields are left-zero-padded digits; `PIC X(n)` fields are left-justified,
  space-padded.

## Record layouts (1-based byte positions)

### TCATBAL — CVTRA01Y (50)

| Bytes | Field | PIC |
|---|---|---|
| 1-11 | TRANCAT-ACCT-ID | 9(11) |
| 12-13 | TRANCAT-TYPE-CD | X(02) |
| 14-17 | TRANCAT-CD | 9(04) |
| 18-28 | TRAN-CAT-BAL | S9(09)V99 |
| 29-50 | FILLER | X(22) |

Key = bytes 1-17. **The shipped `app/data/ASCII/tcatbal.txt` has TRAN-CAT-BAL = +0.00
(`0000000000{`) on all 50 records** (all `01`/`0001`), hence the `nonzero-balances` scenario.

### DISCGRP — CVTRA02Y (50)

| Bytes | Field | PIC |
|---|---|---|
| 1-10 | DIS-ACCT-GROUP-ID | X(10) |
| 11-12 | DIS-TRAN-TYPE-CD | X(02) |
| 13-16 | DIS-TRAN-CAT-CD | 9(04) |
| 17-22 | DIS-INT-RATE | S9(04)V99 (annual %, e.g. `00150{` = 15.00) |
| 23-50 | FILLER | X(28) |

Shipped groups: `A000000000`, `DEFAULT`, `ZEROAPR` (17 type/category rows each). `ZEROAPR` is
all 0.00; `A000000000` and `DEFAULT` differ only for `07`/`0001` (15.00 vs 0.00).

### ACCTDATA — CVACT01Y (300)

| Bytes | Field | PIC |
|---|---|---|
| 1-11 | ACCT-ID | 9(11) |
| 12 | ACCT-ACTIVE-STATUS | X(01) |
| 13-24 | ACCT-CURR-BAL | S9(10)V99 |
| 25-36 | ACCT-CREDIT-LIMIT | S9(10)V99 |
| 37-48 | ACCT-CASH-CREDIT-LIMIT | S9(10)V99 |
| 49-58 | ACCT-OPEN-DATE | X(10) |
| 59-68 | ACCT-EXPIRAION-DATE | X(10) |
| 69-78 | ACCT-REISSUE-DATE | X(10) |
| 79-90 | ACCT-CURR-CYC-CREDIT | S9(10)V99 |
| 91-102 | ACCT-CURR-CYC-DEBIT | S9(10)V99 |
| 103-112 | ACCT-ADDR-ZIP | X(10) |
| 113-122 | ACCT-GROUP-ID | X(10) |
| 123-300 | FILLER | X(178) |

Note: in the shipped data the value `A000000000` sits in **ACCT-ADDR-ZIP** (103-112) and
**ACCT-GROUP-ID is blank** for all 50 accounts, so CBACT04C gets status `23` on the first
DISCGRP read and falls back to the `DEFAULT` group for every shipped account.
1050-UPDATE-ACCOUNT changes only bytes 13-24 (`ADD WS-TOTAL-INT`) and sets 79-90 / 91-102 to
+0.00 (already +0.00 in all shipped records).

### CARDXREF — CVACT03Y (50)

| Bytes | Field | PIC |
|---|---|---|
| 1-16 | XREF-CARD-NUM | X(16) |
| 17-25 | XREF-CUST-ID | 9(09) |
| 26-36 | XREF-ACCT-ID | 9(11) |
| 37-50 | FILLER | X(14) |

CBACT04C reads this by the alternate key XREF-ACCT-ID. The shipped file has 36-byte lines; the
fixture copies are padded to 50.

### SYSTRAN — CVTRA05Y (350), written by 1300-B-WRITE-TX

| Bytes | Field | PIC | Value written by CBACT04C |
|---|---|---|---|
| 1-16 | TRAN-ID | X(16) | PARM-DATE (10) + WS-TRANID-SUFFIX 9(06), i.e. `2022071800000001`, `...002`, ... (suffix counts across all accounts) |
| 17-18 | TRAN-TYPE-CD | X(02) | `01` |
| 19-22 | TRAN-CAT-CD | 9(04) | `0005` |
| 23-32 | TRAN-SOURCE | X(10) | `System    ` |
| 33-132 | TRAN-DESC | X(100) | `Int. for a/c ` + ACCT-ID(11), space padded |
| 133-143 | TRAN-AMT | S9(09)V99 | WS-MONTHLY-INT = TRUNC(TRAN-CAT-BAL * DIS-INT-RATE / 1200, 2) |
| 144-152 | TRAN-MERCHANT-ID | 9(09) | `000000000` |
| 153-202 | TRAN-MERCHANT-NAME | X(50) | spaces |
| 203-252 | TRAN-MERCHANT-CITY | X(50) | spaces |
| 253-262 | TRAN-MERCHANT-ZIP | X(10) | spaces |
| 263-278 | TRAN-CARD-NUM | X(16) | XREF-CARD-NUM of the account |
| 279-304 | TRAN-ORIG-TS | X(26) | **nondeterministic** `YYYY-MM-DD-HH.MM.SS.mm0000` (run time) |
| 305-330 | TRAN-PROC-TS | X(26) | **nondeterministic**, same value as TRAN-ORIG-TS |
| 331-350 | FILLER | X(20) | spaces |

### Nondeterministic columns

Only `systran.txt` bytes **279-330** (TRAN-ORIG-TS + TRAN-PROC-TS) depend on the wall clock
(`Z-GET-DB2-FORMAT-TIMESTAMP` uses `FUNCTION CURRENT-DATE`; the last four characters are always
`0000`). Parity tests must mask/ignore these 52 bytes (or compare only their format,
`\d{4}-\d{2}-\d{2}-\d{2}\.\d{2}\.\d{2}\.\d{6}`). Everything else, including `expected/acctdata.txt`
and `run.log`, is deterministic for a given input and PARM-DATE.

## Program behaviour reproduced by the fixtures

* Interest = `(TRAN-CAT-BAL * DIS-INT-RATE) / 1200`, **truncated** (no `ROUNDED`) toward zero to
  2 decimals; e.g. 100.53 * 15 / 1200 = 1.256625 -> 1.25, 47.99 * 15 / 1200 = 0.599875 -> 0.59.
* A transaction is written for every TCATBAL record whose (resolved) rate is not 0 — even when the
  truncated amount is 0.00. Rate 0 => no transaction, but the balance is still counted in the
  control break.
* Missing `ACCT-GROUP-ID` / type / category in DISCGRP => status `23` => retry with group
  `DEFAULT` (`run.log` shows `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`).
* **The last account in TCATBALF is never posted.** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` in the
  main loop is unreachable (`PERFORM UNTIL END-OF-FILE = 'Y'` exits first), so the final control
  group's transactions are written but its `ACCT-CURR-BAL` is left unchanged. All other accounts
  are posted when the next account's first record is read.

## Scenarios

### `sample-data`

`app/data/ASCII/*` unchanged apart from normalisation (CRLF->LF, padding to record length, sort
by key). 50 accounts x 1 category (`01`/`0001`), all balances +0.00, all groups blank -> DEFAULT
15.00%.

* expected transactions: **50**, one per account, TRAN-AMT `0000000000{` (+0.00), TRAN-ID
  `2022071800000001` .. `2022071800000050`.
* expected posted balances: unchanged for all 50 accounts (`expected/acctdata.txt` is
  byte-identical to `input/acctdata.txt`).

### `nonzero-balances`

Shipped data with 12 hand-edited TCATBAL records over accounts 1-7 (accounts 8-50 have no
TCATBAL records and are untouched), groups set on accounts 1-3 and 5-7, and account 3's balance
set to -500.00. See `nonzero-balances/README.md` for the per-record table.

* expected transactions: **10** (2 records skipped for rate 0 on acct 1 and 2 on acct 2 / ZEROAPR):
  +1.25, -52.08, +15.43, -0.01, +2083333.33, +0.59, -10.00, +10.00, +0.00, +15.00.
* expected posted balances (bytes 13-24 of `expected/acctdata.txt`):

| Acct | Group | Input bal | Total interest | Expected bal | Note |
|---|---|---|---|---|---|
| 1 | A000000000 | 194.00 | -50.83 | **143.17** | truncation 1.256625 -> 1.25 |
| 2 | ZEROAPR | 158.00 | (no tx) | 158.00 | rewritten unchanged |
| 3 | NOSUCHGRP | -500.00 | +15.42 | **-484.58** | group missing -> DEFAULT |
| 4 | (blank) | 40.00 | +2083333.92 | **2083373.92** | DEFAULT; 0.599875 -> 0.59 |
| 5 | A000000000 | 345.00 | 0.00 | 345.00 | +10.00 and -10.00 cancel |
| 6 | A000000000 | 218.00 | 0.00 | 218.00 | 0.000208 -> 0.00 tx written |
| 7 | A000000000 | 193.00 | +15.00 (not posted) | 193.00 | last account: tx written, balance not updated |

### `single-account`

Account 1 only (group set to `A000000000`), its single card in cardxref, full discgrp, three
categories `01`/`0001..0003`.

* expected transactions: **3**: +12.50 (1000.00 @ 15%), -6.94 (-333.33 @ 25%, -6.944375
  truncated), +0.01 (0.50 @ 25%, 0.010416 truncated).
* expected posted balance: **194.00, unchanged** — account 1 is the last (only) account, so
  1050-UPDATE-ACCOUNT never runs for it (would be 199.57 if it did).
