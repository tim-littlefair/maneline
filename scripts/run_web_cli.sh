#!/bin/bash

cli_jar=desktopFHAUcli-0.0.0.jar

check_webhome() {
    if [ ! -d _work/webhome ]
    then
        mkdir -p _work/webhome
    fi
    if [ ! -L _work/webhome/web_ui ]
    then
        ln -s ../../deployment/balena/web_ui _work/webhome
    fi
    if [ ! -L _work/webhome/lua ]
    then
        ln -s ../../deployment/balena/lua _work/webhome
    fi

    if [ ! -L _work/webhome/run.sh ]
    then
        ln -s ../../deployment/balena/run.sh _work/webhome
    fi

    if [ ! -L _work/webhome/$cli_jar ]
    then
        ln -s ../../desktop-app/build/libs/$cli_jar _work/webhome
    fi

    set +e
    cd ./_work/webhome/lua/
    lua check_requirements.lua
    if [ ! "$?" = "0" ]
    then
        echo Run the following commands to install Lua requirements:
        # Base version of Lua and luarocks
        echo sudo apt-get install lua5.1 luarocks
        # apt-packaged lua dependencies
        echo sudo apt-get install lua-socket lua-filesystem lua-sec lua-zlib lua-cjson lua-cqueues
        # We would prefer an apt package lua-pegasus if one existed,
        # but it doesn't so we need to do a build install via luarocks;
        # gcc, liblz-dev and zlib1g-dev are required before luarocks
        # can build pegasus.
        echo sudo apt-get install gcc liblz-dev zlib1g-dev
        echo sudo luarocks install pegasus
        exit 1
    fi
    set -e
}

rundir=$(pwd)/_work/webhome
check_webhome

cd $rundir
tail -F fhau.log &
tail_pid=$!
LOCAL_BROWSER=0 sh ./run.sh $rundir
kill $tail_pid



