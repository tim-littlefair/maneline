-- based on:
-- https://github.com/EvandroLG/pegasus.lua

local cjson = require 'cjson'
local pegasus = require 'pegasus'


local server = pegasus:new({
  port='9090',
  --location='example/root'
  location='/opt/fhau'
})

server:start(function (request, response)
  print("\nRequest:", request:path())
  response:writeFile("."..request:path())
  --return response.close()
end)
