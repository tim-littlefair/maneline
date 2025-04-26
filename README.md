# Feral Horse Amp Utils

A collection of code and data related to the protocols used to control Fender Musical Instrument Corporation's Mustang® modelling guitar amplifiers

# Abbreviations and Definitions

+ FHAU = Feral Horse Amp Utils

  How to pronounce FHAU:
  - Pronounce as 'fwaw', rhyming with 'awe';
  - or you could just sound it out as letters F-H-A-U
  
+ FMIC = Fender Musical Instrument Corporation

+ modelling amplifier

  A smart physical (guitar) amplifier product which enables software models of 
  specific or generic prior amplifiers and guitar effect filters/pedals to be selected,
  so that the modelling amplifier is able to produce a wide range of sounds 
  imitating the effect of the prior amplifiers and effects.
  
  FMIC's amplifiers sold under the 'Mustang' brand are examples of the 
  generic category of modelling amplifiers.

+ preset

  In relation to a modelling amplifier, a setting of the amplifier which 
  causes it to imitate a particular combination of a base amplifier and 
  up to 4 different effects.
  For FMIC Mustang amplifiers, the presets have names and slot numbers starting
  at 1 ranging up to a capacity limit which varies between models and 
  preset versions (for example 1-60 for the Mustang LT40S product running 
  firmware 1.0.7), and can be selected using hardware controls on the amplifier 
  control panel.

# Project Vision

The vision of this project is that it contribute to the community of 
third party developers working to exploration of the capabilities of 
FMIC's Mustang modelling amplifier range and deliver third-party
software solutions to add value to these FMIC products.

Specifically, this project is expected to consist of:
- a command line desktop application which will be focussed on exporting 
  and importing the configuration files consumed by the amplifier which 
  define the preset guitar sounds the Mustang product is capable of 
  delivering;
- a mobile application for Android phones and tablets which allows the
  user to create screens grouping a manageable number of favourite 
  presets for efficient switching; and
- a common library, written in Java, which contains common logic 
  used by the two applications listed above, and which is available
  to other developers to use in their own integrations.

# Licensing and Distribution

The original software in this project is licensed under the 
GPL version 2 or any later version published by the Free Software Foundation
at the user's choice.  

