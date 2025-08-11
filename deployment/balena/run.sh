#!/bin/bash

start_dir=$(pwd)
echo Starting $0 in directory $start_dir

cd lua

lua ./run.lua $start_dir
pegasus_pid=$!
echo Pegasus started with process id $pegasus_pid
exit

# TBD: Work out how to get the browser started
# and send the message to it to display the 
# FHAU web UI without closing standard input to 
# the FHAU/web wrapper

# Maybe the browser has to start first
# Maybe the wrapper needs to read from a pipe

sleep 10

# Give the Lua web server time to get its port open before
# telling the browser to display it
browser_api_url=http://127.0.0.1:5011/url
fhau_url=http://127.0.0.1:9090/web_ui/index.html
sleep_length=2
while false
do
    if [ ! -d /proc/$pegasus_pid ]
    then
        echo "Pegasus has exited"
        break
    fi

    fhau_web_response=$( curl --silent -X GET $fhau_url 2>&1 )
    echo $fhau_web_response | grep --silent "html"
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

        browser_api_response=$(curl --silent -X GET $browser_api_url 2>&1)
        echo $browser_api_response | grep --silent -e "file:///" -e "http://"
        if [ ! "$?" = "0" ]
        then
            echo $browser_api_response
            echo Browser API not ready
            sleep $sleep_length
        else
            echo Browser API is ready
            curl -X POST --data "url=$fhau_url" $browser_api_url
            break
        fi
    fi
done

# Bring the Lua script which integrates FHAU CLI with the
# Pegasus web server back to the foreground
while true
do
  if [ ! -d /proc/$pegasus_pid ]
  then
      # If something goes wrong, we don't want the container to loop
      # tightly, so we have a delay between detection and exit
      echo "Pegasus has exited, Balena container will exit shortly"
      sleep 30
      echo "Balena container will exit now"
      break
  fi
  # echo Sleeping
  sleep 10
done

# wait

