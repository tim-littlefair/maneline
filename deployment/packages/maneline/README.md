# maneline as a distribution package

The package `maneline` contains, for now, maneline's Mustang Micro Plus programs
(`scripts/mmp_*.py`), the browser page `mmp_web.py` serves and the Mustang Micro Plus
documents. It needs Python 3.11+, `dbus-fast` and BlueZ 5.86+, all taken from the
distribution: no venv, no `bleak` on Linux.

| File | For |
|---|---|
| `install.sh` | puts the files into a destination directory; all three package formats call it |
| `maneline.spec` | RPM (openSUSE Tumbleweed, Leap 16.0 and 16.1) |
| `PKGBUILD` | Arch |
| `debian/` | Debian 13 and testing, Raspberry Pi OS 13, Ubuntu 26.04 |

Installed layout: `/usr/share/maneline/scripts`, `/usr/share/maneline/web_ui`,
commands `mmp-web`, `mmp-ble`, `mmp-backup`, `mmp-restore`, `mmp-audition`, `mmp-check`,
`mmp-level` and `mmp-bluez-setup` (`scripts/bluez_setup.sh`, the one-time BlueZ setup) in
`/usr/bin`, the documents in the package's doc directory. Backups go to
`~/.local/share/maneline/backups` unless `--out` or `MMP_WORK_DIR` says otherwise.

## Installing

