# Rig Control Configuration

The **Rig Control** section configures how FdSwarm connects to
[rigctld](https://hamlib.github.io) (the Hamlib rig-control daemon) to
automatically track your transceiver's band and mode.

## Configuration fields

| Field | Description |
|---|---|
| `enabled` | Enable automatic rigctld polling at startup |
| `rigctldPath` | Filesystem path to the `rigctld` binary |
| `rigModel` | Hamlib rig model number (use the **Select Rig** picker or run `rigctl -l`) |
| `rigDevice` | How rigctld reaches the radio — serial port, IP address, etc. |
| `host` | Hostname where rigctld is listening (default `localhost`) |
| `port` | TCP port rigctld listens on (default 4532) |
| `pollIntervalMs` | How often FdSwarm queries the rig for band/mode changes (ms) |
| `cmdOverride` | Fully replace the generated command line with a custom one |

## Details

- [Rig Device](rigDevice.md) — serial/USB port or network address for your radio
- [Override Command Line](cmdOverride.md) — advanced: replace the generated rigctld command line

## Testing the connection

Use **RigCtl → Test** in the menu to open the test dialog, which lets you:

- Launch or relaunch rigctld and view its live output
- Poll rigctld in real time to verify frequency and mode are being read correctly
