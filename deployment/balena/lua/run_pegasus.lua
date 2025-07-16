-- based on:
-- https://github.com/EvandroLG/pegasus.lua

local cjson = require 'cjson'
local pegasus = require 'pegasus'
local fhau = require 'fhau'

local server = pegasus:new({
  port='9090',
  --location='example/root'
  location='/opt/fhau'
})

server:start(
    function (request, response)
        print("Request:", request:path())
        -- print("Headers:", cjson.encode(request:headers()))
        if request:method() == 'GET'
        then
            response:writeFile("."..request:path())
        elseif request:method() == 'POST'
        then
            print("POST params: ", cjson.encode(request.post()))
            response:write(cjson.encode(request.post()))
        else
            response:write("Unsupported request method")
        end
    end
)

