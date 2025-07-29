#!/bin/bash

# This script is the glue which starts a web server
# and the FHAU CLI program, and passes messages between
# these two processes.
# It will be brought back to the foreground after
# the browser container has started up and is displaying
# the UI provided by the web server.
echo Starting Pegasus
lua ./run_pegasus.lua $1 &
pegasus_pid=$!
echo Pegasus started with process id $pegasus_pid

# Give the Lua web server time to get its port open before
# telling the browser to display it
browser_api_url=http://localhost:5011/url
fhau_url=http://localhost:9090/web_ui/index.html
sleep_length=2
while true
do
    if [ ! -d /proc/$pegasus_pid ]
    then
        echo "Pegasus has exited"
        break
    fi

    fhau_web_response=$( curl --silent -X GET $fhau_url 2>&1 )
    echo $fhau_web_response | grep --silent "<!DOCTYPE html>"
    if [ ! "$?" = "0" ]
    then
        echo $fhau_web_response
        echo FHAU web server not ready
        sleep $sleep_length
    else
        echo FHAU web server is ready

        if [ ! "$LOCAL_BROWSER" = "1" ]
        then
            break
        fi

        browser_api_response=$(curl --silent -X GET $browser_api_url 2>&1 )
        echo $browser_api_response | grep --silent -e "file:///" -e "http://"
        if [ ! "$?" = "0" ]
        then
            echo $browser_api_response
            echo Browser API not ready
            sleep $sleep_length
        else
            echo Browser API is ready
            break
        fi
    fi
done

if [ ! "$LOCAL_BROWSER" = "1" ]
then
    curl -X POST --data "url=$fhau_url" http://localhost:5011/url
fi

# Bring the Lua script which integrates FHAU CLI with the
# Pegasus web server back to the foreground
while true
do
  if [ ! -d /proc/$pegasus_pid ]
  then
      echo "Pegasus has exited"
      break
  fi
  echo Sleeping
  sleep 60
done

# wait



