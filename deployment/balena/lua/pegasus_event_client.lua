#! lua

local socket = require('socket')
local pegasus_handler = require('pegasus.handler')
local lfs = require('lfs')
local fhau_cli = require('fhau_cli')

local port = 9090

local retval = {}

retval._port = port
retval._socket, err = socket.bind('*', port)
assert(retval._socket, err)

function callback(request,response)
    -- io.stdout:write(retval._phdlr==nil)
    io.stdout:write("Request: ", request:method()," ",request:path())
    local req_path = request:path()
    if request:method()~="GET"
    then
        io.stdout:write("POST params: ", cjson.encode(request.post()))
        response:write(cjson.encode(request.post()))
        io.stdout:write("Non-get methods TBD")
    else
        response:addHeader("Cache-Control","no-cache")
        if req_path=="/cds"
        then
            status = fhau_cli:get_cxn_and_dev_status()
            response:write("<html>"..status.."</html>")
        elseif req_path=="/all-presets"
        then
            all_presets = fhau_cli:get_all_presets()
            response:write(all_presets)
        elseif req_path=="/favicon.ico"
        then
            response:writeFile("./web_ui/_static/app-icon-512x512.png")
        else
            if lfs.attributes("."..req_path)
            then
                response:writeFile("."..req_path)
            else
                io.stdout:write(" !!!not found!!!")
                response:write("<html>Not found: "..req_path.."</html>")
            end
        end
    end
    retval = response:close()
    return retval
end
retval._phdlr=nil
retval._client = nil

function retval:create_handler(rundir)
    assert(self._phdlr==nil)
    self._phdlr = pegasus_handler:new(callback, rundir)
end

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
    assert(self._phdlr~=nil)
    self._phdlr:processRequest(self._port,self._client)
    self._client:close()
    self._client=nil
end

return retval
