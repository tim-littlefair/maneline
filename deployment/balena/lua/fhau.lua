-- /usr/bin/lua
-- fhau.lua
-- The purpose of this script is to start the fhau command line process
-- and enable sibling script run_pegasys.lua to send commands to the
-- running subprocess.

-- Part of the feral_horse_amp_utils project
-- Copyright: Tim Littlefair 2025
-- For copying rules see
-- https://github.com/tim-littlefair/feral-horse-amp-utils/blob/main/LICENSE

lfs = require 'lfs'
cjson = require 'cjson'

local Fhau = {}

local session_start_time_t = os.time()
local session_name = "session_"..os.date("%Y%m%d%H%M%S")
lfs.mkdir("../"..session_name)

-- On the Balena node, the jar is presently in the top directory
local jar_file_name="desktopFHAUcli-0.0.0.jar"

fhau_cli_input_fd = io.popen(
    "cd .. && " ..
    "java -jar " .. jar_file_name .. " --no-disclaimer --web=" .. session_name .. " > fhau.log",
    "w"
)

function Fhau:send_cli_command(command)
    response = nil

    -- Give the command a tag
    session_elapsed_seconds = os.time() - session_start_time_t
    command_tag = string.format("%08.2f.json",session_elapsed_seconds)
    fhau_cli_input_fd:write(
        string.format("web %s %s",command_tag, command)
    )
    for i=1,5 do
        response_file_fd  = io.open(command_tag,"r")
        if response_file_fd
        then
            response = response_file_fd:read("*ALL")
            response_file_fd:close()
            return response
        else
            os.execute("sleep 1")
        end
    end
    return string.format(
        "request with tag %s for command %s timed out",
        command_tag, command
    )
end

function Fhau:get_cxn_and_dev_status()
    local retval
    print(lfs.currentdir())
    fd = io.open(session_name.."/txn00-startProvider-001.json","rb")
    if fd
    then
        retval=cjson.decode(fd:read("*all")).message
    else
        retval="Connection not completed yet"
    end
    return retval
end

return Fhau
