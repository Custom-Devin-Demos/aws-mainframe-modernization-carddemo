      *****************************************************************
      * Stub for the LE abend service CEE3ABD (test harness only).
      * Reports the abend code on SYSERR so that stdout stays the
      * program's SYSOUT, then ends the run unit (CEE3ABD never
      * returns to the caller).
      *****************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEE3ABD.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-ABCODE               PIC 9(9).
       LINKAGE SECTION.
       01  LK-ABCODE               PIC S9(9) BINARY.
       01  LK-TIMING               PIC S9(9) BINARY.
       PROCEDURE DIVISION USING LK-ABCODE LK-TIMING.
           MOVE LK-ABCODE TO WS-ABCODE
           DISPLAY 'CEE3ABD ABCODE=' WS-ABCODE UPON SYSERR
           STOP RUN RETURNING 99.
