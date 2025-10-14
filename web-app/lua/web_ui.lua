#!/usr/bin/lua

-- web_ui.lua
-- The purpose of this package is to contain all logic related to
-- building up the HTML pages of the web UI.
-- Generation and retrieval of file paths for dynamic content
-- in the session directory is delegated to fhau_cli.

-- Part of the maneline project released under GPL 2.0
-- Copyright: Tim Littlefair 2025
-- For copying rules see
-- https://github.com/tim-littlefair/maneline/blob/main/LICENSE

local lfs = require('lfs')
local fhau_cli = require('fhau_cli')
local cjson = require('cjson.safe')
local sessions = require('sessions')

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

function build_cds_html(startup_messages)
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

function Web_UI:build_preset_suite_html(suite_name, suite_file_path, header_level)
    header_text = file_text("web_ui/frame_head.html.fragment")
    body_start = string.gsub(string.gsub(
        file_text("web_ui/preset_suite_body_start.html.fragment"),
        "#SUITE_NAME#", suite_name
    ),"#HEADER_LEVEL#", header_level)
    suite_json = file_text(suite_file_path)
    body_end = nil
    if(suite_json~=nil)
    then
        body_end = string.gsub(
            file_text("web_ui/preset_suite_body_end.html.fragment"),
            "#SUITE_JSON#", suite_json
        )
    else
        body_end = file_text("web_ui/not_connected_body_end.html.fragment")
    end
    if(header_text and body_start and body_end)
    then
        return header_text .. body_start .. body_end
    else
        return "problems?"
    end
end

function Web_UI:build_sessions_html(retained_session_names, current_session_name)
    header_text = file_text("web_ui/frame_head.html.fragment")
    body_start = file_text("web_ui/sessions_body_start.html.fragment")
    all_sessions = sessions:get_sessions(retained_session_names, current_session_name)
    sessions_json = cjson.encode(all_sessions)
    body_end = nil
    if(sessions_json~=nil)
    then
        body_end = string.gsub(
            file_text("web_ui/sessions_body_end.html.fragment"),
            "#SESSIONS_JSON#", sessions_json
        )
    else
        body_end = file_text("web_ui/not_connected_body_end.html.fragment")
    end
    if(header_text and body_start and body_end)
    then
        return header_text .. body_start .. body_end
    else
        return "problems?"
    end
end

function Web_UI:handle(request, response)
    local req_path = request:path()
    if request:method()=="POST"
    then
        local post_params = request:post()
        io.stdout:write("post_params: "..cjson.encode(post_params).."\n")
        response:write(cjson.encode(post_params))
        if not post_params
        then
            -- What is going on???
            io.stderr:write("post_params evaluates false")            
        elseif(post_params.slot)
        then
            fhau_cli:relay_stdin_line(post_params.command.." "..post_params.slot.."\n")
        elseif(post_params.command)
        then
            fhau_cli:relay_stdin_line(post_params.command.."\n")
        else
            -- What is going on???
            io.stderr:write("POST params: ",cjson.encode(post_params))
        end
    elseif request:method()~="GET"
    then
        io.stdout:write("Unexpected method: ", request:method())
        io.stdout:write("Non-get methods TBD")
    else
        if req_path=="/index.html"
        then
            response:addHeader("Cache-Control","max-age=60, stale-while-revalidate=120")
            response:writeFile("./web_ui/index.html")
        elseif req_path=="/cds"
        then
            response:addHeader("Cache-Control","no-cache")
            status = fhau_cli:get_cxn_and_dev_status()
            cds_html = build_cds_html("<p>"..status.."</p>")
            response:write(cds_html)
        elseif req_path=="/all-presets"
        then
            response:addHeader("Cache-Control","no-cache")
            all_presets_path = fhau_cli:get_all_presets_path()
            all_presets_html = Web_UI:build_preset_suite_html("All Presets", all_presets_path, "2")
            response:write(all_presets_html)
        elseif req_path=="/suite"
        then
            response:addHeader("Cache-Control","no-cache")
            suite_num = request.querystring.num
            suite_name = request.querystring.name
            suite_path = fhau_cli:get_preset_suite_path(suite_num, suite_name)
            suite_html = Web_UI:build_preset_suite_html(suite_name, suite_path, "3")
            response:write(suite_html)
        elseif req_path=="/sessions"
        then
            response:addHeader("Cache-Control","no-cache")
            sessions_html = Web_UI:build_sessions_html(
                fhau_cli:get_retained_session_names(),
                fhau_cli:get_current_session_name()
            )
            response:write(sessions_html)
        elseif req_path=="/favicon.ico"
        then
            response:addHeader("Cache-Control","max-age=60, stale-while-revalidate=120")
            response:writeFile("./web_ui/_static/maneline-icon-512x512.png")
        else
            if lfs.attributes("."..req_path)
            then
                if req_path:find("web_ui")
                then
                    response:addHeader("Cache-Control","max-age=60, stale-while-revalidate=120")
                end
                response:writeFile("."..req_path)
            else
                response:addHeader("Cache-Control","no-cache")
                io.stdout:write(" !!!not found!!!")
                response:write("<html>Not found: "..req_path.."</html>")
            end
        end
    end
end

return Web_UI

