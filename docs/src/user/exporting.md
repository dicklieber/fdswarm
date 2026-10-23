# Exporting logs

ADIF and Cabrillo exports use the contest callsign from **Contest Configuration**.
ADIF also includes each QSO's recorded operator separately as `OPERATOR`.
Dates and times are UTC: ADIF exports seconds (`HHmmss`), while Cabrillo exports
minutes (`HHmm`). Neither format preserves fractional seconds.

Cabrillo uses `CW`, `PH` and `DG` for the application's three mode categories.
All supported bands have a frequency/band field. VHF and higher bands use Cabrillo
identifiers such as `50`, `144`, `1.2G` and `10G`. HF values are representative
frequencies because the QSO model stores a band, not an actual operating frequency.
An unknown or out-of-band Cabrillo value is rejected rather than silently exported
as an empty field.

The application currently records only a generic digital category, `DIGI`.
ADIF's standard `MODE` field accepts mode names such as FT8 or RTTY, and does not define
`DATA` as a mode. For generic digital contacts, exports omit the standard `MODE`
field and retain `DIGI` in `APP_FDSWARM_MODE`. FDSwarm imports this application
field when standard `MODE` is absent, allowing its own exports to round trip.
Other applications may need the actual mode supplied separately. Existing
FDSwarm exports that used `MODE=DATA` are imported as digital for compatibility.

Generated test QSOs now use the active contest's name and class choices, and
only the bands and modes selected in the Band / Mode Manager. Historical QSOs
already in the journal are preserved; changing the generator does not rewrite
their metadata or exchanges.
