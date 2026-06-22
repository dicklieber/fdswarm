# Override Command Line (`cmdOverride`)

The **Override Command Line** field allows fully customizing the rigctld command line

Leave this field **empty** to let FdSwarm generate the rigctld command line.

## Examples:

### Flex 8600 with CAT port set to 5001

```rigctl -m 2036 -r localhost:5001```

### ICOM 7300 on Microsoft Windows
```rigctld -m 3073 -r COM5 -s 19200 -t 4532 -c 0x93```
* -m 3073 = Icom IC-7300
* -r COM5 = serial port
* -s 19200 = CI-V baud rate (must match the radio)
* -t 4532 = rigctld TCP listening port
* -c 0x93 = Override default CI-V control byte with 0x93 (Note default on radio is 0x94)

For detailed information, see the [Hamlib Wiki ](rigctl/https://github.com/Hamlib/Hamlib/wiki).