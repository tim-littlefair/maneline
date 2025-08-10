-- based on:
-- https://github.com/EvandroLG/pegasus.lua

--[[
local cjson = require 'cjson'
local pegasus = require 'pegasus'
local fhau = require 'fhau_thread2'
local lfs = require 'lfs'

-- scripts/run_web_cli.sh is intended to provide
-- a way to simulate the balena deploy environment
-- on the Linux desktop development platform.end
-- The simulated environment set up by this scripts
-- accesses the directory containing lua scripts
-- via a symlink, so the base directory of the install
-- needs to be passed in as a parameter as, on the
-- development environment Balena simulation,
-- ".." points to the parent of the symlink target,
-- not the base of the simulated Balena install.
lfs.chdir(arg[1])

local server = pegasus:new({
  port='9090',
  location="."
})

-- The very first subprocess check will start
-- the thread which relays commands from the parent
-- process stdin to the subprocess's stdin
fhau:check_cli_subprocess()

server:start(
    function (request, response)
        print("Request:", request:path())
        cli_ok = fhau:check_cli_subprocess()
        print("CLI status:",cli_ok)
        -- print("Headers:", cjson.encode(request:headers()))
        if request:path()=="/cds"
        then
            if(cli_ok)
            then
                status = fhau.get_cxn_and_dev_status()
                -- print(status)
                response:write("<html>"..status.."</html>")
            else
                print("CLI subprocess not OK")
                response:write("<html>CLI subprocess not OK</html>")
            end
        elseif request:method() == 'GET'
        then
            response:writeFile(request:path())
        elseif request:method() == 'POST'
        then
            print("POST params: ", cjson.encode(request.post()))
            response:write(cjson.encode(request.post()))
        else
            response:write("Unsupported request method")
        end
        retval = response:close()
        cqueues.sleep(0.1)
        return retval
    end
)
]]

local socket = require('socket')
local pegasus_handler = require('pegasus.handler')
local port = 9090

local retval = {}

retval._port = port
retval._socket, err = socket.bind('*', port)
assert(retval._socket, err)

function callback(request,response)
    io.stdout:write("Request: ", request:method()," ",request:path())
    if request:method()~="GET"
    then
        print("POST params: ", cjson.encode(request.post()))
        response:write(cjson.encode(request.post()))
        io.stdout:write("Non-get methods TBD")
    elseif request:path()=="/cds"
    then
        if(true)
        then
            status = "FHAU OK" -- fhau.get_cxn_and_dev_status()
            -- print(status)
            response:write("<html>"..status.."</html>")
        else
            print("CLI subprocess not OK")
            response:write("<html>CLI subprocess not OK</html>")
        end
    else 
        response:writeFile(request:path())
    end
    retval = response:close()
    return retval
end
retval._phdlr = pegasus_handler:new(callback, "..")
retval._client = nil
function retval:settimeout(timeout)
    self._socket:settimeout(timeout)
end
function retval:fd()
    if(self._client==nil)
    then
        self._client = self._socket:accept()
    end
    return self._client
end
function retval:handler()        
    assert(self._client~=nil)
    self._phdlr:processRequest(self._port,self._client)
    self._client:close()
    self._client=nil
end

return retval
