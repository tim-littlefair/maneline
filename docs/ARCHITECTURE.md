# Architecture of the maneline Codebase

Notes below are believed correct as at February 2026.

## Component Model

As at the document date noted above, a modular structure for the project 
seems to be emerging, which is reflected in selected subdirectories of 
the GitHub repository layout, with the following being the major modules:

### maneline-lib

  This module is a Java library, written to be portable between integrated
  applications built in Android Java and some or all of the non-Oracle 
  alternate desktop Java Development Kit implementations.

  The module contains Java framework code based on the the abstract concept
  of an 'Amp Provider', which is an object which can perform operations on Fender
  LT-series devices (and can hopefully be extended to operate on Fender products
  from other ranges at some time in the future).  
  
  The module includes classes which encapsulate the on-the-wire protocol and 
  interaction sequence required for an Amp Provider to interoperate with the 
  supported Mustang I/II/III/IV/V (versions 1 and 2) and LT- series devices, 
  but does not include an implementation of the transport layer required for 
  interoperation with a device over its physical USB physical interface.  

  The maneline-lib module is consumed by the android-app and 
  desktop-app modules, each of which provides its own implementation of the 
  Amp Provider concept class based on different third-party libraries supporting 
  the USB-HID framework for the different target platforms (Android and Desktop 
  Linux).
  
  The provider support code in the maneline-lib module includes an implementation 
  of the message protocol used by the Fender Mustang LT40S device capable of:

  * sending the LT40S device a command to switch to a specified preset; and
  
  * exporting JSON files from the LT40S device which represent the configuration
    of preset amplifier definitions.

  It is expected that this protocol implementation would be equally interoperable 
  with any of the following other post-2016 USB-controlled FMIC devices:

  * Fender Mustang LT25;
  
  * Fender Mustang LT50; and

  * Fender Rumble LT25.

  No testing has been done to date to determine whether these devices are interoperable
  with the current codebase.

  As well as the LT-series devices listed above, a separate message protocol class has been
  implemented which provides initial support for the Mustang I v2 model, and which will presumably
  also interoperate with other models from versions 1 and 2 of the 2012-2016 Mustang I/II/III/IV/V
  range.  This module is also able to send preset switch command and to export preset definitions 
  in a JSON format, although more work is required to calculate preset parameter values in exported 
  preset files for these models which are directly comparable to the values shown for the same
  parameters in LT-series exports.

### desktop-app

  This module is a command line interface console program written in Java, built on top 
  of the 'maneline-lib' module, which includes a concrete implementation of the LT-series 
  USB Amp Provider capable of running on a desktop or laptop computer running Linux (with 
  appropriate dependency packages installed).  

  The CLI program is provided primarily as a platform for development, testing, 
  and capability exporation. 

  This module is presently tested on Ubuntu 24.04.3 LTS (AMD64 architecture).

  The primary executable in this module has also been built, deployed and tested 
  on various Raspberry Pi hardware variants.  See the notes below on module 
  'deployment/balena' for more details of how desktop-app contributes to the 
  Balena/RPi deployment target.

