-- /usr/bin/lua
-- fhau.lua
-- The purpose of this script is to start the fhau command line process
-- and enable sibling script run_pegasys.lua to send commands to the
-- running subprocess.

-- Part of the feral_horse_amp_utils project
-- Copyright: Tim Littlefair 2025
-- For copying rules see
-- https://github.com/tim-littlefair/feral-horse-amp-utils/blob/main/LICENSE

require 'lfs'

local session_start_time_t = os.time()
local session_name = "session_"..os.date("%Y%m%d%H%M%S")
lfs.mkdir(session_name)

-- On the Balena node, the jar is presently in the top directory
local jar_file_path="desktopFHAUcli-0.0.0.jar"
-- Test for its presence by attempting to open it
jar_file_fd  = io.open(jar_file_path,"r")
if jar_file_fd
then
    print(jar_file_path .. " found in current directory")
    jar_file_fd:close()
else
    -- Testing this script on the development host, the jar
    -- is in ../tmp relative to the directory which contains
    -- the Lua scripts
    print(jar_file_path .. " assumed to be in ../tmp")
    jar_file_path="../tmp/"..jar_file_path
end

fhau_cli_input_fd = io.popen(
    "cd "..session_name .." && " ..
    "java -jar ../" .. jar_file_path .. " --no-disclaimer --output=. > fhau.log",
    "w"
)

function send_cli_command(command)
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

-- For debug/development we want to run this script standalone
-- Change 'false' to 'true' to do this...
if false then
    -- The code below attempts to write to the input file descriptor
    -- for fhau CLI - when the write fails it tells us that the subprocess
    -- has exited.
    for i=1,25 do
        fhau_cli_input_fd:write(".")
        fhau_cli_input_fd:flush()
        --print(io.type(fhau_cli_input_fd))
        os.execute("sleep 2")
    end
    print("CLI app may not be closed")
end