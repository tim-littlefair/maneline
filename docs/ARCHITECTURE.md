# Architecture of the maneline Codebase

Notes below are believed correct as at August 2025.

## Component Model

As at the document date noted above, a modular structure for the project 
seems to be emerging, which is reflected in selected subdirectories of 
the GitHub repository layout, with the following being the major modules:

* lib

  This module is a Java library, written to be portable between integrated
  applications built in Android Java and some or all of the non-Oracle 
  alternate desktop Java Development Kit implementations.

  The module contains Java framework code based on the the abstract concept
  of an 'Amp Provider', which is an object which can perform operations on Fender
  LT-series devices (and can hopefully be extended to operate on Fender products
  from other ranges at some time in the future).  
  
  The module includes classes which encapsulate the on-the-wire protocol and 
  interaction sequence required for an Amp Provider to interoperate with the 
  supported LT- series devices, but does not include an implementation 
  of the transport layer required for interoperation with a device over its
  physical USB physical interface.  The lib module is consumed by the android-app and 
  desktop-app modules, each of which provides its own implementation of the Amp Provider
  concept class based on different third-party libraries supporting the USB-HID 
  framework for the different target platforms (Android and Desktop Linux).
  
  The provider support code in the lib module includes an implementation of the 
  message protocol used by the Fender Mustang LT40S device capable of:

  * exporting JSON files from the LT40S device which represent the configuration
    of preset amplifier definitions; and

  * sending the LT40S device a command to switch to a specified preset.

  It is expected that this protocol implementation would be equally interoperable 
  with any of the following other post-2016 USB-controlled FMIC devices:

  * Fender Mustang LT25;
  
  * Fender Rumble LT25; and

  * Fender Mustang LT50.

  No testing has been done to date to determine whether these devices are interoperable
  with the current codebase.

* desktop-app

  This module is a command line interface console program written in Java, built on top 
  of the 'lib' module, which includes a concrete implementation of the LT-series USB Amp 
  Provider capable of running on a desktop or laptop computer running Linux (with 
  appropriate dependency packages installed).  

  The CLI program is provided primarily as a platform for development, testing, 
  and capability exporation. 

  This module is presently tested on Ubuntu 24.04.3 LTS (AMD64 architecture).

  The primary executable in this module has also been built, deployed and tested 
  on various Raspberry Pi hardware variants.  See the notes below on module 
  'deployment/balena' for more details of how desktop-app contributes to the 
  Balena/RPi deployment target.

* android-app

  This module is an Android mobile device application, also written in Java, built on 
  top of lib, which includes a concreate implementation of the LT-series USB Amp Provider 
  capable of running on an Android mobile phone.

  The provider implemented in this application presently requires the Android mobile device 
  to be directly connected to the LT- series amplifier via a USB cable.

* deployment/balena
  
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

## Build environment, third party dependencies and supply chains
  
  The development environment I use consists of a desktop computer running Ubuntu 24.04.3 LTS,
  using the Android Studio IDE (presently version 2025.1.1 'Narwhal', I try to update within 
  a few weeks of new releases coming out).
  
  The repository contains a script scripts/rebuild_sdk.sh which attempts to gather 
  and install all packages required for a command-line-driven continuous integration
  build, including a JDK, Android SDK packages (but not the Android Studio IDE).

  The 'lib', 'desktop-app', and 'android-app' modules are all built using Gradle, and 
  rely on a large number of Java packages delivered by the Maven Central and Google 
  Gradle/Maven repositories.

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

  The 'deployment/balena' module contains Lua scripts which depend (at runtime, on the 
  Balena-deployed RPi target host) on a number of Ubuntu .deb packages with the prefix 
  lua- which contain Lua libraries built for the Ubuntu/Debian environment.  This module 
  also depends on a Lua web server called 'Pegasus' for which no apt package exists
  and which needs to be installed using the 'Luarocks' supply chain utility. 
  
  This module also depends on the 
  ['Balena CLI'](https://docs.balena.io/reference/balena-cli/latest/) 
  package.  
  At the time of writing, scripts/rebuild_sdk.sh does not download or install Lua library 
  dependences or the Balena CLI.
  
## Supporting artifacts

The maneline repository also contains a directory called 'docs' which includes the current 
document and various other documents related to the project.  There is also a directory
called 'assets' which contains a variety of supporting information including:
*  the web resources used by the Balena-hosted Pegasus web server,

## Future possibilities

  A future versions of the maneline codebase may add the following capabilities
  to the project framework:

  * adding additional provider and message protocol classes implementing support 
    for the transport and protocols required to interoperate with other 
    generations/ranges of Fender devices:

    + Mustang Micro Plus 
      
      This will require a transport implementation over Bluetooth Low Energy (BLE), 
      protobuf/JSON message protocol samples have been captured and are known to be 
      comparable to those used by LT- series but not identical.

    + Mustang GT-/GTX- series 

      This also requires BLE transport, may or may not be able to re-use transport and 
      protocol implementations which will be developed for Mustang Micro Plus.

    + pre-2016 Mustang I, II, III, IV and V models 
    
      These were and are supported by the Debian mustang-plug apt package and its more 
      recently updated fork published at https://github.com/offa/plug. 

      A maneline implementation for these models can possibly reuse most of the USB transport
      logic which presently supports LT- series devices, the preset definition payloads 
      for these devices are binary-based rather than the JSON variants used by LT- series
      and Mustang Micro Plus, but should be possible to infer from the original and 
      updated mustang-plug codebases.

    Note that present sole project developer Tim Littlefair owns Mustang LT40S and 
    Mustang Micro Plus devices, but has no plan to purchase any devices from the newer 
    GT-/GTX- series or the pre-2016 series with roman numeral model numbers.  Progress 
    on any of these will depend on recruitment of project contributors with access to 
    the target hardware.

  * The project may add support for uploading preset amplifier definition JSON files 
    from a maneline application into LT- series, Mustang Micro Plus or other supported devices
    (including possibly adding support for translating preset definitions 
    between the similar-but-not-identical JSON schemas used by LT- series and 
    Mustang Micro Plus). 
    
  * The present deployment/balena module may undergo some reorganization, possibly 
    along the following lines:

    + Lua code and HTML/web resources may move out into a new top-level module which 
      might be called 'web-proxy-app';

    + there might be more than one flavour of balena deployment, for example:
    
      - a kiosk-like deployment flavour with integrated browser running on a display attached
        to the RPi balena host
        
      - a headless deployment flavour running the web proxy app only with the expectation 
        that the user will use a browser on a separate device);

      - either or both of the flavours above might exist in a variant in which the 
        Balena RPi host doubles as a wifi access point, as well as a variant in which 
        the RPi host attaches to a pre-existing wifi network.

    + other IOT deployment infrastructure (e.g. https://mender.io) might be showcased 
      alongside the present implementation for https://balena.io.

  * The 'android-app' module may be updated to allow commands to the Fender product 
    to be relayed wirelessly over HTTP via the future 'web-proxy-app' rather than 
    requiring a hard-wired direct USB connection to LT- series Fender products.


