# Rig Device (`rigDevice`)

The **Rig Device** field tells `rigctld` how to reach your radio hardware.
It maps directly to the `-r` flag passed on the `rigctld` command line.

Leave this field **empty** when:

- you are using the **Dummy** rig (model 1) for testing, or
- your rig is connected over the **network** (e.g. FlexRadio, Icom RS-BA1)
  and `rigctld` does not need a local serial port.

---

## Serial / USB rigs

### Linux

USB-to-serial adapters typically appear as `/dev/ttyUSB0`, `/dev/ttyUSB1`, etc.
Native serial ports use `/dev/ttyS0`, `/dev/ttyS1`, etc.

```
/dev/ttyUSB0
```

To find the correct device after plugging in your interface:

```bash
dmesg | grep tty
```

You may also need to add your user to the `dialout` group:

```bash
sudo usermod -aG dialout $USER
```

### macOS

USB serial adapters appear under `/dev/` with names like:

```
/dev/cu.usbserial-A10MFCZB
/dev/cu.SLAB_USBtoUART
```

To list available ports:

```bash
ls /dev/cu.*
```

### Windows

Serial ports use the `COMn` notation (e.g. `COM3`, `COM14`).

```
COM3
```

Find the correct port number in **Device Manager → Ports (COM & LPT)**.

---

## CI-V / CAT interfaces

| Interface type | Typical device path |
|---|---|
| USB direct (Icom, Yaesu) | `/dev/ttyUSB0` (Linux) · `/dev/cu.usbserial-*` (macOS) · `COMn` (Windows) |
| RS-232 via adapter | `/dev/ttyS0` (Linux) · `/dev/cu.usbserial-*` (macOS) · `COMn` (Windows) |
| Rigblaster / SignaLink | Same as USB serial above |

---

## See also

- [Rig Model](rigModel.md) — choose the correct Hamlib model number for your radio
- [Port](port.md) — TCP port `rigctld` listens on
- Hamlib supported rigs: `rigctl -l`