The project depends on a range of upstream libraries
which are used under other licenses, including a library called UsbHid, 
developed by Github user @benlypan, available from 
[this repository](https://github.com/benlypan/UsbHid).  

I needed to make some minor changes to the code of UsbHid to integrate it 
under the most up to date Android APIs, so there is a directory 'usb-hid' 
within  my repository containing the modified version of UsbHid I have 
integrated.  There is
[an issue on my backlog](https://github.com/tim-littlefair/feral-horse-amp-utils/issues/9) 
to review the changes I made to UsbHid to determine whether they
are useful to contribute upstream, I will be creating a pull request for 
@benlypan's repository when this review is done if this review determines
that the changes are necessary or useful to other users.

The code in the usbhid directory, obviously, remains under the MIT license 
selected by @benlypan for his upstream directory.

README-usbhid.md in this repository contains the original README of the upstream repository.

I am planning to release the Android application as a paid download in the Android Play Store.

The app is presently in the Play Store's 'Open Testing' state, when it is finally released 
the Play Store entry for it will be here:
https://play.google.com/store/apps/details?id=net.heretical_camelid.fhau.android_app.

There is a link later in this document which can be used to register as a tester 
rather than a purchaser of the app.

My motivation for seeking payment is not so much to seek revenue, but more to 
discourage feedback on the app except from users who have a reasonably strong motivation 
to use it.  I would prefer users who have the necessary skills to spend the time building 
the app from source, but I will be happy to send Play Store free download vouchers to anyone 
who is interested enough in the project to send me a (non-spammy) message requesting this
via my LinkedIn profile, which is referenced on my Github profile page.  Although I don't 
expect to make significant revenue from selling the app, it would be nice to have a little 
bit of income as an insurance against the risk (described at length 
[here](https://github.com/tim-littlefair/feral-horse-amp-utils/discussions/13))
that I might brick one of my personal devices when I start to experiment with
uploading modified preset configurations to the amp in the next iteration of development.

# Status

As at the end of April 2025, initial versions have been released of 
both the desktop command line application and the Android mobile 
application.  The code used to build these versions has been 
tagged and released on Github as release 0.1.1.

The capabilities of the initial versions of the both the desktop command line 
application and the Android mobile application are described
[here](./docs/quickstart-v0.1.1.rst).

There is also short video demo of the features of the Android mobile application 
[here](https://drive.google.com/file/d/1nqPzorRsGRrGbL4jmq64_8rjcI38sLn5/view?usp=sharing).

If you are interested in trying the Android application out, it is presently in 
'Open Testing' state on the Android Play Store.  You can apply to become a tester
for the app by following 
[this link](https://play.google.com/apps/testing/net.heretical_camelid.fhau.android_app).

# Prior Art and Credits

This project was inspired by the Linux application 'plug', which is available 
in the 'Universe' section of the Debian, Ubuntu, and related distributions under
the package name 'mustang-plug', and  probably in many others.  This application 
was originally written by a developer called @piorefk, who no longer appears to 
be active anywhere under the Internet (at least under that name), but a 
source code repository based on the original is now maintained 
[on Github](https://github.com/offa/plug)
by user @offa.

The plug application is a C++/Qt GUI which replicates some of the capabilities 
of a group of applications called 'Fender FUSE', originally published by FMIC 
for Windows and MacOS(aka OS/X), but these applications are no longer available 
from or supported by FMIC.  The plug program works with a number of older 
amplifier models released under the Mustang brand name up to about 2016.  

There are a 
[couple](https://github.com/offa/plug/issues/7)
of
[tickets](https://github.com/offa/plug/issues/25)
on 
[@offa's plug repository](https://github.com/offa/plug)
discuss the possibility of adding support to his fork of plug for more recent 
Mustang models, which are interoperable with a recent desktop and moble programs 
from FMIC with names based on the string 'Fender Tone'.  For the main part, 
more recent amps with model names containing the prefix 'LT-' are controlled 
over a USB interface from Fender Tone desktop applications running on Windows 
or macOS, while amps with model name prefixes GT- or  GTX- are controlled over 
a Bluetooth Low Energy (BLE) connection from mobile applications running on 
iOS and Android.  

There is also a small inline headphone amplifier released in 
2024 called Mustang Micro Plus (MMP) which can also be controlled by FMIC's mobile 
applications over BLE, and an earlier small amp called Mustang Micro (MM) for 
which FMIC does not supply any control application.

I created a fork of the plug repository some time in which I have added some 
partial support for the Mustang LT40S model which I own, but this was some way 
from being fully useful, and without access of any of the older amps which 
plug does provide useful support for I can't be sure that the extensive changes 
I needed to make support the new Fender Tone USB protocol wouldn't break the code 
for the original users of the project for models which used the older Fender 
FUSE protocol.

FHAU doesn't use any of the plug code directly, but without the plug codebase
for initial experimentation it would not have got started, so I am very grateful
to @piorefkf and @offa for the platform which got me started on this fascinating 
project.

The architecture of FHAU is very messy at the moment, but it is intended that 
it separates concerns of the data payload protocol from the operating system 
platform and the physical datalink layer used to transport commands.  As mentioned
above the UsbHid library by @benlypan is used to provide USB transport on Android,
on desktop OSs USB transport is provided by a library called hid4java, by Github
user @garyrowe, available from (https://github.com/gary-rowe/hid4java).  

The present version of FHAU supports USB transport only, and has only been tested
with the Mustang LT40S model but I would expect that there is also a likelihood 
that it will work OK with the following other models with LT series model numbers:
- Mustang LT25
- Rumble LT25
- Mustang LT50.

If anyone who owns any of these tries the app out, I'm very interested in hearing 
whether they succeeded or failed, preferably via a comment on 
[the Github discussion page I've set up for Success/Failure reports](https://github.com/tim-littlefair/feral-horse-amp-utils/discussions/14).


# Future plans

The current version of both apps is essentially a proof of concept, and clearly requires
more work to establish a product which is useful to a general owner of any of the 
supported amps (as opposed to a tinkerer like myself).

Features I intend to work on in the near future include:
- releasing a version of the Android app on the Google Play Store so that users
  don't need to build it themselves or sideload a prebuilt copy without the 
  (limited) malware protections offered by Google Play;
- finding some way that users of the Android app can select their own screens 
  of favourite presets rather than using the fairly arbitrary ones that app builds 
  at the moment; and
- adding support to the desktop app for the user to upload a preset definition 
  to the amp.

I own a Mustang Micro Plus amplifier, from Android debug dumps of BLE traffic between 
it and the Android version of Fender Tone it looks like the data payload protocol for 
this is structurally similar to that for the LT40S, so I am hoping to add support for 
BLE transport covering this device  in a future version of FHAU.  It is possible that 
support for amplifiers in the GT-/GTX- series could be added if the data payload protocol 
and BLE transport they implement is similar enough to the corresponding protocols
for the MMP.  As the older Mustang Micro (no 'Plus') has a different method of 
selecting presets in its physical UI, I think it is unlikely that support for 
this device is feasible.

# References

There are a number of other repositories on Github and other parts of the Internet 
which contained interesting material related to FMIC modelling amplifiers (including 
future envisioned work related to BLE transport support) including:
- https://github.com/brentmaxwell/LtAmp
- https://web.archive.org/web/20200621233329/https://bitbucket.org/piorekf/plug/wiki/Home
- https://github.com/snhirsch/mustang-midi-bridge
- https://www.bluetooth.com/specifications/specs/hogp-1-0/
- https://fender-mustang-amps-and-fuse.fandom.com/wiki/Fender_Mustang_Amps_and_Fuse_Wiki
- [this archive of preset definitions in Fender FUSE formats, collected before FMIC shut down the web site provided to Fender FUSE users for storing and sharing presets](https://guitarpedaldemos.com/fender-fuse-mustang-v2-archive/)
- [this Google Sheets page containing requests and answers for Mustang presets matching particular artists/songs](https://docs.google.com/spreadsheets/d/1KWvjJ6q6Ora2MqmxtUAWjq9Uerp0Ycza5ouuKuol5HQ/edit?pli=1&gid=0#gid=0)


# Other credits

The current launch icon for the project is based on the following graphic, accessed under Vecteezy's 
'Free License' conditions:
<a href="https://www.vecteezy.com/free-vector/ride">Ride Vectors by Vecteezy</a>.

Retrieved from 
https://files.vecteezy.com/system/protected/files/004/261/707/vecteezy_black-silhouette-of-a-horse-on-a-white-background-vector-illustration_4261707.zip?response-content-disposition=attachment%3Bfilename%3Dvecteezy_black-silhouette-of-a-horse-on-a-white-background-vector_4261707.zip&Expires=1733290772&Signature=TivU3KsbVg-djV7KRsNmYmf~-3fbSjZMCpk-TnnQegha~6~xuGHwT4uBWg3vIK7NXE-7mKpYOy9Wjo~cZtQ-pWntNA6RtcFTSbU-oy8WwYIQc5n6LsHqUwecB4dT4UFi-21dRJhItH4OFFCeMdVBCV6BhC-V4jktwfv3N-o80WTqMR~0fkOLOTKsXYJ7kW2spUOAWUzpopX3Lhy1OvgQauK1v-uWQAyvw2e51WXmlQYnYooDARXXR5jitcpBAE5iGHwTihVfCHPzMruiMvXo1BssAobb3z1ncBGWXTPsfMba7RSWyyCHQZ97cUVxuTtpgOVwmaD6uJOPb4m8ZT4tkw__&Key-Pair-Id=K3714PYOSHV3HB
on 4 December 2024, zip file stored in the /assets directory.


