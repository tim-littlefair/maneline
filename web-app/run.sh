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

if [ true ]
then
  while true
  do
      if [ ! "$LOCAL_BROWSER" = "1" ]
      then
          echo Local browser disabled
          break
      fi
      sleep $browser_wait_sleep_length

      browser_api_response=$(curl --silent -X GET $browser_api_url/url 2>&1)
      echo $browser_api_response | grep --silent -e "file:///" -e "http://" -e "data:"
      if [ ! "$?" = "0" ]
      then
          echo $browser_api_response
          echo Local browser API not ready
      else
          echo Local browser API is ready
          sleep $cli_wait_sleep_length
          nohup sh -c "sleep $cli_start_sleep_length; curl --silent -X POST --data-urlencode url=http://127.0.0.1:8080 $browser_api_url/url" &
          break
      fi
  done
fi

# ... actually we don't, but we pass $start_dir to the 
# lua process and it does an lfs.chdir() to that directory
# after all of the local lua files are loaded.
# The reason we need to do this is related to the
# way the app runs in the development environment -
# the CWD at start time is a symlink, we need to
# chdir to the symlink target so that relative
# links in the Lua work as they need to.
# TBD: Would it be better to use LUAPATH?


#and On the development workstation,
if [ -z $RUN_DIR ]
then
    # When running on RPi, up-to-date copies of
    # sh/lua scripts, jar and web
    # resources are all copied from /home/maneline
    # into the run directory /var/run/maneline.
    cd /var/run/maneline
    for d in lua web_ui lcd_ui jar run.sh
    do
        rm -rf ./$d
        cp -R /home/maneline/$d .
    done
else
    # When running on Linux/macOS development environment
    # the run directory is supplied in the environment
    # variable $RUN_DIR and the copying has been done
    # before the current script starts
    cd $RUN_DIR
fi

start_dir=$(pwd)
echo Starting Pegasus and maneline CLI in directory $start_dir

export cli_jar=$(ls -1 jar/*.jar | sort -r | head -1)
echo cli jar path=$(pwd)/$cli_jar

# On RPi only, start a background process to watch for
# updates to the file preset-details.json and refresh
# the GPIO-attached LCD if there is one
if [ "$(whoami)" = "root" ]
then
  python3 lcd_ui/manage_lcd_ui.py . --fb-both &
  lcd_ui_manager_pid=$!
  echo LCD being managed by process $lcd_ui_manager_pid
else
  python3 -c "import webbrowser ; webbrowser.open('http://127.0.0.1:8080')"
fi

pwd
cd ./lua

lua ./run.lua "$start_dir"

# If something goes wrong, we don't want the container to loop
# tightly, so we have a delay between detection and exit
cli_run_status=$?

echo "Pegasus/CLI app has exited with status $cli_run_status"

if [ ! -z $lcd_ui_manager_pid ]
then
  kill $lcd_ui_manager_pid
fi

# Allow the background sleep/curl subprocess to return its status
wait

echo "The Pegasus/CLI container will exit in $exit_wait_sleep_length seconds"
sleep $exit_wait_sleep_length

echo "Pegasus/CLI container will exit now"
exit $cli_run_status