      *****************************************************************
      * Test shim for the LE service CEE3ABD (abnormal termination).
      * Writes the abend code to SYSERR (never to SYSOUT, so the
      * captured DISPLAY lines are exactly the program's) and ends the
      * run with return code 99, the Java launcher's abend exit code.
      *****************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEE3ABD.
       DATA DIVISION.
       LINKAGE SECTION.
       01  ABCODE                  PIC S9(9) BINARY.
       01  TIMING                  PIC S9(9) BINARY.
       PROCEDURE DIVISION USING ABCODE TIMING.
           DISPLAY 'CEE3ABD ABCODE=' ABCODE ' TIMING=' TIMING
               UPON SYSERR
           STOP RUN RETURNING 99.
