# HISTORICAL BACKGROUND TO THIS PROJECT

## Devices and Software released by Fender Musical Instruments Corporation (aka FMIC)

Fender Musical Instruments released a range of modelling guitar amplifiers under 
the 'Mustang' brand name over the period 2012-2015, with different models within 
the range named using roman numerals I, II, III, IV and V.  

FMIC released a free companion application called 'Fender FUSE' for amplifiers in
this generation, allowing preset amplifier sounds to be edited, backed up, and 
shared with other users.  The first party 'Fender FUSE' software was built for 
Windows and macOS computers only.

Since 2016, FMIC have released the following newer generations of modelling 
amplifiers:

* Mustang/Rumble LT- Series
  - [Mustang LT25](https://www.fmicassets.com/Damroot/Original/10001/OM_2311100000_Mustang_LT25_English.pdf)
  - [Rumble LT25](https://www.fmicassets.com/Damroot/Original/10001/OM_2270100000_Rumble_LT25_English.pdf)
  - [Mustang LT50](https://www.fmicassets.com/Damroot/Original/10001/OM_2311200000_Mustang_LT50_expanded_manual_ENGLISH.pdf)
  - [Mustang LT40S](https://www.fmicassets.com/Damroot/Original/10001/OM_23114XX000_Mustang_LT40S_US.pdf)
* Mustang 
  [GT-](https://www.fmicassets.com/Damroot/Original/10001/231010_Mustang%20GT%20manual%20ENG%207712493000a.pdf)
  and 
  [GTX-](https://www.fmicassets.com/Damroot/Original/10001/OM_2310700000_Mustang_GTX_expanded_manual_ENGLISH.pdf) 
  Series
  - Mustang GT40
  - Mustang GT100
  - Mustang GT200
  - Mustang GTX50
  - Mustang GTX100
* Mustang Headphone amplifiers
  - [Mustang Micro](https://www.fmicassets.com/Damroot/Original/10001/OM_2311300000_Mustang_Micro_US.pdf)
  - [Mustang Micro Plus](https://www.fmicassets.com/Damroot/Original/10062/OM_2311600000_Mustang-Micro-Plus_EN.pdf)
    (for the remainder of this document the Mustang Micro Plus device will be referred to as 'MMP')

FMIC have released new companion apps to work with these:
  - for the LT- series: Fender Tone LT Desktop
    (requires USB connection, versions exist for Windows and macOS);
  - for the GT- and GTX- Series and for the MMP: 
    [Fender Tone mobile](https://www.fmicassets.com/Damroot/Original/10062/APG_Tone-User-Guide_EN.pdf) 
    (requires Bluetooth Low Energy connection, versions exist for Android and iOS).

There is no companion app to work with the (non-'Plus') Mustang Micro.  

With the release of these new models and applications, FMIC have closed down the 
web servers which supported the preset backup and sharing capabilities of the 
'Fender FUSE' application and stopped distributing that application from 
their website (but see later in this document for a link to a non-FMIC site with 
archived material about this generation of hardware and software). 

## The Linux 'plug' application

The 'Universe' section of of the Debian, Ubuntu, and related distributions contains
a package with name 'mustang-plug', which provides a Linux desktop user a GUI with 
comparable capabilities to FMIC's proprietary 'Fender FUSE' applications for Windows 
and macOS.

This application was originally written by a developer working under the 
handle @piorefk, who no longer appears to be active anywhere under the Internet 
(at least under that name), but a source code repository forked from the Debian 
package source is now maintained [by GitHub user @offa](https://github.com/offa/plug).

The 'plug' application is a C++/Qt application which can exchange USB messages with 
FMIC devices from the 2012-2015 generation of amplifiers which were compatible 
with Fender's original 'Fender FUSE' application. The plug application can also 
back up and restore preset definitions to a local filesystem using an XML-based 
format, saving files with the filename extension '.fuse'.  I don't know whether 
the .fuse export capability was also present in 'Fender FUSE' or whether it was 
an innovation introduced by the 'plug' application.

## Other open source and web resources

There are a number of other repositories on Github and other parts of the Internet 
which contained interesting material related to FMIC modelling amplifiers (including 
future envisioned work related to BLE transport support) including:
* https://github.com/brentmaxwell/LtAmp
* https://web.archive.org/web/20200621233329/https://bitbucket.org/piorekf/plug/wiki/Home
* https://github.com/snhirsch/mustang-midi-bridge
* https://www.bluetooth.com/specifications/specs/hogp-1-0/
* https://fender-mustang-amps-and-fuse.fandom.com/wiki/Fender_Mustang_Amps_and_Fuse_Wiki
* https://guitarpedaldemos.com/fender-fuse-mustang-v2-archive/
  which serves archive copies of the FMIC's manuals for 
  - [the 2012-2015 device range](https://guitarpedaldemos.com/wp-content/uploads/2020/04/MustangI-V_v.2_advanced_manual_revA_English.pdf);
  - [a version of the Fender FUSE application](https://guitarpedaldemos.com/wp-content/uploads/2025/06/Fender_FUSE_2.0_manual_for__Mustang_1-2_Rev-G_English.pdf); and
  - [an archive of more than 9000 preset definitions in Fender FUSE formats, 
    collected before FMIC shut down the web site provided to Fender FUSE 
    users for storing and sharing presets](https://guitarpedaldemos.com/wp-content/uploads/2020/04/entire-archive.zip)
* [this Google Sheets page containing requests and answers for Mustang 
  presets matching particular artists/songs](https://docs.google.com/spreadsheets/d/1KWvjJ6q6Ora2MqmxtUAWjq9Uerp0Ycza5ouuKuol5HQ/edit?pli=1&gid=0#gid=0)
* The Remuda and Remuda/SC apps published on the Play Store by 
  [Triton Interactive](https://play.google.com/store/apps/developer?id=Triton+Interactive), 
  which are Android apps interoperating with the 2012-2015 range of Fender Mustang amps models.

## My personal fork of plug

The current project started as an attempt by myself (GitHub user @tim-littlefair)
to expand the capabilities of the @offa's GitHub-hosted fork of the mustang-plug 
application so that it could work with the Mustang LT40S device I own.

I was able to implement the message protocol changes required to get the LT40S 
to export preset definition files in a JSON-based file format.  I am confident 
that if I had continued with the fork I would also have been able to implement 
a capability to import JSON preset files back into the LT40S, giving owners
of the device a means of backing up and restoring presets without needing to 
rely on continued operation of the FMIC-controlled web infrastructure backing 
the proprietary Fender Tone applications.

Although I found that it was possible to add some LT- series support to the 
plug application, I could only this by making major changes to the structure 
of the plug codebase, and without access to at least one of the older Mustang I - 
Mustang V generation devices I had no way of testing to determine whether 
my changes had broken the capabilities of plug in relation to those older devices.

My plug fork is hosted on GitHub.  The 'master' branch in my forked repository 
does not contain any new material relative to @offa's upstream repository, and
has now fallen some way behind that version, but there are a number of branches
containing different baselines, of which the most recent and inclusive one is
https://github.com/tim-littlefair/plug/tree/LT40S-support-1019.

I am not actively working on my fork of plug at the moment, but I will consider
returning to it at some time in the future if and when I bring my own project
(described below) to a stable baseline, or if I pause or abandon or suspend 
work on it for some other reason.

## My current project

At this point I decided to set up an independent project, working primarily
in Java (with both desktop and Android application target).  I was massively 
helped by the availability of the Maven/Gradle package repository 
infrastructure, and especially by the availability of the following 
open source projects which I was able to reuse to implement separate 
USB transport layer implementations for Android Java and Desktop Java 
respectively:
* https://github.com/benlypan/UsbHid
* https://github.com/gary-rowe/hid4java

After some exploratory work, I've established that the on-the-wire/on-the-air 
protocols used by both LT-series and MMP devices replay preset definitions as
payloads in JSON-based formats.  As the GT-/GTX- series interoperate with 
the same mobile companion app as the MMP, I think I can assume that the format 
used by these will be very similar. Although the LT-series and MMP JSON formats
aren't identical, the structures of the formats are very similar (with 
some differences in JSON key naming), and I believe that it will almost 
certainly be possible to cross-translate the majority of presets between the 
two JSON dialects, providing that the source preset does not use any 
audio model modules which are not present in the target device type.

I've now arrived at 
[a rough architecture](ARCHITECTURE.md) 
for the project, and identified the following items as the short-to-medium 
term project goals:
* to support USB connectivity to LT-series devices;
* to support Bluetooth Low Energy connectivity to Mustang Micro Plus;
* some level of support for GT-/GTX- series devices may be achieved if the 
  protocol required for these is sufficiently similar to Mustang Micro Plus;
* for all supported devices provide capabilities to import and export preset 
  definitions in human readable/writeable format and if possible enable 
  translation of JSON presets exported from the LT- series into MMP dialect
  and vice versa; and
* to attempt to provide a range of applications based on a single common 
  library, implemented in Java, capable of being deployed in multiple types 
  of target hardware including:
  - as a command line interface (CLI) application running on Linux laptop, 
    desktop and small form factor/low power Linux devices (e.g. RPi, Intel NUC,
    Beagleboard);
  - command line application could also possibly run on macOS and Windows,
    but these will not be primary targets;
  - as a native mobile application for Android mobile phones and tablets;
  - as a web server application running on the same hardware as the CLI 
    application serving GUI based on HTML5 technologies which can be operated
    via a browser client on the same hardware as the server, or on a remote device
    on the same wireless network.

In all of this, my objective will be to 'scratch my own itches', practice
curiousity and minimalism and deliver open source software which is 'just 
barely good enough' to be useful to myself. 

In the event that all or most of the goals above are achieved I may consider
working to translate some or all of 
[the Fender FUSE preset collection mentioned 
above](https://guitarpedaldemos.com/wp-content/uploads/2020/04/entire-archive.zip)
to the JSON dialects for the LT- series and MMP device (if I take this on I will
need to be careful to ensure that I don't fall into the trap of violating 
FMIC IP restrictions by re-publishing presets they own copyright on without 
explicit permission from FMIC).  I'd also be interested in extending the common 
library to interoperate with the older generation of devices if I came to have access 
(either personally or via a project contributor) to device(s) of that generation 

I have decided, for the time being, that scope of the project will not attempt 
to include any of the following capabilities:
* deployment as a native iOS application (as this would require porting of the 
  'single common library' from Java to C/Objective C/Swift, and then continuing 
  to maintain parallel versions); or
* capability to edit presets using a GUI (as the preset export/import 
  JSON format for preset will be human readable).

My own work is released on GitHub under GPL-v2.0 licensing conditions.  
I welcome others to use and comment on the software, fork the source code and 
raise pull requests (on the understanding that a pull request will be interpreted
as perpetual permission for me to use the contributed code under 
GPL-v2.0 or any other licensing conditions I choose).

## Current status

The current status is that a common library, Android application and desktop
Linux command line application have been implemented and interoperate with 
the LT40S device over USB.  

The library and applications do not yet contain any support for interoperability
with Mustang Micro Plus over Bluetooth Low Energy, but work has been started
on reverse engineering the MMP's protocol and JSON preset format (which is not 
identical to that used by the LT40S, but appears to be broadly similar and 
with translation between the two dialects appearing very feasible).

A prototype web application exists, based on the CLI application running
in a mode where it dumps information about the state of the connected 
amp to a session subdirectory the filesystem, mainly in JSON format, and 
a web server application written in Lua generates HTML5 UI pages by combining 
static templates with the JSON files in the session subdirectory.  The 
web application is served using 
[the 'Pegasus' Lua web server](https://github.com/EvandroLG/pegasus.lua).





