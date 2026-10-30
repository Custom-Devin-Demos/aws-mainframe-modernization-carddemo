      ******************************************************************
      * Parity harness only: unload the ACCTFILE KSDS after CBACT04C
      * has rewritten it, as fixed 300-byte records (no line feeds).
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. UNLDACCT.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ACCT-IDX ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS AX-KEY
                  FILE STATUS  IS WS-STAT.
           SELECT ACCT-SEQ ASSIGN TO ACCTOUT
                  ORGANIZATION IS SEQUENTIAL.
       DATA DIVISION.
       FILE SECTION.
       FD  ACCT-IDX.
       01  AX-REC.
           05 AX-KEY              PIC X(11).
           05 FILLER              PIC X(289).
       FD  ACCT-SEQ.
       01  AS-REC                 PIC X(300).
       WORKING-STORAGE SECTION.
       01  WS-STAT                PIC XX.
       01  WS-EOF                 PIC X VALUE 'N'.
       PROCEDURE DIVISION.
           OPEN INPUT ACCT-IDX OUTPUT ACCT-SEQ
           PERFORM UNTIL WS-EOF = 'Y'
               READ ACCT-IDX NEXT AT END MOVE 'Y' TO WS-EOF
               NOT AT END WRITE AS-REC FROM AX-REC
               END-READ
           END-PERFORM
           CLOSE ACCT-IDX ACCT-SEQ
           STOP RUN.
