### How to Change Log Levels

Log levels for the FDLog Swarm application are configured in `fdswarm/resources/loggers.conf`.

#### To change the log level for a specific component:

1.  Open `fdswarm/resources/loggers.conf`.
2.  Locate the `loggers` block.
3.  Add or change the level for the desired logger. For example, to change `fdswarm.replication.NodeStatusHandler` to `DEBUG`:

```hocon
loggers {
  ...
  "fdswarm.replication.NodeStatusHandler" = "DEBUG"
}
```

#### Available Log Levels:
- `TRACE`
- `DEBUG`
- `INFO`
- `WARN`
- `ERROR`
- `FATAL`
- `OFF`

The changes will take effect after you restart the application.

#### Technical Details:
The configuration is loaded by `fdswarm.util.LoggingConfigurator` using Typesafe Config and applied to Log4j2 programmatically.
