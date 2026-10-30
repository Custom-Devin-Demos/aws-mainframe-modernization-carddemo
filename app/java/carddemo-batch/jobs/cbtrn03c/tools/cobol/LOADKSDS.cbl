      *****************************************************************
      * Test-harness loader: builds a GnuCOBOL (BDB) indexed file from
      * the ASCII unload of a KSDS, like an IDCAMS REPRO into the KSDS.
      * Usage: LOADKSDS CARDXREF|TRANTYPE|TRANCATG
      *   KSDSIN   = line-sequential input (LF, no CR)
      *   CARDXREF / TRANTYPE / TRANCATG = indexed output file
      *****************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. LOADKSDS.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-FILE ASSIGN TO KSDSIN
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS IN-STATUS.
           SELECT XREF-FILE ASSIGN TO CARDXREF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS RANDOM
                  RECORD KEY IS XREF-KEY
                  FILE STATUS IS OUT-STATUS.
           SELECT TYPE-FILE ASSIGN TO TRANTYPE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS RANDOM
                  RECORD KEY IS TYPE-KEY
                  FILE STATUS IS OUT-STATUS.
           SELECT CATG-FILE ASSIGN TO TRANCATG
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS RANDOM
                  RECORD KEY IS CATG-KEY
                  FILE STATUS IS OUT-STATUS.
       DATA DIVISION.
       FILE SECTION.
       FD  IN-FILE.
       01  IN-REC                PIC X(60).
       FD  XREF-FILE.
       01  XREF-REC.
           05 XREF-KEY           PIC X(16).
           05 FILLER             PIC X(34).
       FD  TYPE-FILE.
       01  TYPE-REC.
           05 TYPE-KEY           PIC X(02).
           05 FILLER             PIC X(58).
       FD  CATG-FILE.
       01  CATG-REC.
           05 CATG-KEY           PIC X(06).
           05 FILLER             PIC X(54).
       WORKING-STORAGE SECTION.
       01  WHICH                 PIC X(08).
       01  IN-STATUS             PIC XX.
       01  OUT-STATUS            PIC XX.
       01  LOADED                PIC 9(7) VALUE 0.
       PROCEDURE DIVISION.
           ACCEPT WHICH FROM COMMAND-LINE
           OPEN INPUT IN-FILE
           EVALUATE WHICH
             WHEN 'CARDXREF' OPEN OUTPUT XREF-FILE
             WHEN 'TRANTYPE' OPEN OUTPUT TYPE-FILE
             WHEN 'TRANCATG' OPEN OUTPUT CATG-FILE
             WHEN OTHER
                DISPLAY 'LOADKSDS: unknown file ' WHICH UPON SYSERR
                MOVE 16 TO RETURN-CODE
                STOP RUN
           END-EVALUATE
           PERFORM UNTIL IN-STATUS NOT = '00'
             MOVE SPACES TO IN-REC
             READ IN-FILE
             IF IN-STATUS = '00'
               EVALUATE WHICH
                 WHEN 'CARDXREF' WRITE XREF-REC FROM IN-REC
                 WHEN 'TRANTYPE' WRITE TYPE-REC FROM IN-REC
                 WHEN 'TRANCATG' WRITE CATG-REC FROM IN-REC
               END-EVALUATE
               IF OUT-STATUS NOT = '00'
                 DISPLAY 'LOADKSDS: write status ' OUT-STATUS
                         ' for ' IN-REC UPON SYSERR
                 MOVE 12 TO RETURN-CODE
                 STOP RUN
               END-IF
               ADD 1 TO LOADED
             END-IF
           END-PERFORM
           CLOSE IN-FILE XREF-FILE TYPE-FILE CATG-FILE
           DISPLAY 'LOADKSDS: ' WHICH ' records ' LOADED UPON SYSERR
           STOP RUN.
