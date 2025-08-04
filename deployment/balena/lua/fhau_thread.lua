-- /usr/bin/lua
-- fhau.lua
-- The purpose of this script is to start the fhau command line process
-- and enable sibling script run_pegasys.lua to send commands to the
-- running subprocess.

-- Part of the feral_horse_amp_utils project
-- Copyright: Tim Littlefair 2025
-- For copying rules see
-- https://github.com/tim-littlefair/feral-horse-amp-utils/blob/main/LICENSE

-- START OF LOGIC COPIED FROM p.43 OF
-- https://sources.debian.org/data/main/l/lua-cqueues/20200726-2/doc/cqueues.pdf

local cqueues = require"cqueues"
local thread = require"cqueues.thread"

-- we start a thread and pass two parameters --`0' and '9'
local thr , con = thread.start(function(con , i, j)
    -- the `cqueues ' upvalue defined above is gone
    local cqueues = require"cqueues"
    local cq = cqueues.new ()

    cq:wrap(function()
        for n = tonumber (i), tonumber(j) do
            io.stdout:write("sent ", n, "\n")
            con:write(n, "\n")
            -- sleep so our stdout writes don 't mix
            cqueues.sleep (0.1)
        end
    end)
    assert(cq:loop ())
end, 0, 9)

local cq = cqueues.new ()
cq:wrap(function()
    while(1)
    do
        ln=con:read("*l")
        if(ln=="" or ln==nil)
        then
            break
        end
        io.stdout:write(ln , " rcvd", "\n")
    end

    local ok , why = thr:join ()

    if ok then
        print(why or "OK")
    else
        error(require"cqueues.errno".strerror(why ))
    end
end)

assert(cq:loop ())

-- END OF LOGIC ADAPTED FROM p.43 OF
-- https://sources.debian.org/data/main/l/lua-cqueues/20200726-2/doc/cqueues.pdf

--[[
lfs = require 'lfs'
cjson = require 'cjson'
web_ui = require 'web_ui'
cqueues = require 'cqueues'
socket = require 'socket'

fhau_errors = require 'fhau_errors'

local Fhau = {}

local session_start_time_t = os.time()
local session_name = "session_"..os.date("%Y%m%d%H%M%S")
lfs.mkdir("../"..session_name)

-- On the Balena node, the jar is presently in the top directory
local fhau_cli_input_fd = nil

-- Use the Lua cqueues library to create a loop checking
-- for commands on standard input
-- NB There is a cqueues manual at
-- https://25thandclement.com/~william/projects/cqueues.pdf.
-- On the sixth page of the PDF (numbered page 1) there is
-- a statement that the library is not compatible with Lua 5.1,
-- but for the moment it seems to be working.
local stdin_relay_queue = cqueues.new()

-- stdin_relay_queue:timeout(function() return 1.0; end)

function stdin_relay_queue:pollfd()
    fds_with_queued_input = socket.select([1],[],1.0))
    print(fds_with_queued_input)
    if(fds_with_queued_input)
    then
        return 1
    else
        return nil
    end
end


function read_queued_input_bytes()
    cqueues:poll()
    while(1)
    do
        new_bytes = io.read(1024)
        if(new_bytes=="")
        then
            return
        else
            queued_input_bytes = queued_input_bytes..new_bytes
        end
        do(
            next_newline = string.find()
        queued_input_bytes.append()
        if(queued_input_bytes=="")
        then
            break
        end

        if(line)
        then
            fhau_cli_input_fd:write(line.."\n")
            fhau_cli_input_fd:flush()
        end
        cqueues:poll()
    end
end
stdin_relay_queue:wrap()

function Fhau:check_cli_subprocess()
    -- Our only link to the CLI subprocess is the file descriptor
    -- we use to send commands.

    -- We don't open the file descriptor until this function is
    -- called for the first time
    if(fhau_cli_input_fd==null)
    then
        print("Starting FHAU subprocess")
        jar_file_name="desktopFHAUcli-0.0.0.jar"
        fhau_cli_input_fd = io.popen(
            "java -jar " .. jar_file_name .. " --web=" .. session_name,
            "w"
        )
        print("FHAU subprocess started")
        print("stdin relay loop started")
    end

    -- Read and relay any output from the subprocess
    assert(stdin_relay_queue:loop())

    -- We check whether it is alive by sending a newline (which
    -- will be ignored as a command), and seeing whether the
    -- flush after writing to the FD fails
    fhau_cli_input_fd:write("\n");
    flush_status=fhau_cli_input_fd:flush();
    return flush_status
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
    local retval
    fd = io.open(session_name.."/txn00-startProvider-001.json","rb")
    if fd
    then
        retval=cjson.decode(fd:read("*all")).message
        fd:close()
    else
        retval="Connection not completed yet"
    end

    if(Fhau:check_cli_subprocess()==nil)
    then
        print("USB/HID CLI process appears to have died")
        os.exit(fhau_errors.FATAL_CLI_HAS_EXITED)
    end

    return build_cds_html(retval)
end

return Fhau
]]
