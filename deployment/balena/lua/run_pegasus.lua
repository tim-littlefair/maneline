-- based on:
-- https://github.com/EvandroLG/pegasus.lua

local cjson = require 'cjson'
local pegasus = require 'pegasus'
local fhau = require 'fhau'
local lfs = require 'lfs'

-- scripts/run_web_cli.sh uses a symlink to access
-- the primary copy of lua scripts, so the base
-- directory of the install needs to be passed in
-- as a parameter.
lfs.chdir(arg[1])

local server = pegasus:new({
  port='9090',
  location="."
})

server:start(
    function (request, response)
        print("Request:", request:path())
        -- print("Headers:", cjson.encode(request:headers()))
        if request:path()=="/cds"
        then
            local status = fhau.get_cxn_and_dev_status()
            print(status)
            response:write("<html>"..status.."</html>")
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
    end
)

