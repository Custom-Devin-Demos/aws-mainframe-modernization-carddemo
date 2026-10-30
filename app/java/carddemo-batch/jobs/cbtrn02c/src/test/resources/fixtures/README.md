# CBTRN02C parity fixtures

Every file under `*/expected/` was produced by the original COBOL program `app/cbl/CBTRN02C.cbl` compiled and run
under GnuCOBOL 3.1.2 (BDB indexed files, `-fsign=EBCDIC`) by `tools/cobol/run_cobol.sh`, then converted to CSV by
`tools/cobol/cobol_files.py`. None was produced or edited from Java output. See `tools/cobol/README.md`.

Per scenario: `posted_transactions.csv` (TRANFILE unloaded in key order), `daily_rejects.csv` (DALYREJS in write
order), `account_master.csv` / `tran_cat_balance.csv` (ACCTFILE / TCATBALF after the run, key order),
`sysout.txt` (the program's DISPLAY output), `return_code.txt` (RETURN-CODE; 99 = CEE3ABD stub).

| Scenario | Input | What it covers |
| --- | --- | --- |
| `sample-data` | `app/data/ASCII` (read via `SampleData.file`, not copied) | 300 transactions, 262 posted, 38 rejects (all 0102), 50 TCATBAL creates, RC 4 |
| `reject-reasons` | hand-built, `make_edge_inputs.py` | 0100, 0101, 0102, 0103, 0102+0103 → 0103, limit == temp bal and expiry == orig date both accepted, TCATBAL update, RC 4 |
| `tcatbal-create-update` | hand-built | update existing, create missing (status 23), update the record created earlier in the run, RC 0 |
| `negative-amounts` | hand-built | negative → CYC-DEBIT, zero → CYC-CREDIT, negative TCATBAL balances, CREDIT − DEBIT growing with debits (0102 after debits) |
| `picture-truncation` | hand-built | WS-TEMP-BAL S9(09)V99 high-order truncation (overlimit missed), ACCT S9(10)V99 and TRAN-CAT-BAL S9(09)V99 wrap on ADD |
| `duplicate-tran-id-abend` | hand-built | WRITE TRANFILE status 22 → `9999-ABEND-PROGRAM`; only `sysout.txt` and `return_code.txt` (no datasets) |

`Cbtrn02cParityTest.duplicateTranIdAbends` drops the last COBOL SYSOUT line (`CEE3ABD ABCODE=...`), which comes
from the stub, not from CBTRN02C. `missingAccountFileAbendsAtOpen` has no fixture directory: its expected SYSOUT
(`ERROR OPENING ACCOUNT MASTER FILE` / `FILE STATUS IS: NNNN0035` / `ABENDING PROGRAM`) is derived by hand from
`0400-ACCTFILE-OPEN` and `9910-DISPLAY-IO-STATUS`.

Regenerate: `python3 tools/cobol/make_edge_inputs.py` then `run_cobol.sh` per scenario (see tools README).
