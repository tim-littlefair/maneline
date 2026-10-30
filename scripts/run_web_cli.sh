#!/bin/bash

if [ "$1" = "--micro-plus" ]; then
    shift
    micro_python=${MANELINE_PYTHON:-$(pwd)/_work/mmp-venv/bin/python}
    if [ ! -x "$micro_python" ]; then
        echo 'Set MANELINE_PYTHON to a Python 3.11+ interpreter with Bleak installed.' >&2
        echo 'See docs/MUSTANG_MICRO_PLUS_MACOS.md for setup.' >&2
        exit 1
    fi
    exec "$micro_python" "$(pwd)/scripts/mmp_web.py" "$@"
fi

rundir=$(pwd)/_work/webhome

if [ "$1" = "--clean" ]
then
    rm -rf $rundir/* $rundir
    shift
fi

check_webhome() {

    if [ ! -d $rundir ]
    then
        mkdir -p $rundir
    fi

    if [ ! -L $rundir/web_ui ]
    then
        ln -s ../../web-app/web_ui $rundir
    fi

    if [ ! -L $rundir/lcd_ui ]
    then
        ln -s ../../web-app/lcd_ui $rundir
    fi

    if [ ! -L $rundir/lua ]
    then
        ln -s ../../web-app/lua $rundir
    fi

    if [ ! -L $rundir/run.sh ]
    then
        ln -s ../../web-app/run.sh $rundir
    fi

    if [ ! -L $rundir/jar ]
    then
        ln -s ../../desktop-app/build/libs $rundir/jar
    fi
    ls -l $rundir

    if false
    then
        set +e
        cd ./$rundir/lua/
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
    fi
}

check_webhome
cd $rundir
RUN_DIR=$rundir LOCAL_BROWSER=0 sh ./run.sh $rundir



