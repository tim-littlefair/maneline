#!/usr/bin/lua

-- balena_actions.lua
-- The purpose of this package is to contain all logic related to
-- actions which need to be dedicated to the balena host on balena
-- devices

-- Part of the maneline project released under GPL 2.0
-- Copyright: Tim Littlefair 2025
-- For copying rules see
-- https://github.com/tim-littlefair/maneline/blob/main/LICENSE

local cjson = require('cjson.safe')
local fhau_cli = require('fhau_cli')

local BalenaActions = {}

function _post_balena_request(action_name)
    command_prefix='curl -X POST --header "Content-Type:application/json"'
    command_data=cjson.encode({
        appId=os.getenv("BALENA_APP_ID"),
        force=true
    })
    action_url='$BALENA_SUPERVISOR_ADDRESS/v1/'..action_name..'?apikey=$BALENA_SUPERVISOR_API_KEY'
    command=command_prefix.." -d '"..command_data.."' "..action_url
    os.execute(command)
    print() -- add newline after curl output
end

function BalenaActions:do_action(action_name)
    if os.getenv("BALENA_SUPERVISOR_ADDRESS") == nil
    then
        -- running on non-Balena Linux/macOS host
        -- On this context, the only action supported is
        -- to restart the CLI executable
        if action_name=="restart"
        then
            fhau_cli:relay_stdin_line("restart\n")
            return "Balena service restart simulated"
        else
            return "Requested action '"..action_name.."' is not available on non-Balena hosts"
        end
    elseif (
        action_name=="shutdown" or
        action_name=="reboot" or
        action_name=="restart"
    )
    then
        _post_balena_request(action_name)
        return "Balena action "..action_name.." has been requested"
    else
        return "Unexpected Balena action "..action_name.." has not been requested"
    end
end

return BalenaActions


