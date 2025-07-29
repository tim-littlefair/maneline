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
        echo sudo apt-get install lua5.1 luarocks lua-socket lua-filesystem lua-sec lua-zlib lua-cjson
        echo sudo luarocks install pegasus
        exit 1
    fi
    cd ../../..
    set -e
}


check_webhome


rundir=$(pwd)/_work/webhome
cd _work/webhome/lua
sh ../run.sh $rundir

