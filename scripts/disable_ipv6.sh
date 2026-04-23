#!/bin/sh

# This script needs to be run on Tim Littlefair's development
# environment before doing pushes of new app versions to BalenaCloud.

# See the following forum posts for details:
# https://forums.balena.io/t/unable-to-deploy-to-my-fleets/373536/7
# NB The forum post recommends reversing the changes below directly
# after doing the push, as the push is in a separate script I don't
# do this - there appear to be no problems created by leaving ipv6
# disabled, it will re-enable itself on next reboot.

sudo sysctl -w net.ipv6.conf.all.disable_ipv6=1
sudo sysctl -w net.ipv6.conf.default.disable_ipv6=1