#
# spec file for package maneline
#
# maneline as a distribution package; for now its Mustang Micro Plus programs.
# See README.md next to this file.
#

Name:           maneline
Version:        0.3.6
Release:        0
Summary:        Tools for Fender Mustang amplifiers (for now the Mustang Micro Plus)
License:        GPL-2.0-only AND MIT AND OFL-1.1 AND Apache-2.0
Group:          Productivity/Multimedia/Sound/Utilities
URL:            https://github.com/tim-littlefair/maneline
Source0:        %{name}-%{version}.tar.xz
BuildArch:      noarch
BuildRequires:  python3 >= 3.11
BuildRequires:  python3-dbus_fast
BuildRequires:  python3-numpy
# Leap 16.0 and 16.1 ship an older BlueZ; the project that builds this package
# builds a current one for them (a link to Base:System/bluez).
Requires:       bluez >= 5.86
Requires:       python3 >= 3.11
Requires:       python3-dbus_fast
# mmp-level: analysis, finding the amp's audio devices, recording
Recommends:     python3-numpy
Recommends:     pipewire-tools
Recommends:     pulseaudio-utils

%description
The Mustang Micro Plus part of maneline: programs that talk to a Fender
Mustang Micro Plus headphone amplifier over Bluetooth LE, without the
vendor's phone app.

mmp-web serves a small browser page and an HTTP API and keeps the one
connection the amp allows; mmp-backup and mmp-restore save and write back
the amp's presets; mmp-audition tries a preset without storing it; mmp-check
checks the amp after a firmware update; mmp-level measures preset loudness.

Needs BlueZ 5.86 or newer with "Experimental = true" in
/etc/bluetooth/main.conf and the amp paired over LE: see
MUSTANG_MICRO_PLUS_LINUX.md in the documentation.

The rest of maneline (the Mustang LT amplifiers over USB, the Android and
desktop apps) is not part of this package.

%prep
%autosetup -n %{name}-%{version}

%build

%install
deployment/packages/maneline/install.sh %{buildroot} %{_docdir}/%{name}

%check
python3 -m unittest discover -s scripts/tests

%files
%license LICENSE
%doc %{_docdir}/%{name}
%{_bindir}/mmp-*
%{_datadir}/maneline

%changelog
