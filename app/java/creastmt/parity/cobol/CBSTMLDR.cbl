       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CBSTMLDR.
      ******************************************************************
      * Parity-harness helper (not part of CREASTMT): stands in for the
      * IDCAMS REPRO/VSAM loads by building the four indexed files that
      * CBSTM03B reads from line-sequential copies of the sample data.
      ******************************************************************
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT XREF-IN  ASSIGN TO XREFIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-IN-STAT.
           SELECT XREF-OUT ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS XREF-KEY
                  FILE STATUS  IS WS-OUT-STAT.
           SELECT CUST-IN  ASSIGN TO CUSTIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-IN-STAT.
           SELECT CUST-OUT ASSIGN TO CUSTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS CUST-KEY
                  FILE STATUS  IS WS-OUT-STAT.
           SELECT ACCT-IN  ASSIGN TO ACCTIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-IN-STAT.
           SELECT ACCT-OUT ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS ACCT-KEY
                  FILE STATUS  IS WS-OUT-STAT.
           SELECT TRNX-IN  ASSIGN TO TRNXIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-IN-STAT.
           SELECT TRNX-OUT ASSIGN TO TRNXFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS TRNX-KEY
                  FILE STATUS  IS WS-OUT-STAT.
       DATA DIVISION.
       FILE SECTION.
       FD  XREF-IN.
       01  XREF-IN-REC              PIC X(50).
       FD  XREF-OUT.
       01  XREF-OUT-REC.
           05  XREF-KEY             PIC X(16).
           05  FILLER               PIC X(34).
       FD  CUST-IN.
       01  CUST-IN-REC              PIC X(500).
       FD  CUST-OUT.
       01  CUST-OUT-REC.
           05  CUST-KEY             PIC X(09).
           05  FILLER               PIC X(491).
       FD  ACCT-IN.
       01  ACCT-IN-REC              PIC X(300).
       FD  ACCT-OUT.
       01  ACCT-OUT-REC.
           05  ACCT-KEY             PIC X(11).
           05  FILLER               PIC X(289).
       FD  TRNX-IN.
       01  TRNX-IN-REC              PIC X(350).
       FD  TRNX-OUT.
       01  TRNX-OUT-REC.
           05  TRNX-KEY             PIC X(32).
           05  FILLER               PIC X(318).
       WORKING-STORAGE SECTION.
       01  WS-IN-STAT               PIC X(02).
       01  WS-OUT-STAT              PIC X(02).
       01  WS-COUNT                 PIC 9(07) VALUE 0.
       PROCEDURE DIVISION.
           OPEN INPUT XREF-IN OUTPUT XREF-OUT
           PERFORM CHECK-OPEN
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-IN-STAT NOT = '00'
               MOVE SPACES TO XREF-IN-REC
               READ XREF-IN
               IF WS-IN-STAT = '00'
                   WRITE XREF-OUT-REC FROM XREF-IN-REC
                   PERFORM CHECK-WRITE
               END-IF
           END-PERFORM
           CLOSE XREF-IN XREF-OUT
           DISPLAY 'LOADED XREFFILE ' WS-COUNT

           OPEN INPUT CUST-IN OUTPUT CUST-OUT
           PERFORM CHECK-OPEN
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-IN-STAT NOT = '00'
               MOVE SPACES TO CUST-IN-REC
               READ CUST-IN
               IF WS-IN-STAT = '00'
                   WRITE CUST-OUT-REC FROM CUST-IN-REC
                   PERFORM CHECK-WRITE
               END-IF
           END-PERFORM
           CLOSE CUST-IN CUST-OUT
           DISPLAY 'LOADED CUSTFILE ' WS-COUNT

           OPEN INPUT ACCT-IN OUTPUT ACCT-OUT
           PERFORM CHECK-OPEN
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-IN-STAT NOT = '00'
               MOVE SPACES TO ACCT-IN-REC
               READ ACCT-IN
               IF WS-IN-STAT = '00'
                   WRITE ACCT-OUT-REC FROM ACCT-IN-REC
                   PERFORM CHECK-WRITE
               END-IF
           END-PERFORM
           CLOSE ACCT-IN ACCT-OUT
           DISPLAY 'LOADED ACCTFILE ' WS-COUNT

           OPEN INPUT TRNX-IN OUTPUT TRNX-OUT
           PERFORM CHECK-OPEN
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-IN-STAT NOT = '00'
               MOVE SPACES TO TRNX-IN-REC
               READ TRNX-IN
               IF WS-IN-STAT = '00'
                   WRITE TRNX-OUT-REC FROM TRNX-IN-REC
                   PERFORM CHECK-WRITE
               END-IF
           END-PERFORM
           CLOSE TRNX-IN TRNX-OUT
           DISPLAY 'LOADED TRNXFILE ' WS-COUNT

           STOP RUN.

       CHECK-OPEN.
           IF WS-IN-STAT NOT = '00' OR WS-OUT-STAT NOT = '00'
               DISPLAY 'OPEN FAILED ' WS-IN-STAT ' ' WS-OUT-STAT
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF.

       CHECK-WRITE.
           IF WS-OUT-STAT NOT = '00'
               DISPLAY 'WRITE FAILED ' WS-OUT-STAT
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF
           ADD 1 TO WS-COUNT.
