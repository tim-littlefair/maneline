#!/bin/sh

if [ "$1" = "--no-browser" ]
then
    no_browser_mode=1
    shift
fi

if [ ! -z "$1" ]
then
    target=$1
elif [ ! -z "$FHAU_DEPLOY_TARGET" ]
then
    target=$FHAU_DEPLOY_TARGET
else
    echo The target device must be specified
    exit 1
fi

echo Deploying to $target

if [ "$no_browser_mode" = "1" ]
then
    balena push $target --detached --source deployment/balena --env tl-fhau-web:LOCAL_BROWSER=0 --service tl-fhau-web
else
    balena push $target --detached --source deployment/balena
fi

