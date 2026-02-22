#! /bin/bash
# version_env.bash

# This script is intended to be run using the '.'/'source'
# bash directive its intended effect being the side effect of
# defining the 4 variables below

if [ -z "$BASH_SOURCE" ]
then
  echo "This script needs to be run in the current shell via the 'source' or '.' directive."
  echo "Running it as a subprocess has no effect."
elif [ ! "$#" = "4" ]
then
  echo "Wrong number of arguments $#, require exactly 4 arguments"
  echo Usage: source $BASH_SOURCE major minor patch build
else
  if [ -z "$4" ]
  then
    release_prefix=$(printf "%d.%d.%d" $1 $2 $3)
    export BUILD_ID=9001
  else
    release_prefix=$(printf "%d.%d.%d+beta%04d" $1 $2 $3 $4)
    export BUILD_ID=$4
  fi
  echo "Release prefix will be $release_prefix"
  export RELEASE_VERSION_MAJOR=$1
  export RELEASE_VERSION_MINOR=$2
  export RELEASE_VERSION_PATCH=$3
fi

