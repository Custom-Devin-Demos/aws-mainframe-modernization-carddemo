      ******************************************************************
      * Parity harness only: load line-sequential ASCII extracts into
      * the GnuCOBOL indexed files that CBACT04C opens (stand-in for the
      * IDCAMS REPRO / BLDINDEX steps used on the mainframe).
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. LOADIDX.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT TCAT-IN  ASSIGN TO TCATIN
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT XREF-IN  ASSIGN TO XREFIN
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT ACCT-IN  ASSIGN TO ACCTIN
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT DISC-IN  ASSIGN TO DISCIN
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT TCAT-OUT ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS TO-KEY
                  FILE STATUS  IS WS-STAT.
           SELECT XREF-OUT ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS XO-CARD
                  ALTERNATE RECORD KEY IS XO-ACCT WITH DUPLICATES
                  FILE STATUS  IS WS-STAT.
           SELECT ACCT-OUT ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS AO-KEY
                  FILE STATUS  IS WS-STAT.
           SELECT DISC-OUT ASSIGN TO DISCGRP
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS DO-KEY
                  FILE STATUS  IS WS-STAT.
       DATA DIVISION.
       FILE SECTION.
       FD  TCAT-IN.
       01  TI-REC                 PIC X(50).
       FD  XREF-IN.
       01  XI-REC                 PIC X(50).
       FD  ACCT-IN.
       01  AI-REC                 PIC X(300).
       FD  DISC-IN.
       01  DI-REC                 PIC X(50).
       FD  TCAT-OUT.
       01  TO-REC.
           05 TO-KEY              PIC X(17).
           05 FILLER              PIC X(33).
       FD  XREF-OUT.
       01  XO-REC.
           05 XO-CARD             PIC X(16).
           05 FILLER              PIC X(09).
           05 XO-ACCT             PIC X(11).
           05 FILLER              PIC X(14).
       FD  ACCT-OUT.
       01  AO-REC.
           05 AO-KEY              PIC X(11).
           05 FILLER              PIC X(289).
       FD  DISC-OUT.
       01  DO-REC.
           05 DO-KEY              PIC X(16).
           05 FILLER              PIC X(34).
       WORKING-STORAGE SECTION.
       01  WS-STAT                PIC XX.
       01  WS-EOF                 PIC X.
       01  WS-COUNT               PIC 9(6).
       PROCEDURE DIVISION.
      *    Random-access WRITE lets extracts arrive in any order; the
      *    indexed file keeps them in key order, as a KSDS would.
           OPEN INPUT TCAT-IN OUTPUT TCAT-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ TCAT-IN AT END MOVE 'Y' TO WS-EOF
               NOT AT END
                   WRITE TO-REC FROM TI-REC
                   PERFORM CHECK-STATUS
               END-READ
           END-PERFORM
           CLOSE TCAT-IN TCAT-OUT
           DISPLAY 'TCATBALF LOADED: ' WS-COUNT

           OPEN INPUT XREF-IN OUTPUT XREF-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ XREF-IN AT END MOVE 'Y' TO WS-EOF
               NOT AT END
                   WRITE XO-REC FROM XI-REC
                   PERFORM CHECK-STATUS
               END-READ
           END-PERFORM
           CLOSE XREF-IN XREF-OUT
           DISPLAY 'XREFFILE LOADED: ' WS-COUNT

           OPEN INPUT ACCT-IN OUTPUT ACCT-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ ACCT-IN AT END MOVE 'Y' TO WS-EOF
               NOT AT END
                   WRITE AO-REC FROM AI-REC
                   PERFORM CHECK-STATUS
               END-READ
           END-PERFORM
           CLOSE ACCT-IN ACCT-OUT
           DISPLAY 'ACCTFILE LOADED: ' WS-COUNT

           OPEN INPUT DISC-IN OUTPUT DISC-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ DISC-IN AT END MOVE 'Y' TO WS-EOF
               NOT AT END
                   WRITE DO-REC FROM DI-REC
                   PERFORM CHECK-STATUS
               END-READ
           END-PERFORM
           CLOSE DISC-IN DISC-OUT
           DISPLAY 'DISCGRP  LOADED: ' WS-COUNT
           STOP RUN.

       CHECK-STATUS.
           IF WS-STAT NOT = '00' AND WS-STAT NOT = '02'
               DISPLAY 'LOAD WRITE FAILED, STATUS ' WS-STAT
               MOVE 8 TO RETURN-CODE
               STOP RUN
           END-IF
           ADD 1 TO WS-COUNT.
