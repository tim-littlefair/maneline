# maneline as a distribution package

The package `maneline` contains, for now, maneline's Mustang Micro Plus programs
(`scripts/mmp_*.py`), the browser page `mmp_web.py` serves and the Mustang Micro Plus
documents. It needs Python 3.11+, `dbus-fast` and BlueZ 5.86+, all taken from the
distribution: no venv, no `bleak` on Linux.

| File | For |
|---|---|
| `install.sh` | puts the files into a destination directory; all three package formats call it |
| `maneline.spec` | RPM (openSUSE Tumbleweed, Leap 16.0) |
| `PKGBUILD` | Arch |
| `debian/` | Debian 13, Raspberry Pi OS, Ubuntu |

Installed layout: `/usr/share/maneline/scripts`, `/usr/share/maneline/web_ui`,
commands `mmp-web`, `mmp-ble`, `mmp-backup`, `mmp-restore`, `mmp-audition`, `mmp-check`,
`mmp-level` and `mmp-bluez-setup` (`scripts/bluez_setup.sh`, the one-time BlueZ setup) in
`/usr/bin`, the documents in the package's doc directory. Backups go to
`~/.local/share/maneline/backups` unless `--out` or `MMP_WORK_DIR` says otherwise.

## How it is built

On the openSUSE Build Service (for example project `home:joba1:chordsplay`, package
`maneline`): a `_service` file fetches a branch of the git repository as
`maneline-<version>.tar.xz` (`tar_scm` with `filename` = `maneline` and
`versionformat` = `0.3.6+git%cd.%h`, then `recompress` and `set_version`); the spec, the
PKGBUILD and the Debian files (as `debian.control`, `debian.rules`, … plus a `.dsc`) are
copies of the files here. Every build runs the unit tests
(`python3 -m unittest discover -s scripts/tests`).

By hand, from the top of the source tree: `deployment/packages/maneline/install.sh /`
as root installs the same files without a package.

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
repository replaces the system's Bluetooth stack.
