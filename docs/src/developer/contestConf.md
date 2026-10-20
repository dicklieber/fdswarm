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
Each entry in `fdswarm.contests` in `common/resources/reference.conf` includes
`name`, `displayName`, `cabrilloContestName`, `allowChosing`,
`classChoices`, and a `dates` object. Date rules formerly under
`fdswarm.contestDates` now belong in each contest's `dates` object.
HOCON list overrides replace the whole contest list.

The date rule contains `month`, `fullWeekend`, `startUtc`, `endUtc`, and optional
`setup` (`daysFromSaturday`, `startUtc`, `maxCumulativeDuration`). End times
identify the final operational minute, inclusive.

Contest types always encode as their name string, including custom and modified
definitions. Decoding resolves the name using local configuration; definitions and
date rules are never embedded in JSON. Nodes must configure matching definitions.
Scoring remains implemented for WFD and ARRL; other names use the no-op scorer
and metric value zero.

There is no placeholder contest type. An unconfigured node holds
`Option[ContestConfig] = None`; every actual `ContestConfig` must use a configured
contest definition. Status messages encode an absent configuration as JSON `null`.
The configuration dialog initially offers the first configured contest without
activating it until the user accepts the dialog.

`ApplicationConfig` resolves `config/sarasec.conf` over the standard configuration.
Dependency injection and derived JSON codecs share `ContestTypes.configured`, so
both resolve names against the same definitions. Importers receive their contest
or metadata explicitly.
