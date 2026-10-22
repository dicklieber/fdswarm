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

Contest types in `ContestConfig` persistence and HTTP responses encode as complete objects:
`name`, `displayName`, `cabrilloContestName`, and `compute` (the date-rule object).
Edits therefore survive persistence and replication without resolving the type
against the receiving node's bundled definitions. The old name-only format is
not supported.

Each QSO's `QsoMetadata.contest` stores only `ContestType.name` as a JSON string,
such as `"WFD"`; it does not embed or resolve a contest definition.

`ContestConfig` also contains `timeStamp` and `fingerPrint`. Each local save sets
a fresh timestamp and calculates an 11-character fingerprint using the first
eight bytes of SHA-256. Its input is the case-class fields, recursively including
the nested contest definition and timestamp, excluding the outer `fingerPrint`.
Config object keys are sorted so their ordering does not affect the fingerprint.
Receiving or adopting a saved version preserves both fields.

UDP status messages advertise only `fingerPrint` and `timeStamp` for the contest
configuration. The advertised timestamp is URL-safe, unpadded Base64 of signed,
big-endian epoch nanoseconds (11 characters for contemporary dates). Nodes decode
it before comparing versions; Base64 string ordering does not indicate recency.
Equal timestamps are resolved by lexical fingerprint order.

On discovering a newer version, a node fetches the full configuration from
`GET /contestConfig` on the advertising node. Only one configuration request is
in flight at a time. The response fingerprint is checked and its version must
be at least the advertised version and newer than the current local version.
HTTP runs asynchronously; adoption and UI updates run on the JavaFX thread.
Periodic status broadcasts retry failures and allow reconnecting nodes to catch
up. Local edits advertise their new version immediately. Recency assumes
reasonably synchronized node clocks. All nodes need the updated status format.

The swarm display resolves full configuration details from an in-memory cache.
An uncached older version displays `Details pending` until its details are known;
only newer versions trigger automatic HTTP requests.

The configuration dialog selects ARRL or WFD and edits station settings; contest
metadata and date rules are not exposed for editing. An optional `endTime`
(`Option[LocalDateTime]`) controls only the contest-end display. Users can enable
the countdown and select a local date and time using a GemsFX 24-hour `HH:mm`
picker with popup selection, or disable it to save
`None`. The countdown is green before the end and red at or after it. With no end
time there is no countdown; resource date rules do not supply a fallback.
The end time is saved and synchronized with the complete configuration. Nodes
are assumed to use the same local time zone. Timestamp and fingerprint fields
are managed automatically and are not editable. On loading a saved configuration,
its fingerprint is recomputed to account for schema changes without changing its
timestamp. The separate contest-start state is unchanged.
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
