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

# On RPi0w2, deployment is smoother if existing containers are stopped
browser_container_name=balenalabs-browser_1_1_10ca12e1ea5e
fhau_container_name=tl-fhau-web_0_0_10ca12e1ea5e
echo Stopping existing containers
echo "balena container stop $browser_container_name" | balena device ssh $target
echo "balena container stop $fhau_container_name" | balena device ssh $target

echo Deploying to $target
if [ "$no_browser_mode" = "1" ]
then
    balena push $target --detached --source deployment/balena --env tl-fhau-web:LOCAL_BROWSER=0 --service tl-fhau-web
else
    balena push $target --detached --source deployment/balena
fi

# If either of the containers were unchanged, the push will not have restarted them
echo Restarting unchanged containers
echo "balena container start $fhau_container_name" | balena device ssh $target
echo "balena container start $browser_container_name" | balena device ssh $target
