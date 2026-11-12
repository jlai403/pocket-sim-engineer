# Third-party notices

## GT7 track signatures

`app/src/main/assets/track-signatures.json` is derived from the track signature data in
[jbhoorasingh/gt7-datalogger-track-data](https://github.com/jbhoorasingh/gt7-datalogger-track-data),
which is dedicated to the public domain under **CC0 1.0 Universal**.

The bundled file keeps the signature fields (`official_id`, `official_name`, `length_m`,
`min_x`, `max_x`, `min_z`, `max_z`), the racing line (`path`, thinned to a point every 20 m in
driving order) and the reverse configuration (`reverse`). The circuit names are factual metadata
about Gran Turismo 7, a product of Polyphony Digital / Sony Interactive Entertainment, included
for identification only. This project is not affiliated with or endorsed by either.

The racing-line fixture in `app/src/test/resources/road-atlanta-line.json` is a thinned copy of
the Road Atlanta capture published by
[zetetos/gt-telemetry](https://github.com/zetetos/gt-telemetry), used under the MIT licence.

Source: <https://github.com/jbhoorasingh/gt7-datalogger/blob/main/backend/app/data/track-signatures.json>
