# GnuCOBOL harness for the CBACT01C parity fixtures

Runs the **original** `app/cbl/CBACT01C.cbl` under GnuCOBOL 3.1.2 (BDB indexed files) and turns
its output into the canonical CSV fixtures under `src/test/resources/fixtures/*/expected/`.
The Java job is never involved.

```
sudo apt-get install -y gnucobol3 python3    # cobc (GnuCOBOL) 3.1.2.0
./generate_fixtures.sh                       # rebuilds every expected/ directory (and edge inputs)
```

| File | Role |
|------|------|
| `run_cbact01c.sh` | Compiles CBACT01C + shims with `-fsign=EBCDIC -fno-pretty-display -fstatic-call`, loads ACCTFILE, runs the program with DD names as environment variables. `CB_OUTFILE`/`CB_ARRYFILE`/`CB_VBRCFILE` override an output path (OPEN failure scenarios). |
| `LOADACCT.cbl` | Loader: ASCII unload (300-byte lines) → indexed `ACCTFILE` keyed on the first 11 bytes (the IDCAMS REPRO step). |
| `COBDATFT.cbl` | COBOL stand-in for `app/asm/COBDATFT.asm`: same branches (VALIDIN1/VALIDIN2/GOTOERR), stores exactly the bytes the assembler stores. |
| `CEE3ABD.cbl` | Stub for the LE abend service: prints `CEE3ABD-STUB ABCODE=…` on stderr and stops with exit status 99. |
| `make_edge_inputs.py` | Writes the hand-designed edge-case inputs (`fixtures/<scenario>/input/acctdata.txt`). |
| `to_canonical.py` | Decodes the raw outputs (zoned with EBCDIC overpunch, COMP-3, GnuCOBOL variable-record headers) into canonical CSV (schema columns, plain decimals with full scale, strings right-trimmed) plus `result.properties` (return/abend code and record counts). |
| `generate_fixtures.sh` | Runs every scenario. |

Notes:

* `-fsign=EBCDIC` makes zoned signs match the sample data (`{`, `A`–`I`, `}`, `J`–`R`).
* GnuCOBOL DISPLAYs a signed zoned item as digits plus a separate sign (`000000019400+`) while
  z/OS writes the stored bytes (`00000001940{`); `to_canonical.py` rewrites those SYSOUT lines to the
  z/OS form.
* `acctfile-read-error` cannot be produced by COBOL: the FD is `PIC X`, so GnuCOBOL reads a
  non-numeric balance with status 00. Its COBOL part (record 1) comes from a run on the prefix; the
  error tail is written by hand from paragraphs 1000/9910/9999 (see the script).
* `target/` (binaries, work files) is git-ignored.
