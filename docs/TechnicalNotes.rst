Technical Notes
===============

udev rules
----------
The udev rules file /usr/lib/udev/rules.d/50-mustang.rules which is
installed by the very old version of mustang-plug which is available
in the Ubuntu 24.04 LTS repository contains of multiple lines like
this:

```
SUBSYSTEM=="usb", ENV{DEVTYPE}=="usb_device", ATTRS{idVendor}=="1ed8", ATTRS{idProduct}=="0015", GROUP="plugdev"
```

The purpose of this rule is to allow user-space access to the device.
Without it, it is necessary to run any given program with root
privileges using 'sudo' in order to be able to open the USB device.

I added a similar line with no idProduct filter to try to get my
Mustang LT40S enabled for userspace access, but this did not work
for my Java/USB/CLI example.1ed8

After looking at
https://unix.stackexchange.com/questions/85379/dev-hidraw-read-permissions
I added the following line instead:

```
SUBSYSTEM=="hidraw", ATTRS{idVendor}=="1ed8", MODE="0660", GROUP="plugdev", TAG+="uaccess"
```

With this line the CLI example started working.

Note that for some reason, this was not required to get the precursor plug
program working in userspace.  Not sure why yet.


