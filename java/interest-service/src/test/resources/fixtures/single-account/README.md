# single-account

One account (1, `ACCT-GROUP-ID` set to `A000000000`, ACCT-CURR-BAL 194.00 as shipped), its single
cardxref row, the full shipped discgrp, and three TCATBAL records. Built by `single_account()` in
`tools/build_fixture_inputs.py`, run with `tools/generate.sh single-account` (PARM-DATE `2022071800`).

| # | Type/Cat | TRAN-CAT-BAL | Rate | Exact | Written TRAN-AMT |
|---|---|---|---|---|---|
| 1 | 01/0001 | +1000.00 | 15.00 | 12.50 | +12.50 |
| 2 | 01/0002 | -333.33 | 25.00 | -6.944375 | -6.94 |
| 3 | 01/0003 | +0.50 | 25.00 | 0.0104166 | +0.01 |

Expected `systran.txt`: 3 transactions (`2022071800000001`..`000003`), card `9680294154603697`.

Expected `acctdata.txt`: **ACCT-CURR-BAL unchanged at 194.00**. Total interest is +5.57, but
account 1 is the last (only) account in TCATBALF and CBACT04C never executes
1050-UPDATE-ACCOUNT for the final control group (see `../README.md`). A Java implementation that
posts the last account would produce 199.57 and fail this fixture — that is a deliberate parity
signal; decide explicitly whether to preserve or fix the COBOL behaviour.

Bytes 279-330 of `systran.txt` are run-time timestamps and must be masked when comparing.
