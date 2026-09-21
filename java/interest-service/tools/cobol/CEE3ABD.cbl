      ******************************************************************
      * CEE3ABD - stand-in for the Language Environment abend service
      * called by 9999-ABEND-PROGRAM.  Prints the abend code and stops
      * the run with a non-zero return code so generate.sh fails loudly.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CEE3ABD.
       DATA DIVISION.
       LINKAGE SECTION.
       01  ABCODE                  PIC S9(9) BINARY.
       01  TIMING                  PIC S9(9) BINARY.
       PROCEDURE DIVISION USING ABCODE, TIMING.
           DISPLAY 'CEE3ABD: USER ABEND U' ABCODE
           MOVE 99 TO RETURN-CODE
           STOP RUN.
