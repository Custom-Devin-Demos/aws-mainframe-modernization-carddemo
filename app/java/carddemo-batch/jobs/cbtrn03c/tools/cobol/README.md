# CBTRN03C GnuCOBOL golden-output harness

Runs the **unmodified** `app/cbl/CBTRN03C.cbl` under GnuCOBOL 3.1.2 to produce every expected fixture under
`src/test/resources/fixtures/*/expected/`. The Java job is never used to produce expected output.

```bash
sudo apt-get install -y gnucobol3      # cobc (GnuCOBOL) 3.1.2.0, BDB indexed files
./generate_fixtures.sh                 # build + derive inputs + run every scenario
```

| File | Role |
| --- | --- |
| `build.sh` | `cobc -x -fsign=EBCDIC -I app/cpy` of `CBTRN03C.cbl` + `CEE3ABD.cbl`; builds `LOADKSDS`. Output in `target/` (git-ignored). |
| `CEE3ABD.cbl` | Stub for the LE service: sets RETURN-CODE to the abend code (999) and `STOP RUN`. Its message goes to stderr so SYSOUT is the program's own. `COBDATFT` is not called by CBTRN03C, so no stand-in is needed. |
| `LOADKSDS.cbl` | Loads the ASCII unloads of CARDXREF / TRANTYPE / TRANCATG into GnuCOBOL indexed (BDB) files, like a REPRO into the KSDS. |
| `presort.py` | Independent model of TRANREPT STEP05R: REPRO (TRAN-ID key order), `INCLUDE` on `TRAN-PROC-DT,305,10,CH`, `SORT FIELDS=(TRAN-CARD-NUM,263,16,ZD,A)` with EQUALS. Builds the TRANFILE the COBOL program reads. |
| `derive_tranfile.py` | Builds the sample TRANSACT unload from `app/data/ASCII/dailytran.txt` (`TRAN-PROC-TS` := `TRAN-ORIG-TS`, input order), design section 7.2. |
| `make_edge_inputs.py` | Writes the hand-designed edge-case inputs (`fixtures/<scenario>/input`). |
| `run_scenario.py` | Allocates the DDs (`DD_<name>` env vars), runs CBTRN03C, and converts the output: `transaction_report.txt` (TRANREPT, one 133-byte record per line), `transaction_report.csv` (canonical rows: schema columns, decimals plain with scale 2, strings right-trimmed), `sysout.txt`, `result.properties` (`return_code`, `abended`). |

## Faithfulness notes

* `-fsign=EBCDIC` makes `TRAN-AMT` overpunches (`{`, `A`..`I`, `}`, `J`..`R`) decode as on z/OS.
* GnuCOBOL DISPLAYs a signed numeric item as `+000000050.47` (pretty-display). z/OS prints the raw zoned
  image (`00000005047G`). `run_scenario.py` rewrites exactly the two numeric DISPLAYs (`TRAN-AMT `,
  `WS-PAGE-TOTAL`) to the z/OS image; every other SYSOUT line is byte-for-byte GnuCOBOL output.
* Missing DDs are not allocated, so `OPEN` fails with status 35 (file status 35), the status the program reports for an OPEN failure.
* When the program abends, the partial TRANREPT is discarded: the Java job publishes nothing on an abend
  (design section 4), so abend scenarios only have `sysout.txt` and `result.properties`.
* Collation is ASCII (dates and digit-only keys collate the same in EBCDIC).
