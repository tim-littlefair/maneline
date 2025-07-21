#!/bin/sh

if [ ! -z "$1" ]
then
    target=$1
else
    target=$FHAU_DEPLOY_TARGET
fi

echo Deploying to $target

balena push $target --detached --source deployment/balena --env tl-fhau-web:LOCAL_BROWSER=0 --service tl-fhau-web