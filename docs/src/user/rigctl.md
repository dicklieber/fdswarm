# Rig Control (rigctld)

FdSwarm can track your transceiver's current band and mode automatically using
**rigctld**, the rig-control daemon that is part of the
[Hamlib](https://hamlib.github.io) project.

When enabled, FdSwarm polls rigctld over a TCP connection and updates the
active band and mode in the logging window without any manual input.

## Configuration fields

- [Rig Device](rigctl/rigDevice.md) — serial / USB port used to connect to the radio
