      *****************************************************************
      * Test shim: builds the CARDFILE KSDS (BDB indexed file) from an
      * ASCII unload, the GnuCOBOL stand-in for IDCAMS REPRO in
      * app/jcl/CARDFILE.jcl. Unload conventions match the Java
      * FixedWidthFile reader: a trailing CR (CRLF files) is dropped,
      * short lines are space-padded, blank lines are skipped.
      * Records are written with random access, so input order does
      * not matter; a duplicate key (status 22) fails the load.
      *****************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. LOADCARD.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT CARDIN-FILE ASSIGN TO CARDIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS IN-STATUS.
           SELECT CARDOUT ASSIGN TO CARDFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS OUT-CARD-NUM
                  FILE STATUS  IS OUT-STATUS.
       DATA DIVISION.
       FILE SECTION.
       FD  CARDIN-FILE.
       01  IN-REC                  PIC X(150).
       FD  CARDOUT.
       01  OUT-REC.
           05 OUT-CARD-NUM         PIC X(16).
           05 OUT-CARD-DATA        PIC X(134).
       WORKING-STORAGE SECTION.
       01  IN-STATUS               PIC XX.
       01  OUT-STATUS              PIC XX.
       01  LOADED                  PIC 9(7) VALUE 0.
       PROCEDURE DIVISION.
           OPEN INPUT CARDIN-FILE
           IF IN-STATUS NOT = '00'
               DISPLAY 'LOADCARD: OPEN CARDIN STATUS ' IN-STATUS
                   UPON SYSERR
               STOP RUN RETURNING 8
           END-IF
           OPEN OUTPUT CARDOUT
           IF OUT-STATUS NOT = '00'
               DISPLAY 'LOADCARD: OPEN CARDFILE STATUS ' OUT-STATUS
                   UPON SYSERR
               STOP RUN RETURNING 8
           END-IF
           PERFORM UNTIL IN-STATUS NOT = '00'
               READ CARDIN-FILE
               IF IN-STATUS = '00'
                   INSPECT IN-REC REPLACING ALL X'0D' BY SPACE
                   IF IN-REC NOT = SPACES
                       MOVE IN-REC TO OUT-REC
                       WRITE OUT-REC
                       IF OUT-STATUS NOT = '00'
                           DISPLAY 'LOADCARD: WRITE STATUS ' OUT-STATUS
                               ' KEY ' OUT-CARD-NUM UPON SYSERR
                           STOP RUN RETURNING 8
                       END-IF
                       ADD 1 TO LOADED
                   END-IF
               END-IF
           END-PERFORM
           CLOSE CARDIN-FILE CARDOUT
           DISPLAY 'LOADCARD: ' LOADED ' RECORDS LOADED' UPON SYSERR
           STOP RUN.
