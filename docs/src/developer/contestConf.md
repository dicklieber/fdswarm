# Contest Configuration
All nodes need to be configured with the following parameters:

```aiignore
/**
 * @param transmitters number of transmitters
 * @param ourCallsign  our callsign
 * @param ourClass     our class
 * @param ourSection   our section
 * @param contestType WFD or ARRL
 */

case class ContestConfig(
    contestType: ContestType,
    ourCallsign: Callsign,
    transmitters: Int = 1,
    ourClass: String,
    ourSection: String)
 
```

The ContestConfig is used primarily in the Qso entry panel to indicate what classes are allowed. (They are different for WFD and ARRL).
and in the exchange display:
```
We are 1H IL
```


Contest types are loaded at startup by the injectable `ContestTypes` singleton.
The bundled files `common/resources/contestTypes/ARRL.json` and
`common/resources/contestTypes/WFD.json` each include
`name`, `displayName`, `cabrilloContestName`,
`classChoices`, and a `dates` object. Contest definitions are no longer read from
`reference.conf` or application configuration overrides. `ContestTypes` loads
the two resources explicitly in ARRL, WFD order, including when packaged in a JAR.

The date rule contains `month`, `fullWeekend`, `startUtc`, `endUtc`, and optional
`setup` (`daysFromSaturday`, `startUtc`, `maxCumulativeDuration`). End times
identify the final operational minute, inclusive. `maxCumulativeDuration` is a
duration string, such as `"12h"`.

Contest types in saved logs and replication messages encode as complete objects:
`name`, `displayName`, `cabrilloContestName`, and `compute` (the date-rule object).
Edits therefore survive persistence and replication without resolving the type
against the receiving node's bundled definitions. The old name-only format is
not supported.

`ContestConfig` also contains `timeStamp` and `fingerPrint`. Each local save sets
a fresh timestamp and calculates an 11-character fingerprint using the first
eight bytes of SHA-256. Its input is canonical JSON for the complete configuration,
including the nested contest definition and timestamp, excluding `fingerPrint`.
Receiving or adopting a saved version preserves both fields.

The configuration dialog has an expandable **Edit contest definition** section
for names, UTC date rules, and the optional setup period. Contest names remain
ARRL or WFD because class choices are selected by that resource name. Timestamp
and fingerprint fields are managed automatically and are not editable.
Scoring uses the same QSO points for every contest: phone QSOs count as one
point, and CW and digital QSOs count as two points each. Power, objectives, and
other contest-specific bonuses do not affect the displayed score.

There is no placeholder contest type. An unconfigured node holds
`Option[ContestConfig] = None`. Status messages encode an absent configuration as JSON `null`.
The configuration dialog initially offers the first configured contest without
activating it until the user accepts the dialog.

`ApplicationConfig` resolves `config/sarasec.conf` over the standard configuration.
Dependency injection uses `ContestTypes.configured` for the bundled templates.
Importers receive their contest or metadata explicitly.
