#!/bin/sh

restart_seconds=20

ls -lR .

lua ./run_pegasus.lua &

# Give the Lua web server time to get its port open before
# telling the browser to display it
browser_api_url=http://localhost:5011/url
fhau_url=http://localhost:9090/start-fhau.html
sleep_length=5
while true
do
    fhau_web_response=$( curl --silent -X GET $fhau_url 2>&1 )
    echo $fhau_web_response | grep "<!DOCTYPE html>"
    if [ ! "$?" = "0" ]
    then
        echo $fhau_web_response
        echo FHAU web server not ready
        sleep $sleep_length
    else
        echo FHAU web server is ready
        browser_api_response=$(curl --silent -X GET $browser_api_url 2>&1 )
        echo $browser_api_response | grep "file:///"
        if [ ! "$?" = "0" ]
        then
            echo $browser_api_response
            echo Browser API not ready
            sleep $sleep_length
        else
            break
        fi
    fi
done

curl -X POST --data "url=$fhau_url" http://localhost:5011/url

while true
do
    if [ ! -c /dev/hidraw0 ]
    then
        echo No HID USB device detected - is Mustang LT turned on?
        echo Container will restart in $restart_seconds seconds
        sleep $restart_seconds
        echo Restarting 
        exit
    else
        echo HID device OK
        sleep 60
    fi
done

