#!/bin/bash

exit_wait_sleep_length=2

# We pass $start_dir to the lua process and it
# does an lfs.chdir() to that directory
# after all of the local lua files are loaded.
# The reason we need to do this is related to the
# way the app runs in the development environment -
# the CWD at start time is a symlink, we need to
# chdir to the symlink target so that relative
# links in the Lua work as they need to.

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
  rm amp-details.json
  rm preset-details.json
  python3 lcd_ui/manage_lcd_ui.py . --fb-both &
  lcd_ui_manager_pid=$!
  echo LCD being managed by process $lcd_ui_manager_pid
fi

cd ./lua

while true
do
    # Under Lua 5.1 the :close() method on a file descriptor opened
    # with io:popen() returns a boolean (always true?) and there is
    # no way of accessing the true exit status of the subprocess.
    # I could fix this by upgrading to Lua 5.2 or later, but
    # for now I prefer to stick with 5.1 and work around this
    # by dropping an indicator file containing the exit status value.
    exit_status_file=$start_dir/cli_exit_status
    if [ -f $exit_status_file ]
    then
        rm $exit_status_file
    fi

    # The lua process started on the next line will be the
    # parent process for the Java CLI session
    lua ./run.lua "$start_dir"
    cli_exit_status=$?

    if [ -f $exit_status_file ]
    then
        cli_exit_status=$(cat $exit_status_file)
        rm $exit_status_file
    fi
    echo "Pegasus/CLI app has exited with status $cli_exit_status"

    if [ "$cli_exit_status" = "101" ]
    then
        echo Restart of CLI process due to user request
        rm amp-details.json
        rm preset-details.json
    else
        break
    fi
done

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