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
web_ui = require 'web_ui'
cqueues = require 'cqueues'
fhau_errors = require 'fhau_errors'

local Fhau = {}

local session_start_time_t = os.time()
local session_name = "session_"..os.date("%Y%m%d%H%M%S")
local fhau_cli_input_fd = nil

function Fhau:purge_stale_session_dirs(number_to_retain)
    session_dirs = io.popen("ls -1d session_* | sort --reverse","r")
    for i=0,number_to_retain
    do
        dir=session_dirs:read("*line")
        if(dir==nil)
        then
            break
        else
            print("Retaining "..dir)
        end
    end
    while(session_dirs)
    do
        dir=session_dirs:read("*line")
        if(dir==nil)
        then
            break
        else
            print("Deleting "..dir)
            os.execute("rm -rf "..dir)
        end
    end
end

function Fhau:start_fhau_cli()
    local jar_file_name="jar/maneline-cli-0.0.0.jar"
    fhau_cli_input_fd = io.popen(
        "java -jar " .. jar_file_name .. " --web=" .. session_name,
        "w"
    )
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

function check_for_cli_death()
    -- Our only link to the CLI subprocess is the file descriptor
    -- we use to send commands.
    -- We check whether it is alive by sending a newline (which
    -- will be ignored as a command), and seeing whether the
    -- flush after writing to the FD fails
    fhau_cli_input_fd:write("\n");
    flush_status=fhau_cli_input_fd:flush();
    return flush_status~=true
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
    if(check_for_cli_death())
    then
        print("USB/HID CLI process appears to have died")
        os.exit(fhau_errors.FATAL_CLI_HAS_EXITED)
    end

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
        retval = cxn_status .. "\n" .. preset_status
    elseif cxn_status
    then
        retval = cxn_status .. "\nPreset status not known"
    else
        retval = "FMIC device not connected yet"
    end
    retval = retval:gsub("\n","<br/>")
    retval = retval:gsub(",",", &nbsp;")
    retval = web_ui:build_cds_html("<p>"..retval.."</p>")
    return retval
end

function Fhau:get_all_presets()
    return web_ui:build_preset_suite_html(
        "All Presets", 
        session_name .. "/all-presets.preset_suite.json",
        "2"
    )
end

function Fhau:get_preset_suite(num,name)
    -- TBD: At some time in the future this class needs
    -- to compile a JSON object describing the presets
    -- which will be passed in the following function
    -- call and will affect the HTML output
    -- return web_ui:build_all_presets_html()
    return web_ui:build_preset_suite_html(
        name,
        string.format(
            "%s/suites/%s-%s.preset_suite.json",
            session_name, num, name
        ),
        "3"
    )
end
return Fhau
