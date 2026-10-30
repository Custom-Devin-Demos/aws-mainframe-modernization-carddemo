      *> Parity-harness utility: builds the KSDS stand-ins (GnuCOBOL BDB
      *> indexed files) from fixed-length sequential images, and unloads
      *> them back in key order. Usage: idxutil LOAD|UNLOAD XREF|ACCT|TCAT|TRAN
      *> Files: SEQFILE (fixed-length image), and the indexed file by name.
       IDENTIFICATION DIVISION.
       PROGRAM-ID. IDXUTIL.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT SEQ50  ASSIGN TO SEQFILE ORGANIZATION SEQUENTIAL
                  FILE STATUS IS WS-SEQ-STATUS.
           SELECT SEQ300 ASSIGN TO SEQFILE ORGANIZATION SEQUENTIAL
                  FILE STATUS IS WS-SEQ-STATUS.
           SELECT SEQ350 ASSIGN TO SEQFILE ORGANIZATION SEQUENTIAL
                  FILE STATUS IS WS-SEQ-STATUS.
           SELECT XREF-FILE ASSIGN TO XREFFILE ORGANIZATION INDEXED
                  ACCESS SEQUENTIAL RECORD KEY IS XREF-KEY
                  FILE STATUS IS WS-IDX-STATUS.
           SELECT ACCT-FILE ASSIGN TO ACCTFILE ORGANIZATION INDEXED
                  ACCESS SEQUENTIAL RECORD KEY IS ACCT-KEY
                  FILE STATUS IS WS-IDX-STATUS.
           SELECT TCAT-FILE ASSIGN TO TCATBALF ORGANIZATION INDEXED
                  ACCESS SEQUENTIAL RECORD KEY IS TCAT-KEY
                  FILE STATUS IS WS-IDX-STATUS.
           SELECT TRAN-FILE ASSIGN TO TRANFILE ORGANIZATION INDEXED
                  ACCESS SEQUENTIAL RECORD KEY IS TRAN-KEY
                  FILE STATUS IS WS-IDX-STATUS.
       DATA DIVISION.
       FILE SECTION.
       FD  SEQ50.
       01  SEQ50-REC               PIC X(50).
       FD  SEQ300.
       01  SEQ300-REC              PIC X(300).
       FD  SEQ350.
       01  SEQ350-REC              PIC X(350).
       FD  XREF-FILE.
       01  XREF-REC.
           05 XREF-KEY             PIC X(16).
           05 FILLER               PIC X(34).
       FD  ACCT-FILE.
       01  ACCT-REC.
           05 ACCT-KEY             PIC X(11).
           05 FILLER               PIC X(289).
       FD  TCAT-FILE.
       01  TCAT-REC.
           05 TCAT-KEY             PIC X(17).
           05 FILLER               PIC X(33).
       FD  TRAN-FILE.
       01  TRAN-REC.
           05 TRAN-KEY             PIC X(16).
           05 FILLER               PIC X(334).
       WORKING-STORAGE SECTION.
       01  WS-ARGS                 PIC X(40).
       01  WS-MODE                 PIC X(10).
       01  WS-FILE                 PIC X(10).
       01  WS-SEQ-STATUS           PIC XX.
       01  WS-IDX-STATUS           PIC XX.
       01  WS-EOF                  PIC X VALUE 'N'.
       01  WS-COUNT                PIC 9(9) VALUE 0.
       PROCEDURE DIVISION.
           ACCEPT WS-ARGS FROM COMMAND-LINE
           UNSTRING WS-ARGS DELIMITED BY ALL SPACE
               INTO WS-MODE WS-FILE
           EVALUATE WS-MODE ALSO WS-FILE
             WHEN 'LOAD' ALSO 'XREF'
               OPEN INPUT SEQ50 OUTPUT XREF-FILE
               PERFORM UNTIL WS-EOF = 'Y'
                 READ SEQ50 AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                     WRITE XREF-REC FROM SEQ50-REC
                     PERFORM CHECK-IDX
                 END-READ
               END-PERFORM
               CLOSE SEQ50 XREF-FILE
             WHEN 'LOAD' ALSO 'ACCT'
               OPEN INPUT SEQ300 OUTPUT ACCT-FILE
               PERFORM UNTIL WS-EOF = 'Y'
                 READ SEQ300 AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                     WRITE ACCT-REC FROM SEQ300-REC
                     PERFORM CHECK-IDX
                 END-READ
               END-PERFORM
               CLOSE SEQ300 ACCT-FILE
             WHEN 'LOAD' ALSO 'TCAT'
               OPEN INPUT SEQ50 OUTPUT TCAT-FILE
               PERFORM UNTIL WS-EOF = 'Y'
                 READ SEQ50 AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                     WRITE TCAT-REC FROM SEQ50-REC
                     PERFORM CHECK-IDX
                 END-READ
               END-PERFORM
               CLOSE SEQ50 TCAT-FILE
             WHEN 'UNLOAD' ALSO 'ACCT'
               OPEN INPUT ACCT-FILE OUTPUT SEQ300
               PERFORM UNTIL WS-EOF = 'Y'
                 READ ACCT-FILE NEXT AT END MOVE 'Y' TO WS-EOF
                   NOT AT END WRITE SEQ300-REC FROM ACCT-REC
                 END-READ
               END-PERFORM
               CLOSE ACCT-FILE SEQ300
             WHEN 'UNLOAD' ALSO 'TCAT'
               OPEN INPUT TCAT-FILE OUTPUT SEQ50
               PERFORM UNTIL WS-EOF = 'Y'
                 READ TCAT-FILE NEXT AT END MOVE 'Y' TO WS-EOF
                   NOT AT END WRITE SEQ50-REC FROM TCAT-REC
                 END-READ
               END-PERFORM
               CLOSE TCAT-FILE SEQ50
             WHEN 'UNLOAD' ALSO 'TRAN'
               OPEN INPUT TRAN-FILE OUTPUT SEQ350
               PERFORM UNTIL WS-EOF = 'Y'
                 READ TRAN-FILE NEXT AT END MOVE 'Y' TO WS-EOF
                   NOT AT END WRITE SEQ350-REC FROM TRAN-REC
                 END-READ
               END-PERFORM
               CLOSE TRAN-FILE SEQ350
             WHEN OTHER
               DISPLAY 'IDXUTIL: bad arguments ' WS-ARGS
               STOP RUN RETURNING 16
           END-EVALUATE
           STOP RUN.
       CHECK-IDX.
           IF WS-IDX-STATUS NOT = '00'
               DISPLAY 'IDXUTIL: write status ' WS-IDX-STATUS
               STOP RUN RETURNING 16
           END-IF.
