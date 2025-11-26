#! /usr/bin/lua

-- fhau_cli.lua
-- The purpose of this package is to start the fhau command line process
-- and relay commands to it from the web app.

-- Part of the maneline project released under GPL 2.0
-- Copyright: Tim Littlefair 2025
-- For copying rules see
-- https://github.com/tim-littlefair/maneline/blob/main/LICENSE


lfs = require 'lfs'
cjson = require 'cjson'
fhau_errors = require 'fhau_errors'

local Fhau = {}

local session_start_time_t = os.time()
local session_name = "session_"..os.date("%Y%m%d%H%M%S")
local fhau_cli_input_fd = nil
local retained_session_names = {}

function Fhau:manage_session_dirs(number_to_retain)
    session_dirs = io.popen('test ! -z "session_*" && ls -1d session_* | sort --reverse',"r")
    local dir=nil
    -- Read the first line, which will be the current session
    -- We want to exclude this from the logic below which creates
    -- a session zip file
    session_dirs:read("*line")
    for i=1,number_to_retain*2
    do
        dir=session_dirs:read("*line")
        if dir==nil
        then
            break
        elseif dir:match(".zip$")
        then
            -- skip
        else
            session_zip = dir..".zip"
            if lfs.attributes(session_zip)==nil
            then
                zip_status = os.execute(
                    "cd "..dir.." && zip -r ../"..session_zip.." . && cd .."
                )
                print(
                    "Retaining "..dir..
                    " and created "..session_zip..
                    " with status "..zip_status
                )
            else
                print("Retaining "..dir.." and "..session_zip)
            end
            table.insert(retained_session_names,dir)
        end
    end
    -- Any remaining subdirectories need to be deleted
    while(true)
    do
        if(dir==nil)
        then
            break
        else
            print("Deleting "..dir)
            os.execute("rm -rf "..dir)
            dir=session_dirs:read("*line")
        end
    end
    print(#retained_session_names.." session directories retained")
end

function Fhau:start_fhau_cli()
    local jar_file_path=os.getenv("cli_jar")
    fhau_cli_input_fd = io.popen(
        "java -jar " .. jar_file_path .. " --web=" .. session_name,
        "w"
    )
end

function Fhau:check_for_cli_death()
    -- We use the modified timestamp on the session directory
    -- to measure session duration
    -- Update the timestamp to ensure that sessions which aren't
    -- actively logging still get the right duration
    lfs.touch(session_name)

    -- Our only link to the CLI subprocess is the file descriptor
    -- we use to send commands.
    -- We check whether it is alive by sending a newline (which
    -- will be ignored as a command), and seeing whether the
    -- flush after writing to the FD fails
    fhau_cli_input_fd:write("\n");
    flush_status=fhau_cli_input_fd:flush();
    if(flush_status~=true)
    then
        local close_status=fhau_cli_input_fd:close()
        if(close_status)
        then
            print("CLI process has completed OK")
            os.exit(0)
        else
            print("CLI process has completed with error")
            os.exit(fhau_errors.FATAL_CLI_EXITED_BADLY)
        end
    end
end

function Fhau:relay_stdin_line(line)
    if(line)
    then
        fhau_cli_input_fd:write(line.."\n")
        fhau_cli_input_fd:flush()
    else
        print("Nothing to relay")
    end
end

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
    local cxn_status
    fd1 = io.open(session_name.."/txn00-startProvider-001.json","rb")
    if fd1
    then
        cxn_status=cjson.decode(fd1:read("*all")).message
        fd1:close()
    end
    local preset_status
    fd2 = io.open(session_name.."/current-preset-details-001.json","rb")
    while fd2
    do
        preset_json=fd2:read("*line")
        if preset_json
        then
            preset_status = cjson.decode(preset_json).message
        else
            fd2:close()
            break
        end
    end

    local retval
    if cxn_status and preset_status
    then
        retval = cxn_status .. "\n\n" .. preset_status
    elseif cxn_status
    then
        retval = cxn_status .. "\nPreset status not known"
    else
        retval = "FMIC device not connected yet"
    end
    retval = retval:gsub("\n","<br/>")
    retval = retval:gsub(",",", &nbsp;")
    return retval
end

function Fhau:get_all_presets_path()
    return session_name .. "/all-presets.preset_suite.json"
end

function Fhau:get_preset_suite_path(num,name)
    return string.format(
       "%s/suites/%s-%s.preset_suite.json",
       session_name, num, name
    )
end

function Fhau:get_retained_session_names()
    return retained_session_names
end

function Fhau:get_current_session_name()
    return session_name
end

return Fhau
