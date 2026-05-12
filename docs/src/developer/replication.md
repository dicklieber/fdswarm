# QSO Replication

In order for all nodes to have a complete and up-to-date view of the QSO journal, a replication mechanism is required. This mechanism ensures that when a new node joins the swarm, it can catch up with the latest QSOs and synchronize its QsoStore with the rest of the nodes.

## StatusMessage Broadcast
Every 10 seconds, each node broadcasts a UDP StatusMessage.
```aiignore
case class StatusMessage(
    storeStats: StoreStats,
    bandNodeOperator: BandModeOperator,
    contestConfig: ContestConfig,
    contestStart: Instant,
    metrics: Seq[MetricStat]
)
```

The StoreStats field storeStats is used to determine if the QsoStore needs updating. Other fields don't affect replication.
Every node receives the broadcast StatusMessage

```
case class StoreStats(
    hash: String = "",
    qsoCount: Int = 0,
    ourQsoCount: Int = 0
) derives Codec.AsObject, sttp.tapir.Schema:
  def needsUpdate(other: StoreStats): Boolean =
    hash != other.hash || qsoCount != other.qsoCount
```
ourQsoCount is not used as a part of replicatiob feature.

Each compares it's own storeStats with the other nodes' storeStats using needsUpdate. If it needs updating:
It sends an HTTP GET request "/qsos" to the node that sent the StatusMessage, 
The response contains:
```
"qsos": [
    {
      "callsign": "WA9NNN",
      "exchange": "1D IL",
      "bandMode": "20m PH",
      "qsoMetadata": {
        "station": {
          "operator": "WA9NNN",
          "rig": "",
          "antenna": ""
        },
        "node": {
          "hostIp": "192.168.0.59",
          "port": 8079,
          "hostName": "MPB-WA9NNN",
          "instanceId": "g6I"
        },
        "contest": "WFD",
        "v": "0.0.0-dev"
      },
      "stamp": "2026-05-11T13:19:19.050790Z",
      "uuid": "TbyRyWetReunqRJZ5J7mNg"
    }
  ]
```
each qso is the same shape as any QSO.
Any duplicate QSOs, as determined by the UUID, are ignored. Thus only QSOs that are new to the node are added to the QsoStore.
