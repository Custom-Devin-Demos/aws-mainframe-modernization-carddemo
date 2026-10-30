# CBACT01C parity fixtures

Every `expected/` file was produced from the **COBOL program**, never from the Java job:
`app/cbl/CBACT01C.cbl` compiled unchanged with GnuCOBOL 3.1.2 (BDB indexed files,
`-fsign=EBCDIC -fno-pretty-display`), with the shims in `tools/cobol/` (`CEE3ABD` stub, COBOL
stand-in for the assembler `COBDATFT`, `LOADACCT` indexed-file loader). The raw OUTFILE /
ARRYFILE / VBRCFILE / stdout were converted to canonical CSV by `tools/cobol/to_canonical.py`.
Regenerate everything with `tools/cobol/generate_fixtures.sh`.

Each `expected/` directory holds the CSV of every dataset the COBOL run produced
(`account_out.csv`, `account_array.csv`, `account_vbrc.csv`), `sysout.csv` (DISPLAY lines) and
`result.properties` (`return_code` or `abend_code` from the `CEE3ABD` stub, plus record counts of
the produced files, which the tests compare with the job counters). Canonical form: schema column
names, decimals plain with full scale (`-1025.00`), strings right-trimmed, `vbrc` columns that do
not belong to the record type empty. COMP-3 fields are compared by value.

| Scenario | Input | How `expected/` was derived | Rules exercised |
|----------|-------|-----------------------------|-----------------|
| `sample-data` | `app/data/ASCII/acctdata.txt` (50 accounts) | GnuCOBOL run | 01–11, group-id quirk (Q3) |
| `cyc-debit-carryover` | 6 accounts, non-zero debits before/after zero debits | GnuCOBOL run | 07, Q1 (low-values, then 2525.00 carried over) |
| `field-values-and-key-order` | 5 accounts written out of key order; negative/maximum amounts, status `N`/blank/`x`, populated ZIP and group id (comma/quote/leading space), blank dates | GnuCOBOL run | 01, 06, 08–10 |
| `reissue-date-formats` | 7 reissue dates (`YYYY-MM-DD`, `YYYYMMDD`, blank, `/` separators, leading space, `DD-MM-YYYY`, letters) | GnuCOBOL run | 05, Q2 |
| `empty-acctfile` | empty unload | GnuCOBOL run | 02 (status 10 on first READ), 11 |
| `acctfile-missing` | none (DD points to a missing file) | GnuCOBOL run: OPEN status 35 | 12 |
| `outfile-open-error` / `arryfile-open-error` / `vbrcfile-open-error` | 1 account; the output DD points below a regular file | GnuCOBOL run: OPEN OUTPUT status 30 (ENOTDIR). The Java test blocks the dataset directory the same way | 12 |
| `acctfile-read-error` | 3 accounts; record 2 has a non-numeric `ACCT-CURR-BAL` (`00000ABC940{`) | **Partly by hand.** GnuCOBOL reads the record fine (the FD is `PIC X`), so the COBOL cannot produce this branch. Everything up to the failing READ comes from a GnuCOBOL run on record 1 alone; the tail (`ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0030`, `ABENDING PROGRAM`, abend 999) was written from paragraphs `1000-ACCTFILE-GET-NEXT`, `9910-DISPLAY-IO-STATUS` and `9999-ABEND-PROGRAM` | 12, 13 |

The `input/acctdata.txt` files are written by `tools/cobol/make_edge_inputs.py`.

Two conversions are not literal copies of the GnuCOBOL bytes:

* SYSOUT numeric lines (`ACCT-CURR-BAL :` etc.): GnuCOBOL prints `000000019400+`; z/OS prints the
  stored zoned bytes `00000001940{`. The fixtures use the z/OS form.
* `out_acct_curr_cyc_debit` before any zero-debit account is the packed field left at
  low-values (`X'00000000000000'`, no valid sign nibble). Its digits are all zero, so it is
  recorded as `0.00`.

Unit-level expectations not tied to a fixture file (`CobdatftTest`, `Cbact01cJobTest.displayIoStatus`)
were written by hand from `app/asm/COBDATFT.asm` and paragraph `9910-DISPLAY-IO-STATUS`.
