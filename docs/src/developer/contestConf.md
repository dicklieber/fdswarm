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
`name` (stable ID), `displayName`, `cabrilloContestName`, `allowChosing`,
`classChoices`, and a `dates` object. Date rules formerly under
`fdswarm.contestDates` now belong in each contest's `dates` object.
HOCON list overrides replace the whole contest list.

The date rule contains `month`, `fullWeekend`, `startUtc`, `endUtc`, and optional
`setup` (`daysFromSaturday`, `startUtc`, `maxCumulativeDuration`). End times
identify the final operational minute, inclusive.

Existing default types retain their string JSON representation. Custom or
modified definitions include their fields and date rules in JSON so saved
records and receiving nodes can reconstruct them. Older application versions
cannot read that extended representation. Scoring remains implemented for WFD
and ARRL; other IDs use the no-op scorer and metric value zero.
