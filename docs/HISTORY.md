# HISTORICAL BACKGROUND TO THIS PROJECT

## Devices and Software released by Fender Musical Instruments Corporation (aka FMIC)

Fender Musical Instruments released a range of modelling guitar amplifiers under 
the 'Mustang' brand name over the period 2012-2015, with different models within 
the range named using roman numerals I, II, III, IV and V.

FMIC released a free companion application called 'Fender FUSE' for amplifiers in
this generation, allowing preset amplifier sounds to be edited, backed up, and 
shared with other users.  The first party 'Fender FUSE' software was built for 
Windows and macOS computers only

Since 2016, FMIC have released the following newer generations of modelling 
amplifiers:

* Mustang/Rumble LT- Series
  - Mustang LT25
  - Rumble LT25
  - Mustang LT50
  - Mustang LT40S
* Mustang/Rumble GT- and GTX- Series
  - Mustang GT40
  - Mustang GT100
  - Mustang GT200
  - Mustang GTX50
  - Mustang GTX100
* Mustang Headphone amplifiers
  - Mustang Micro
  - Mustang Micro Plus

FMIC have released new companion apps to work with these:
  - Fender Tone LT Desktop for the LT- series 
    (requires USB connection, versions exist for Windows and macOS)
  - Fender Tone mobile for the GT- and GTX- Series and for the Mustang Micro Plus 
    (requires Bluetooth Low Energy connection, versions exist for Android and iOS)

There is no companion app to work with the Mustang Micro.

With the release of these new models and applications, FMIC have closed down the 
web servers which supported the sharing capabilities of the 'Fender FUSE' application 
and stopped distributing that application from their website (although archived 
copies are available from some non-Fender sites on the Internet).  

## The Linux 'plug' application

The 'Universe' section of of the Debian, Ubuntu, and related distributions contains
a package with name 'mustang-plug', which provides a Linux desktop user a GUI with 
comparable capabilities to the 'Fender FUSE' for Windows or macOS.  

This application was originally written by a developer working under the 
handle @piorefk, who no longer appears to be active anywhere under the Internet 
(at least under that name), but a source code repository based on the original is 
now maintained [by GitHub user @offa](https://github.com/offa/plug)

The 'plug' application is a C++/Qt application which can exchange USB messages with 
FMIC devices from the 2012-2015 generation of amplifiers which were compatible 
with Fender's original 'Fender FUSE' application.

## The current project

The current project started as an attempt by myself (GitHub user @tim-littlefair)
to expand the capabilities of the @offa's GitHub-hosted fork of the mustang-plug 
application so that it could work with the Mustang LT40S device I own.

Although I was able to make significant progress in implementing the message 
protocol changes required to interoperate with the LT-series device, I found 
that it was only possible to do this by making major changes to the structure 
of the plug codebase, and without access to at least one of the older Mustang I - 
Mustang V generation devices I had no way of testing to determine whether 
my changes had broken the capabilities of plug in relation to those older devices.

At this point I decided to set up an independent project, with the following aims:
* to support USB connectivity to LT-series devices
* to support Bluetooth Low Energy connectivity to Mustang Micro Plus
* support for GT-/GTX- series devices may be achieved if the protocol required
  for these is sufficiently similar to Mustang Micro Plus
* for all supported devices provide capabilities to import and export preset 
  definitions in human readable/writeable format and if possible migrate 
  preset definitions between device types

I have, for the time being, decided that scope will not include any capability 
to edit presets using a GUI - as the preset export/import format will be human 
readable, users would be able to use any preferred text editor to make changes 
(or other developers might choose to take on this challenge as a separate editor
tool).

