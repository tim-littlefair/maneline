#!/bin/bash

# If the local browser is enabled, wait for it
# to come up before starting the Pegasus web
# server and its FHAU command line subprocess.
browser_api_url=http://127.0.0.1:5011/url
maneline_url=http://127.0.0.1:8080/web_ui/index.html
sleep_length=2
while true
do
    if [ ! "$LOCAL_BROWSER" = "1" ]
    then
        echo Local browser disabled
        break
    fi

    browser_api_response=$(curl --silent -X GET $browser_api_url 2>&1)
    echo $browser_api_response | grep --silent -e "file:///" -e "http://" -e "data:"
    if [ ! "$?" = "0" ]
    then
        echo $browser_api_response
        echo Browser API not ready
        sleep $sleep_length
    else
        echo Browser API is ready
    fi
done

start_dir=$(pwd)
echo Starting Pegasus and FHAU CLI in directory $start_dir

export cli_jar=$(ls -1 jar/*.jar | sort -r | head -1)
echo cli jar path=$(pwd)/$cli_jar

# ... actually we don't, but we pass $start_dir to the 
# lua process and it does an lfs.chdir() to that directory
# after all of the local lua files are loaded.
# The reason we need to do this is related to the
# way the app runs in the development environment -
# the CWD at start time is a symlink, we need to
# chdir to the symlink target so that relative
# links in the Lua work as they need to.
# TBD: Would it be better to use LUAPATH?
cd lua

lua ./run.lua "$start_dir" &
sleep 15
curl -X POST --data "url=$maneline_url" $browser_api_url
wait

echo Lua has executed with status $?

# If something goes wrong, we don't want the container to loop
# tightly, so we have a delay between detection and exit
echo "Pegasus/CLI has exited, USB/HID CLI will exit shortly"
sleep 20
echo "USB/HID CLI will exit now"
break

