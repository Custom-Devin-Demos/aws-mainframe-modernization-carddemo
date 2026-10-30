      ******************************************************************
      * Builds the ACCTFILE indexed file (BDB) from an ASCII unload in
      * the CVACT01Y layout (300-byte lines, e.g. app/data/ASCII/
      * acctdata.txt). Equivalent of the IDCAMS REPRO that loads the KSDS.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. LOADACCT.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-FILE ASSIGN TO ACCTIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS IN-STATUS.
           SELECT KSDS-FILE ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS RANDOM
                  RECORD KEY IS KS-ACCT-ID
                  FILE STATUS IS KS-STATUS.
       DATA DIVISION.
       FILE SECTION.
       FD  IN-FILE.
       01  IN-REC                     PIC X(300).
       FD  KSDS-FILE.
       01  KS-REC.
           05  KS-ACCT-ID             PIC 9(11).
           05  KS-DATA                PIC X(289).
       WORKING-STORAGE SECTION.
       01  IN-STATUS                  PIC XX.
       01  KS-STATUS                  PIC XX.
       01  WS-EOF                     PIC X VALUE 'N'.
       01  WS-COUNT                   PIC 9(9) VALUE 0.
       PROCEDURE DIVISION.
           OPEN INPUT IN-FILE
           OPEN OUTPUT KSDS-FILE
           PERFORM UNTIL WS-EOF = 'Y'
               READ IN-FILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       IF IN-REC NOT = SPACES
                           MOVE IN-REC TO KS-REC
                           WRITE KS-REC
                               INVALID KEY
                                   DISPLAY 'LOADACCT DUPLICATE KEY '
                                           KS-ACCT-ID UPON SYSERR
                                   MOVE 8 TO RETURN-CODE
                               NOT INVALID KEY
                                   ADD 1 TO WS-COUNT
                           END-WRITE
                       END-IF
               END-READ
           END-PERFORM
           CLOSE IN-FILE KSDS-FILE
           DISPLAY 'LOADACCT RECORDS LOADED ' WS-COUNT UPON SYSERR
           GOBACK.
