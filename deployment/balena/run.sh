#!/bin/bash

# If the local browser is enabled, wait for it
# to come up before starting the Pegasus web
# server and its FHAU command line subprocess.
browser_api_url=http://127.0.0.1:5011
maneline_url=http://127.0.0.1:8080
browser_wait_sleep_length=10
cli_wait_sleep_length=10
cli_start_sleep_length=10
exit_wait_sleep_length=10

if [ false ]
then
  while true
  do
      if [ ! "$LOCAL_BROWSER" = "1" ]
      then
          echo Local browser disabled
          break
      fi

      browser_api_response=$(curl --silent -X GET $browser_api_url/url 2>&1)
      echo $browser_api_response | grep --silent -e "file:///" -e "http://" -e "data:"
      if [ ! "$?" = "0" ]
      then
          echo $browser_api_response
          echo Local browser API not ready
          sleep $browser_wait_sleep_length
      else
          echo Local browser API is ready
          sleep $cli_wait_sleep_length
          nohup sh -c "sleep $cli_start_sleep_length; curl --silent -X POST -data http://127.0.0.1:8080 $browser_api_url/url" &
          break
      fi
  done
fi

start_dir=$(pwd)
echo Starting Pegasus and maneline CLI in directory $start_dir

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

lua ./run.lua "$start_dir"

# If something goes wrong, we don't want the container to loop
# tightly, so we have a delay between detection and exit
cli_run_status=$?

echo "Pegasus/CLI app has exited with status $cli_run_status"

# Allow the background sleep/curl subprocess to return its status
wait

echo "The Pegasus/CLI container will exit in $exit_wait_sleep_length seconds"
sleep $exit_wait_sleep_length


echo "Pegasus/CLI container will exit now"
exit $cli_run_status