# CREASTMT in Java 17

Java port of the `CREASTMT` statement job (`app/jcl/CREASTMT.JCL`, `app/cbl/CBSTM03A.CBL`,
`app/cbl/CBSTM03B.CBL`). It reads the card cross-reference, customer, account and transaction
files and writes the 80-column text statement (`STATEMNT.PS`) and the HTML statement
(`STATEMNT.HTML`) in the same format as the COBOL job. Each fixed-length record is space-padded
to its LRECL (80 / 100) and ends with a newline.

```
mvn package
java -jar target/creastmt-1.0.0-SNAPSHOT.jar \
  --xref=../../data/ASCII/cardxref.txt --cust=../../data/ASCII/custdata.txt \
  --acct=../../data/ASCII/acctdata.txt --trnx=../../data/ASCII/dailytran.txt \
  --stmt=STATEMNT.PS --html=STATEMNT.HTML
```

## Parity with the COBOL job

`mvn test` needs GnuCOBOL 3.x (`cobc`, e.g. `apt-get install gnucobol3`). `CobolParityTest`
runs `parity/run-cobol.sh`, which compiles and runs the original CBSTM03A/CBSTM03B against
`app/data/ASCII`, then runs the Java job on the same files. The test passes only if both outputs
are byte-identical. If `cobc` is missing, the test fails; it is never skipped.
`parity/run-parity.sh` does the same run and prints the `diff` results.

What `run-cobol.sh` changes to run the COBOL off-mainframe (the source files themselves are not modified):

1. Drops the PSA/TCB/TIOT control-block walk at the top of CBSTM03A's PROCEDURE DIVISION
   from the compiled copy. It only DISPLAYs the job and DD names, and off z/OS it dereferences a
   null pointer.
2. Compiles with `-fsign=EBCDIC` so that trailing overpunch signs in the sample data (`{`, `A`-`R`, `}`) are
   decoded the way z/OS decodes them. Without the flag, GnuCOBOL misreads the last digit of every amount.
3. Compiles with `-ftab-width=3` because `CUSTREC.cpy` contains TAB characters. With the default
   width of 8, its PIC clauses run past column 72.
4. Adds a `CEE3ABD` stub for the abend path and a loader (`CBSTMLDR`) that builds the indexed
   files. The SORT/OUTREC step is done with `awk` + `sort`, in place of DFSORT/IDCAMS.
