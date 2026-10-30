      ******************************************************************
      * TTLOAD: builds the TRANSACTION_TYPE and TRANSACTION_TYPE_CATEGORY
      * indexed files from the 60-byte TRANEXTR-format unloads
      * (CVTRA03Y / CVTRA04Y). Duplicate keys stop the load (RC 16).
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. TTLOAD.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT TT-IN ASSIGN TO TTIN
                  ORGANIZATION IS SEQUENTIAL
                  FILE STATUS IS IN-STAT.
           SELECT TC-IN ASSIGN TO TCIN
                  ORGANIZATION IS SEQUENTIAL
                  FILE STATUS IS IN-STAT.
           SELECT TT-FILE ASSIGN TO TTKSDS
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS RANDOM
                  RECORD KEY IS TT-TYPE
                  FILE STATUS IS OUT-STAT.
           SELECT TC-FILE ASSIGN TO TCKSDS
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS RANDOM
                  RECORD KEY IS TC-KEY
                  FILE STATUS IS OUT-STAT.
       DATA DIVISION.
       FILE SECTION.
       FD TT-IN RECORDING MODE F.
       01 TT-IN-REC.
          05 TTI-TYPE                  PIC X(2).
          05 TTI-DESC                  PIC X(50).
          05 FILLER                    PIC X(8).
       FD TC-IN RECORDING MODE F.
       01 TC-IN-REC.
          05 TCI-TYPE                  PIC X(2).
          05 TCI-CAT                   PIC X(4).
          05 TCI-DATA                  PIC X(50).
          05 FILLER                    PIC X(4).
       FD TT-FILE.
       01 TT-REC.
          05 TT-TYPE                   PIC X(2).
          05 TT-DESC                   PIC X(50).
       FD TC-FILE.
       01 TC-REC.
          05 TC-KEY.
             10 TC-TYPE                PIC X(2).
             10 TC-CAT                 PIC X(4).
          05 TC-DATA                   PIC X(50).
       WORKING-STORAGE SECTION.
       01 IN-STAT                      PIC XX.
       01 OUT-STAT                     PIC XX.
       01 WS-EOF                       PIC X.
       PROCEDURE DIVISION.
           OPEN INPUT TT-IN OUTPUT TT-FILE
           MOVE 'N' TO WS-EOF
           PERFORM UNTIL WS-EOF = 'Y'
              READ TT-IN
                 AT END MOVE 'Y' TO WS-EOF
                 NOT AT END
                    MOVE TTI-TYPE TO TT-TYPE
                    MOVE TTI-DESC TO TT-DESC
                    WRITE TT-REC
                    IF OUT-STAT NOT = '00'
                       DISPLAY 'TTLOAD TRANTYPE ' TTI-TYPE ' ' OUT-STAT
                       MOVE 16 TO RETURN-CODE
                       STOP RUN
                    END-IF
              END-READ
           END-PERFORM
           CLOSE TT-IN TT-FILE
           OPEN INPUT TC-IN OUTPUT TC-FILE
           MOVE 'N' TO WS-EOF
           PERFORM UNTIL WS-EOF = 'Y'
              READ TC-IN
                 AT END MOVE 'Y' TO WS-EOF
                 NOT AT END
                    MOVE TCI-TYPE TO TC-TYPE
                    MOVE TCI-CAT TO TC-CAT
                    MOVE TCI-DATA TO TC-DATA
                    WRITE TC-REC
                    IF OUT-STAT NOT = '00'
                       DISPLAY 'TTLOAD TRANCATG ' TC-KEY ' ' OUT-STAT
                       MOVE 16 TO RETURN-CODE
                       STOP RUN
                    END-IF
              END-READ
           END-PERFORM
           CLOSE TC-IN TC-FILE
           STOP RUN.