The package is built from this repository's `main` on the openSUSE Build Service, for now in
the project [home:joba1:chordsplay](https://build.opensuse.org/project/show/home:joba1:chordsplay)
(maintained by joba-1; "Building it on OBS" below shows how to set up a project of your own).
The commands below start by setting two variables: `OBS_REPO`, where the project's
repositories are published, and `DIST`, the repository for your system from this table:

| System | `DIST` |
|---|---|
| openSUSE Tumbleweed | `openSUSE_Tumbleweed` |
| openSUSE Leap 16.0, 16.1 | `16.0`, `16.1` |
| Arch, CachyOS, Omarchy | `Arch` |
| Debian 13, Raspberry Pi OS 13 64-bit (default on Pi 4 and 5, possible on Pi 3 and Zero 2) | `Debian_13` |
| Raspberry Pi OS 13 32-bit | `Raspbian_13` |
| Debian testing | `Debian_Testing` |
| Ubuntu 26.04 (64-bit, amd64 and arm64) | `Ubuntu_26.04` |

openSUSE:

```bash
OBS_REPO=https://download.opensuse.org/repositories/home:/joba1:/chordsplay
DIST=openSUSE_Tumbleweed     # or 16.0, 16.1
sudo zypper addrepo -f $OBS_REPO/$DIST/home:joba1:chordsplay.repo
sudo zypper install maneline
```

On Leap, zypper asks to take `bluez` from this project too (a vendor change): say yes, see
"BlueZ" below.

Arch: add to `/etc/pacman.conf`

```ini
[home_joba1_chordsplay_Arch]
Server = https://download.opensuse.org/repositories/home:/joba1:/chordsplay/Arch/$arch
```

then trust its key and install (with `-Syu`: Arch does not support installing onto a partly
updated system):

```bash
OBS_REPO=https://download.opensuse.org/repositories/home:/joba1:/chordsplay
KEY_URL=$OBS_REPO/Arch/x86_64/home_joba1_chordsplay_Arch.key
curl -fsSL $KEY_URL | sudo pacman-key --add -
sudo pacman-key --lsign-key "$(curl -fsSL $KEY_URL | gpg --show-keys --with-colons | awk -F: '/^fpr/ {print $10; exit}')"
sudo pacman -Syu maneline
```

Debian, Ubuntu and Raspberry Pi OS:

```bash
OBS_REPO=https://download.opensuse.org/repositories/home:/joba1:/chordsplay
DIST=Debian_13               # or Raspbian_13, Debian_Testing, Ubuntu_26.04
curl -fsSL $OBS_REPO/$DIST/Release.key | gpg --dearmor | sudo tee /etc/apt/keyrings/maneline.gpg > /dev/null
echo "deb [signed-by=/etc/apt/keyrings/maneline.gpg] $OBS_REPO/$DIST/ /" | sudo tee /etc/apt/sources.list.d/maneline.list
sudo apt update && sudo apt install maneline
```

Debian 13, Raspberry Pi OS 13 and Ubuntu 26.04 need a newer BlueZ than they ship: see
"BlueZ" below.

Then, once, the BlueZ setup for the amp: `Experimental = true` in the `[General]` section of
`/etc/bluetooth/main.conf` (openSUSE has no such file: create it with these two lines),
`sudo systemctl restart bluetooth`, and `sudo mmp-bluez-setup AA:BB:CC:DD:EE:FF` (the amp's
address, `bluetoothctl devices`) with the amp switched on;
[MUSTANG_MICRO_PLUS_LINUX.md](../../../docs/MUSTANG_MICRO_PLUS_LINUX.md), "One-time BlueZ
setup", has the details. Updates come with the system's package updates.

By hand, without a package, from the top of the source tree:
`sudo deployment/packages/maneline/install.sh /` installs the same files (no dependencies,
no uninstall).

## Building it on OBS

The [openSUSE Build Service](https://build.opensuse.org) builds packages for many
distributions from one set of files and publishes them as repositories. It needs a free
account there; a new account gets a home project `home:<account name>`, in which you can
create subprojects. The commands use `osc`, OBS's command line client (packaged in most
distributions); everything also works in the web interface. Set these once in the shell:

```bash
OBS_USER=your-obs-account
PROJECT=home:$OBS_USER:maneline      # the project to create
```

1. **The project and its repositories.** `osc meta prj -e $PROJECT` opens the project's
   settings in an editor; give it the systems to build for (with your account name instead
   of `USER`):

   ```xml
   <project name="home:USER:maneline">
     <title>maneline</title>
     <description>maneline: tools for Fender Mustang amplifiers</description>
     <person userid="USER" role="maintainer"/>
     <repository name="openSUSE_Tumbleweed">
       <path project="openSUSE:Factory" repository="snapshot"/>
       <arch>x86_64</arch>
     </repository>
     <repository name="16.0">
       <path project="openSUSE:Leap:16.0" repository="standard"/>
       <arch>x86_64</arch>
     </repository>
     <repository name="Arch">
       <path project="Arch:Extra" repository="standard"/>
       <arch>x86_64</arch>
     </repository>
     <repository name="Debian_13">
       <path project="Debian:13" repository="standard"/>
       <arch>x86_64</arch>
       <arch>aarch64</arch>
       <arch>armv7l</arch>
     </repository>
     <repository name="Raspbian_13">
       <path project="Raspbian:13" repository="standard"/>
       <arch>armv7l</arch>
     </repository>
     <repository name="Ubuntu_26.04">
       <path project="Ubuntu:26.04" repository="universe"/>
       <arch>x86_64</arch>
       <arch>aarch64</arch>
     </repository>
   </project>
   ```

   (`16.1` and `Debian_Testing` the same way, with `openSUSE:Leap:16.1` and
   `Debian:Testing`.)

2. **The project configuration**, for Arch only: `python-numpy` (needed by the tests) has two
   providers for `lapack` and `cblas` there, and without a choice the build is
   `unresolvable`. `osc meta prjconf -e $PROJECT`, then:

   ```
   %if "%_repository" == "Arch"
   Prefer: blas-openblas
   %endif
   ```

3. **The package.** `osc meta pkg -e $PROJECT maneline` (a title and a description are
   enough), then check it out:

   ```bash
   osc checkout $PROJECT maneline
   cd $PROJECT/maneline
   ```

   and put a file `_service` there that fetches the sources from git:

   ```xml
   <services>
     <service name="tar_scm">
       <param name="scm">git</param>
       <param name="url">https://github.com/tim-littlefair/maneline.git</param>
       <param name="revision">main</param>
       <param name="filename">maneline</param>
       <param name="versionformat">0.3.6+git%cd.%h</param>
       <param name="include">scripts</param>
       <param name="include">web-app/web_ui</param>
       <param name="include">docs</param>
       <param name="include">deployment/packages</param>
       <param name="include">LICENSE</param>
     </service>
     <service name="recompress">
       <param name="file">*tar</param>
       <param name="compression">xz</param>
     </service>
     <service name="set_version"/>
   </services>
   ```

   `filename` names the tarball after the package (the spec expects `maneline-<version>`);
   `versionformat` puts the date and commit after the latest release number (raise it with
   each release); `set_version` writes that version into the build files.

4. **The build files**, copied from this directory of a maneline checkout: the spec and
   the PKGBUILD as they are, the files of `debian/` flat with a prefix (`debian.control`,
   `debian.rules`, …):

   ```bash
   SRC=~/git/maneline/deployment/packages/maneline     # your checkout
   cp $SRC/maneline.spec $SRC/PKGBUILD .
   for f in $SRC/debian/*; do cp $f debian.$(basename $f); done
   ```

   and a file `maneline.dsc` for the Debian builds (`Build-Depends` as in `debian/control`):

   ```
   Format: 1.0
   Source: maneline
   Binary: maneline
   Architecture: all
   Version: 0.3.6-1
   Maintainer: Your Name <you@example.org>
   Homepage: https://github.com/tim-littlefair/maneline
   Standards-Version: 4.7.0
   Build-Depends: debhelper-compat (= 13), python3 (>= 3.11), python3-dbus-fast, python3-numpy
   ```

   Then commit; OBS runs the service, builds every repository and runs the tests in each
   build:

   ```bash
   osc add *
   osc commit -m "maneline from tim-littlefair/maneline main"
   osc results                          # the state of every build
   osc buildlog Debian_13 x86_64        # the log of one
   ```

5. **New commits.** The service fetches `main` only when it runs, so after a change (or
   by cron): `osc service remoterun $PROJECT maneline`. When the build files here change,
   copy them again and commit. OBS can also run the service from a GitHub webhook with a
   token (`osc token --create`, see the OBS user guide).

6. **BlueZ for Leap** (see "BlueZ" below): link openSUSE's devel package into the project
   and build it for Leap only:

   ```bash
   osc linkpac Base:System bluez $PROJECT
   osc meta pkg -e $PROJECT bluez
   ```

   and in the editor:
   `<build><disable/><enable repository="16.0"/><enable repository="16.1"/></build>`.

## A package of a fork

A fork can build the same files under a name of its own, so that its package replaces
`maneline` on a computer where it is installed. Change the name in the spec file's name and
`Name:`, `pkgname` in the PKGBUILD, `Source:` and `Package:` in `debian/control`, the first
line of `debian/changelog` and `filename` in `_service`, and declare `maneline` as provided
and conflicting (spec: `Provides: maneline = %{version}` and `Conflicts: maneline`;
PKGBUILD: `provides=('maneline')` and `conflicts=('maneline')`; `debian/control`:
`Provides:`, `Conflicts:` and `Replaces: maneline`). The installed paths stay the same
(`/usr/share/maneline`, set in `install.sh` only), so programs that depend on `maneline`
work with either package, and installing one removes the other.

## BlueZ

openSUSE Leap 16.0 and 16.1 ship BlueZ 5.79 and 5.82, which lack the LE bearer interface
(`org.bluez.Bearer.LE1`, BlueZ 5.86). The OBS project therefore also builds `bluez` for
Leap, as a link to openSUSE's devel package `Base:System/bluez`, and the RPM requires
`bluez >= 5.86`. Debian 13 and Raspberry Pi OS 13 ship 5.82, Ubuntu 26.04 ships 5.85: the
Debian package has no versioned requirement, so it installs there, but the programs only
connect with a newer BlueZ. One is built, opt-in, in the OBS project
`home:joba1:chordsplay:bluez` (Debian testing's 5.88 rebuilt for these three); adding that
repository replaces the system's Bluetooth stack, so add it only on the computer the amp is
paired with:

```bash
BLUEZ_REPO=https://download.opensuse.org/repositories/home:/joba1:/chordsplay:/bluez
DIST=Debian_13               # or Raspbian_13, Ubuntu_26.04
curl -fsSL $BLUEZ_REPO/$DIST/Release.key | gpg --dearmor | sudo tee /etc/apt/keyrings/maneline-bluez.gpg > /dev/null
echo "deb [signed-by=/etc/apt/keyrings/maneline-bluez.gpg] $BLUEZ_REPO/$DIST/ /" | sudo tee /etc/apt/sources.list.d/maneline-bluez.list
sudo apt update && sudo apt upgrade && sudo reboot
```
