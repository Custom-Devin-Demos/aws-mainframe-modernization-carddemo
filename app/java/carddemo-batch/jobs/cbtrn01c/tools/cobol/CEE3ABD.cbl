      ******************************************************************
      * Test shim for the LE service CEE3ABD (abnormal termination).
      * Writes the abend code to SYSERR, so SYSOUT stays exactly what
      * CBTRN01C DISPLAYs, and ends the run unit with exit status 99
      * (the batch launcher's abend exit code).
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEE3ABD.
       DATA DIVISION.
       LINKAGE SECTION.
       01  LS-ABCODE               PIC S9(9) BINARY.
       01  LS-TIMING               PIC S9(9) BINARY.
       PROCEDURE DIVISION USING LS-ABCODE LS-TIMING.
           DISPLAY 'CEE3ABD ABCODE=' LS-ABCODE ' TIMING=' LS-TIMING
               UPON SYSERR
           MOVE 99 TO RETURN-CODE
           STOP RUN.
