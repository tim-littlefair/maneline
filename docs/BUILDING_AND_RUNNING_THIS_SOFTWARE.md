# Building and running this software

## Prerequisites

This software has been developed on Ubuntu 24.04.3.  The main build process has been 
implemented in Bourne shell scripts (some if which are dependent on the bash variant 
of the Bourne shell family).

The build processed described here are expected to work on any reasonably current 
mainstream x86-64 architecture variant of Linux, and also on an Intel-powered Apple 
computer running a version of macOS ranging from macOS 10.12 Sierra to macOS Sequoia 14.6.  

The first step in establishing a build environment is to clone the project repository
at GitHub, for which a working installation of Git is required.  

For any installation of Linux, use the native package manager (apt-get, apk, yum, dnf etc)
to install a version of Git.  If there is no package manager and/or the default repository
does not have a version of Git available, the distribution does not qualify as "reasonably
current mainstream" and I would not recommend using it as a development platform for this 
project.  All testing done my me to date is on Ubuntu and other Debian-based distributions 
which use apt-get as the package manager command and .deb as the packaging format.

For any installation of macOS, if there is already an installation of Git in place please
use it.

If there is no current installation of Git, consider running the following command:

```
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

This will attempt to install an open source package manager for macOS named 
[Homebrew](https://brew.sh/).
The install process uses Git, and so will install Apple's command line build tools including 
a version of Git for the version of macOS you are running a side effect.  If you are running
a macOS version older than 10.15 Catalina, you will get a message that Homebrew does is not 
supported and may not work, although the Homebrew installation may be non-functional, the 
Git and command line tools installed will still be viable. 

If you prefer not to have (working or non-working) Homebrew installed, you will probably be 
able to find downloads of the command line tools including Git on Apple-owned websites, 
but I choose not to document locations for these here so that I don't need to keep them up 
to date.


## Establishing a parent directory for the project and cloning the source code

The maneline project will rely on a downloaded SDK which will be installed into a 
sibling directory alongside the maneline sandbox.  When the SDK download process is 
complete the maneline git repository will sit alongside two sibling directories, 
one of which will contain the SDK (which you will give a name to, see the next
section), and the other will be called 'cache' and will contain cached copies of
files downloaded for the SDK to save time if you need to rebuild the SDK and 
any of the previous version's files are still current.

I recommend creating a new parent directory called ManelineProject somewhere on your 
filesystem, doing the clone of the GitHub source code from this directory so that 
the sibling directories containing the SDK and its download cache are not in danger 
of clashing with directories belonging to other projects.  The instructions below 
assume that you follow this convention, but it is optional.

## Downloading, Building and Installing the SDK

In a shell in the ManelineProject directory, issue the command:

```
git clone https://github.com/tim-littlefair/maneline
```

The repository will be checked out into a subdirectory called maneline.

Issue the following commands:

```
cd maneline
time scripts/rebuild_sdk.sh sdk.260224a
```

---
**NOTE**

260224a in the command above is a placeholder for the current date and a single-letter suffix, 
it will control the directory name the SDK is created in, allowing multiple versions of the 
SDK to exist side by side when the SDK needs to be updated due to a change in the 
scripts/rebuild_sdk.sh file in the repository.

---

The SDK (re)build process will take approximately 30-40 minutes.

If the rebuild succeeds the last few lines of the output should look something like this:

```
 
TBD

```

## Clean Gradle Build

```
./gradlew clean build
```

## Running Maneline on the development environment

```
scripts/run_web_cli.sh
```

Browse to http://localhost:8080

Should look like this:
[![image of Maneline web UI](/assets/MustangIv2-Screenshot.png)]






