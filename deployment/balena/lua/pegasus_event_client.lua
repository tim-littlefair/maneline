#! lua

local logging = require('logging')
local socket = require('socket')
local pegasus = require('pegasus')
local pegasus_handler = require('pegasus.handler')
local lfs = require('lfs')
local fhau_cli = require('fhau_cli')
local cjson = require('cjson.safe')

local logger = logging.new(
    function(self,level,message)
        print(level, message)
        return true
    end
)
logger:setLevel(logging.WARN)
logger:info("Pegasus logging enabled")

local port = 9090

local retval = {}

retval._port = port
retval._socket, err = socket.bind('*', port)
assert(retval._socket, err)

function callback(request,response)
    -- io.stdout:write(retval._phdlr==nil)
    io.stdout:write("Request: ", request:method()," ",request:path())
    local req_path = request:path()
    if request:method()=="POST"
    then
        local post_params = request:post()
        io.stdout:write("POST params: ",cjson.encode(post_params))
        response:write(cjson.encode(post_params))
        fhau_cli:relay_stdin_line(post_params.command.." "..post_params.slot.."\n")
    elseif request:method()~="GET"
    then
        io.stdout:write("Unexpected method: ", request:method())
        io.stdout:write("Non-get methods TBD")
    else
        if req_path=="/cds"
        then
            response:addHeader("Cache-Control","no-cache")
            status = fhau_cli:get_cxn_and_dev_status()
            response:write("<html>"..status.."</html>")
        elseif req_path=="/all-presets"
        then
            response:addHeader("Cache-Control","no-cache")
            all_presets = fhau_cli:get_all_presets()
            response:write(all_presets)
        elseif req_path=="/suite"
        then
            -- response:addHeader("Cache-Control","no-cache")
            -- For the moment, the suites are not editable so it is
            -- OK for them to be cached
            response:addHeader("Cache-Control","max-age=3600, stale-while-revalidate=10")
            suite_num = request.querystring.num
            suite_name = request.querystring.name
            preset_suite = fhau_cli:get_preset_suite(suite_num,suite_name)
            response:write(preset_suite)
        elseif req_path=="/favicon.ico"
        then
            response:writeFile("./web_ui/_static/app-icon-512x512.png")
        else
            if lfs.attributes("."..req_path)
            then
                if req_path:find("web_ui")
                then
                    response:addHeader("Cache-Control","max-age=3600, stale-while-revalidate=10")
                end
                response:writeFile("."..req_path)
            else
                response:addHeader("Cache-Control","no-cache")
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
    self._phdlr.log = logger
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
