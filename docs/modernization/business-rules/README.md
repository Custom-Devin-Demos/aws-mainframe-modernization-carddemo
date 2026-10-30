# Business rules per migrated program

One file per COBOL program, named `<PROGRAM>.md` (for example `CBTRN02C.md`). Each file is written by the
session that migrates the program and is reviewed with that program's PR.

Required sections:

1. **Purpose and JCL context**: the job and step that runs the program, its DD names, and its upstream/downstream jobs.
2. **Inputs and outputs**: every file/table with its copybook/DDL, its organization (KSDS/ESDS/PS/Db2) and its access mode, mapped to the Java input DD name and the output dataset name(s).
3. **Processing rules**: numbered rules (`BR-<PROGRAM>-nn`), each citing the COBOL paragraph (for example `1500-B-LOOKUP-ACCT`) and stating conditions, computations, field moves, counters and return codes.
4. **Error handling**: file-status checks, INVALID KEY branches, abend points (`CEE3ABD`), and return codes.
5. **Quirks preserved for parity**: behavior that looks like a bug but is reproduced (with the COBOL evidence), and anything deliberately *not* reproduced, with the reason.
6. **Traceability**: a table mapping each rule to the Java class/method and the parity test(s) that cover it.
