-- /usr/bin/lua
-- web_ui.lua
-- The purpose of this library is to define error constants
-- for different error conditions which can cause the lua
-- server to exit or perform error recovery

-- Part of the feral_horse_amp_utils project
-- Copyright: Tim Littlefair 2025
-- For copying rules see
-- https://github.com/tim-littlefair/feral-horse-amp-utils/blob/main/LICENSE

FHAU_Errors = {}

-- Non-fatal error conditions are assigned numbers
-- in the range 81-89
FHAU_Errors.ERROR_HTML_FRAGMENT_NOT_FOUND=81

-- Fatal error conditions are assigned numbers in the
-- range 91-99
FHAU_Errors.FATAL_CLI_HAS_EXITED=91

return FHAU_Errors



