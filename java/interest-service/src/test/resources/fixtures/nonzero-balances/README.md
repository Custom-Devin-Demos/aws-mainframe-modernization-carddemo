# nonzero-balances

Shipped data with hand edits (applied reproducibly by `nonzero_balances()` in
`tools/build_fixture_inputs.py`), then run through CBACT04C with `tools/generate.sh nonzero-balances`
(PARM-DATE `2022071800`).

Edits to `acctdata.txt` (accounts 8-50 untouched):

| Acct | ACCT-GROUP-ID | ACCT-CURR-BAL |
|---|---|---|
| 1 | `A000000000` | 194.00 (as shipped) |
| 2 | `ZEROAPR` | 158.00 |
| 3 | `NOSUCHGRP` (no DISCGRP rows -> DEFAULT) | **-500.00** |
| 4 | blank (as shipped -> DEFAULT) | 40.00 |
| 5 | `A000000000` | 345.00 |
| 6 | `A000000000` | 218.00 |
| 7 | `A000000000` | 193.00 |

`tcatbal.txt` replaced by these 12 records (key order):

| # | Acct | Type/Cat | TRAN-CAT-BAL | Rate used | Exact interest | Written TRAN-AMT |
|---|---|---|---|---|---|---|
| 1 | 1 | 01/0001 | +100.53 | A000 15.00 | 1.256625 | **+1.25** (truncation) |
| 2 | 1 | 01/0002 | -2500.00 | A000 25.00 | -52.08333 | -52.08 |
| 3 | 1 | 02/0001 | +999.99 | A000 0.00 | — | *no transaction* |
| 4 | 2 | 01/0001 | +5000.00 | ZEROAPR 0.00 | — | *no transaction* |
| 5 | 2 | 03/0001 | -10.00 | ZEROAPR 0.00 | — | *no transaction* |
| 6 | 3 | 01/0001 | +1234.56 | DEFAULT 15.00 | 15.432 | +15.43 |
| 7 | 3 | 04/0002 | -1.00 | DEFAULT 15.00 | -0.0125 | -0.01 |
| 8 | 4 | 01/0003 | +100000000.00 | DEFAULT 25.00 | 2083333.333 | +2083333.33 |
| 9 | 4 | 06/0001 | +47.99 | DEFAULT 15.00 | 0.599875 | **+0.59** (would round to 0.60) |
| 10 | 5 | 05/0001 | -800.00 | A000 15.00 | -10.00 | -10.00 (`0000000100}`) |
| 11 | 5 | 07/0001 | +800.00 | A000 15.00 (DEFAULT has 0.00 here) | 10.00 | +10.00 |
| 12 | 6 | 01/0004 | +0.01 | A000 25.00 | 0.000208 | +0.00 (transaction still written) |
| 13 | 7 | 01/0001 | +1200.00 | A000 15.00 | 15.00 | +15.00 |

Expected `systran.txt`: **10** transactions in the above order, TRAN-ID suffix 000001..000010.

Expected `acctdata.txt` (only ACCT-CURR-BAL, bytes 13-24, changes):

| Acct | Input | Expected | Why |
|---|---|---|---|
| 1 | 194.00 | **143.17** | +1.25 - 52.08 |
| 2 | 158.00 | 158.00 | no interest, record rewritten unchanged |
| 3 | -500.00 | **-484.58** | +15.43 - 0.01 |
| 4 | 40.00 | **2083373.92** | +2083333.33 + 0.59 |
| 5 | 345.00 | 345.00 | +10.00 - 10.00 |
| 6 | 218.00 | 218.00 | +0.00 |
| 7 | 193.00 | 193.00 | **last account in TCATBALF: never posted by CBACT04C** |
| 8-50 | as shipped | unchanged | no TCATBAL records |

`run.log` shows 4 DEFAULT fallbacks (accounts 3 and 4, two categories each).
Bytes 279-330 of `systran.txt` are run-time timestamps and must be masked when comparing.
