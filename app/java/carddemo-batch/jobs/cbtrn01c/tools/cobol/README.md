# GnuCOBOL parity harness for CBTRN01C

Runs the unmodified `app/cbl/CBTRN01C.cbl` under GnuCOBOL and turns its SYSOUT into the canonical expected
fixtures in `src/test/resources/fixtures/` (see the README there). Nothing here is used by the Maven build.

```bash
sudo apt-get install -y gnucobol3     # 3.1.2, BDB indexed files
jobs/cbtrn01c/tools/cobol/gen_fixtures.sh
git status jobs/cbtrn01c/src/test/resources/fixtures   # should show no changes
```

| File | Role |
|---|---|
| `build.sh` | `cobc -x -fsign=EBCDIC -fstatic-call -I app/cpy` the original program plus the `CEE3ABD` stub; builds one loader per indexed file from `LOADKSDS.cbl.tmpl`. Output in `build/` (git-ignored). |
| `CEE3ABD.cbl` | Stub for LE `CEE3ABD`: writes `CEE3ABD ABCODE=<code> TIMING=<t>` to stderr and stops with exit status 99. |
| `LOADKSDS.cbl.tmpl` | Loader: reads a newline-delimited ASCII unload and `WRITE`s each record to a GnuCOBOL indexed file (prime key at offset 0: CUSTFILE 9, XREFFILE 16, CARDFILE 16, ACCTFILE 11, TRANFILE 16). |
| `to_fixed.py` | Converts the newline-delimited DALYTRAN unload to fixed 350-byte records for `ORGANIZATION SEQUENTIAL`. |
| `run_scenario.sh` | Loads the inputs, sets `DD_<name>` environment variables (GnuCOBOL's ASSIGN mapping), runs the program; writes `sysout.txt`, `stderr.txt`, `exit_code`. A DD left out is pointed at a nonexistent file, giving OPEN status 35. |
| `make_edge_inputs.py` | Builds the edge-scenario `input/` files from `app/data/ASCII`, byte-for-byte copies with the documented edits. |
| `sysout_to_expected.py` | Parses one run into `sysout.txt`, `sysout.csv`, `daily_tran_verification.csv`, `result.properties`. |
| `gen_fixtures.sh` | Runs all of the above for every scenario. |

Shims and deviations: only `CEE3ABD` needed a stub (CBTRN01C does not call `COBDATFT`). `-fsign=EBCDIC` keeps
the overpunched signs of the sample amounts as on the mainframe; CBTRN01C only moves them as part of the
record image. GnuCOBOL initialises WORKING-STORAGE without VALUE to spaces/zeros, which decides the
empty-input behaviour (see `docs/modernization/business-rules/CBTRN01C.md` §5).
