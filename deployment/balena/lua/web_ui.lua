-- /usr/bin/lua
-- web_ui.lua
-- The purpose of this library is to contain all logic related to
-- building up the HTML pages of the web UI.

-- Part of the feral_horse_amp_utils project
-- Copyright: Tim Littlefair 2025
-- For copying rules see
-- https://github.com/tim-littlefair/feral-horse-amp-utils/blob/main/LICENSE

local lfs = require('lfs')

local Web_UI = {}


function file_text(file_path)
    fd = io.open(file_path)
    if(fd)
    then
        text = fd:read("*all")
        fd:close()
        return text
    else
        print("Failed to open",file_path,"from",lfs.currentdir())
        return nil
    end
end

function Web_UI:build_cds_html(startup_messages)
    header_text = file_text("web_ui/frame_head.html.fragment")
    body_text = file_text("web_ui/cds_body.html.fragment")
    if(header_text and body_text)
    then
        return string.gsub(
            header_text .. body_text,
            "%%MESSAGES_STRING%%",startup_messages
        )
    else
        return startup_messages
    end
end

function Web_UI:build_all_presets_html()
    header_text = file_text("web_ui/frame_head.html.fragment")
    body_text = file_text("web_ui/all-presets_body.html.fragment")
    if(header_text and body_text)
    then
        return header_text .. body_text
    else
        return "problems?"
    end
end

return Web_UI

