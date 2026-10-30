      *****************************************************************
      * Test-harness loader (stands in for IDCAMS DEFINE + REPRO):
      * builds the XREFFILE indexed file from the ASCII unload
      * (line sequential, CVACT03Y 50-byte records) named by DD XREFIN.
      * Mirrors FixedWidthFile/KeyedFile.load: a trailing CR (CRLF
      * unloads) is dropped, short lines are space-padded, all-blank
      * lines are skipped, and a duplicate key stops the load (RC 8).
      *****************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. XREFLOAD.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT XREFIN-FILE ASSIGN TO XREFIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS XREFIN-STATUS.
           SELECT XREFFILE-FILE ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-XREF-CARD-NUM
                  FILE STATUS  IS XREFFILE-STATUS.
       DATA DIVISION.
       FILE SECTION.
       FD  XREFIN-FILE.
       01  FD-XREFIN-REC                       PIC X(50).
       FD  XREFFILE-FILE.
       01  FD-XREFFILE-REC.
           05 FD-XREF-CARD-NUM                  PIC X(16).
           05 FD-XREF-DATA                      PIC X(34).
       WORKING-STORAGE SECTION.
       01  XREFIN-STATUS           PIC XX.
       01  XREFFILE-STATUS         PIC XX.
       01  WS-REC                  PIC X(50).
       01  WS-LOADED               PIC 9(9) VALUE 0.
       PROCEDURE DIVISION.
           OPEN INPUT XREFIN-FILE
           IF XREFIN-STATUS NOT = '00'
               DISPLAY 'XREFLOAD: OPEN XREFIN ' XREFIN-STATUS
               STOP RUN RETURNING 8
           END-IF
           OPEN OUTPUT XREFFILE-FILE
           IF XREFFILE-STATUS NOT = '00'
               DISPLAY 'XREFLOAD: OPEN XREFFILE ' XREFFILE-STATUS
               STOP RUN RETURNING 8
           END-IF
           PERFORM UNTIL XREFIN-STATUS NOT = '00'
               MOVE SPACES TO FD-XREFIN-REC
               READ XREFIN-FILE
               IF XREFIN-STATUS = '00' OR XREFIN-STATUS = '04'
                   MOVE '00' TO XREFIN-STATUS
                   MOVE FD-XREFIN-REC TO WS-REC
                   INSPECT WS-REC REPLACING ALL X'0D' BY SPACE
                   IF WS-REC NOT = SPACES
                       MOVE WS-REC TO FD-XREFFILE-REC
                       WRITE FD-XREFFILE-REC
                       IF XREFFILE-STATUS NOT = '00'
                           DISPLAY 'XREFLOAD: WRITE ' XREFFILE-STATUS
                                   ' KEY ' FD-XREF-CARD-NUM
                           STOP RUN RETURNING 8
                       END-IF
                       ADD 1 TO WS-LOADED
                   END-IF
               END-IF
           END-PERFORM
           CLOSE XREFIN-FILE XREFFILE-FILE
           DISPLAY 'XREFLOAD: LOADED ' WS-LOADED
           STOP RUN.