### web-app
  This module is a script written in Lua which brings up a web server
  using [Pegasus.lua](https://github.com/EvandroLG/pegasus.lua), starts a   
  the desktop-app program described above as a subprocess, and throws up 
  a web UI which displays the current amplifier state, allows the user to 
  commands to select a different preset, and allows the user to explore
  preset definition files and log files of the USB data traffic between
  desktop-app and the amplifier.

### android-app

  This module is an Android mobile device application, also written in Java, built on 
  top of lib, which includes a concreate implementation of the LT-series USB Amp Provider 
  capable of running on an Android mobile phone.

  The provider implemented in this application presently requires the Android mobile device 
  to be directly connected to the LT- series amplifier via a USB cable.

  This module still builds, but has not been maintained or tested since focus shifted to 
  delivering CLI and web user experiences via the desktop-app/web-app modules.

### deployment/balena
  
  This module is a bundle of software deployable to various variants of Raspberry Pi
  using the infrastructure and software framework developed by [Balena](https://balena.io).
  The software bundle includes:
  
  + a copy of the desktop-app executable; 

  + small suite of Lua scripts which implement a minimal HTTP-only web server which 
    presents a UI through which the operation of the desktop-app application (running
    as a subprocess of the web server) can be controlled; and

  + a web browser client running on the RPi's local HDMI display device which is 
    configured to operate as a kiosk displaying the web application served by the 
    HTTP-only web server.

### Local copy of UsbHid package published by GitHub user @benlypan

The android-app module depends on a library supplied by a third-party Java package
cloned from https://github.com/benlypan/UsbHid, which required (very minor) code changes
to build and integrate under the Android SDK version I am using for maneline.  
The upstream package is released under the MIT license, and does not appear to have been
modified since the initial two commits were uploaded in 2017.

For the moment, the maneline Git repository contains a copy of the usb-hid subdirectory
of the upstream GitHub repository with the necessary changes cut in.  I have not yet
raised a pull request on the upstream repository with these changes, as I am not
sure how to do this without potentially breaking the build of the package under
the very old Android SDK version referenced in the upstream's build.gradle
file (compileSdkVersion and targetSdkVersion are 24, buildToolsVersion is "25.0.0").


## Build Environment, Third Party Dependencies and Supply Chains
  
  The main development environment I use consists of a desktop computer running Ubuntu 24.04.3 LTS.

  Source code for the application can be cloned using git from 
  https://github.com/tim-littlefair/maneline

  The primary build mechanisms are implemented in CLI scripts in the 'scripts' subdirectory, 
  including:

  + scripts/rebuild_sdk.sh

    A script which downloads and installs versions of Java, Lua (+LuaRocks), Gradle, the 
    Balena CLI tool, the Android SDK, and a Python 3 virtual environment to a project 
    SDK directory for Maneline which sits in the filesystem as a sibling to the current maneline git sandbox.  The script generates
    a script sdk_vars.sh in the top level of the SDK directory which can be processed via 
    the Bourne shell 'source' command to add the downloaded versions to the CLI path.
  
  + scripts/run_web_cli.sh

    A script which performs a clean Gradle build of the Java/gradle components, then 
    runs the Lua web application for the command line on the development workstation.

  + scripts/build_release.sh

    A script which performs a clean Gradle build of the Java/gradle components, then
    deploys the new build using the Balena infrastructure, either directly to a RPi 
    device in Balena's 'local' development mode, or as a new release to a Balena fleet.

As well as the Ubuntu development environment, the rebuild_sdk.sh script can be run
on (Intel x64) macOS.  Periodically I run rebuild_sdk.sh and run_web_cli.sh on a macOS
host to discover whether the build on that environment is still in good working order.

## Supply Chains

The Gradle build brings in a large number of dependency components from Maven Central, 
and the project has a configuration which trigger's GitHub's dependabot infrastructure 
to perform frequent checks for vulnerabilities in packages imported via the Gradle 
supply chain.  I try to resolve as many dependabot alerts as possible within 4 weeks
of initial appearance, although there are some alerts (particularly ones associated
with dependencies brought in by the Android SDK and the Android Gradle Plugin), for which 
I'm not able to find good resolutions.

The build also has separate supply chain dependencies into the Lua/LuaRocks and Python3/PyPi 
infrastructures.  At the present time I don't have any active vulnerability monitoring 
in place for either of these.

## Android Studio IDE Usage 

I am presently using the Android Studio IDE (presently version 2025.2.3 'Otter 3'), as my
habitual editor for the project, but as noted above the primary build is based on CLI, I 
rarely build using the IDE and I don't have a convenient workflow for running the web 
application under a debugger.  I do refer to the IDE's automated flagging of outdated Gradle 
dependencies in the build.gradle files.

Updates to the IDE need to be kept in sync with updates to the Gradle dependencies 
associated with the Android Gradle Plugin, which frequently figures in dependabot 
alters, so I try to update to new versions of Android Studio within a few weeks of 
new releases coming out. 


## Supporting artifacts

The maneline repository also contains a directory called 'docs' which includes the current 
document and various other documents related to the project.  There is also a directory
called 'assets' which contains a variety of supporting information including files I 
used in the creation of the project icon, and some .xmi files containing (out of date)
UML design material related to the project originated in the Linux Umbrello UML editor.


## Future possibilities

  A future version of the maneline codebase may add the following capabilities
  to the project framework:

  * additional provider and message protocol classes implementing support 
    for the transport and protocols required to interoperate with other 
    generations/ranges of Fender devices:
    * Mustang Micro Plus
      
      This will require a transport implementation over Bluetooth Low Energy (BLE), 
      protobuf/JSON message protocol samples have been captured and are known to be 
      comparable to those used by LT- series but not identical.  I own an MMP device
      and making Maneline interoperable with it is a high priority for me. 

    * Mustang GT-/GTX-/LTX- series 

      This also requires BLE transport, may or may not be able to re-use transport and 
      protocol implementations which will be developed for Mustang Micro Plus.  I do not
      presently own an amp in any of these series, nor do I plan to purchase any, so work 
      on any of these will probably only happen
      + (a) after MMP support is in place; and
      + (b) if one or more volunteers with access to the hardware join the project and 
        is/are  able to undertake testing and development.

  * The project may add support for uploading preset amplifier definition JSON files 
    from a maneline application into LT- series, Mustang Micro Plus or other supported devices
    (including possibly adding support for translating preset definitions 
    between the similar-but-not-identical JSON schemas used by LT- series and 
    Mustang Micro Plus, and the .fuse format used by classic Mustang I/II/III/IV/V). 
    
  * other IOT deployment infrastructure (e.g. https://mender.io) might be showcased 
      alongside the present implementation for https://balena.io.

  * The 'android-app' module may be updated to allow commands to the Fender product 
    to be relayed wirelessly over HTTP via the future 'web-proxy-app' rather than 
    requiring a hard-wired direct USB connection to LT- series Fender products.
