# Importing QSOs

Choose **File → Import…**, then use **Browse…** to select a file. The file chooser
accepts `.json`, `.adi` and `.adif` files. Its extension selects the importer.
Import runs in the background, with progress and a completion report in the dialog.

## QSO journals

A `.json` file must contain one complete FDSwarm QSO JSON object per line, as in
`qsosJournal.json`. A JSON array or exported contest configuration is not a QSO
journal. Blank lines are ignored.

Journal import uses the same store merge as node synchronization:

- Records retain their UUIDs, timestamps and metadata.
- A record is rejected as a duplicate only if its UUID is already in the store,
  including a UUID accepted earlier in the same file. The first record wins.
- Different UUIDs are accepted even when callsign, band and mode are identical.
- No contest-start date filter is applied, matching synchronization behavior.
- Invalid lines are reported with their physical line numbers; other valid lines
  continue to be imported.

The report shows total nonblank records, parsed QSO records, records added,
duplicates rejected and line parse errors. Added plus duplicates equals parsed;
parsed plus parse errors equals total. Full rejected lines are shown and can be
saved as JSON lines for inspection or repair. Duplicate lines are counted, rather
than included in this failed-line file.

Imported journal records become available to other nodes through normal status
broadcasts and QSO synchronization.

## ADIF

`.adi` and `.adif` files retain the existing ADIF import behavior, including the
callsign/band/mode duplicate policy and individual QSO broadcasts. Parse errors
and duplicate records can still be saved as failed ADIF records.

Both importers log the completion report counts. Parse-error logs also contain
the rejected record and, for journals, its line number. File-read or store-write
failures are shown in the dialog and logged as import failures.
