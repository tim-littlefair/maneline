-- /usr/bin/lua
-- check_requirements.lua
-- This script is run by the scripts/run_web_cli.sh process
-- to check that all required Lua dependencies have been
-- installed.

local lfs = require 'lfs'
local cjson = require 'cjson'
local pegasus = require 'pegasus'
local cqueues = require 'cqueues'

os.exit(0)


