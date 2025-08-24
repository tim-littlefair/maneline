#! lua

local EventLoop = {}

local _enable_debug = false

function _debug_mark(c)
    if _enable_debug==true
    then
        io.stdout:write(c)
        io.stdout:flush()
    end
end

function EventLoop.run_event_loop(stdin_evtclt, pegasus_evtclt, enable_debug, active_timeout, passive_timeout)
    socket = require 'socket'

    _enable_debug = enable_debug or false
    _active_timeout = active_timeout or 0.1
    _passive_timeout = passive_timeout or 1.0

    local timeout=_active_timeout
    while(1)
    do
        stdin_evtclt:settimeout(timeout)
        fd_array = { stdin_evtclt:fd() }
        pegasus_evtclt:settimeout(timeout)
        pegasus_client_fd = pegasus_evtclt:fd()
        if(pegasus_client_fd)
        then
            fd_array[#fd_array+1] = pegasus_client_fd
        end
        select_rv = socket.select(fd_array, nil, timeout)
        if(#select_rv==0)
        then
            -- _debug_mark("!")
            timeout=_passive_timeout
        elseif(select_rv[#select_rv]==pegasus_client_fd)
        then
            _debug_mark("web input<\n")
            pegasus_evtclt:handler()
            timeout=_active_timeout
            _debug_mark("\n>\n")
        else
            stdin_bytes = io.stdin:read("*line")
            if(stdin_bytes)
            then
                _debug_mark("stdin input<\n")
                _debug_mark(stdin_bytes)
                _debug_mark("\n>\n")
                stdin_evtclt:handler(stdin_bytes)
                timeout=_active_timeout
            else
                if(io.stdin:read(0)==nil)
                then
                    _debug_mark("\nstdin closed\n")
                    break
                else
                    timeout=_passive_timeout
                end
            end
        end
        io.stdout:flush()
    end
end

return EventLoop
