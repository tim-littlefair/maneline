#!/usr/bin/env python3
"""Serve Maneline's Micro Plus browser controls and a persistent BLE session."""
import argparse
import asyncio
import contextlib
import json
import mimetypes
import socket
from pathlib import Path
from urllib.parse import urlsplit
from mmp_session import MicroPlusSession

WEB_ROOT = Path(__file__).resolve().parents[1] / 'web-app' / 'web_ui'


class HTTPError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


async def dispatch(method, path, headers, body, session, allowed_hosts=None):
    host = urlsplit('//' + headers.get('host', '')).hostname
    if allowed_hosts is not None and host not in allowed_hosts:
        raise HTTPError(403, 'Use the local Maneline address to open these controls.')
    if path.startswith('/api/'):
        if path != '/api/micro-plus':
            raise HTTPError(404, 'Page not found.')
        if method == 'GET':
            return 200, 'application/json', json.dumps(session.snapshot()).encode()
        if method != 'POST':
            raise HTTPError(405, 'Use GET or POST for this request.')
        # Browser writes must come from this same host; no cross-origin controls.
        origin = headers.get('origin')
        if origin is None or urlsplit(origin).netloc != headers.get('host'):
            raise HTTPError(403, 'Open the controls on this server before changing presets.')
        if headers.get('content-type', '').split(';')[0].strip() != 'application/json':
            raise HTTPError(415, 'Send the preset number as JSON.')
        try:
            request = json.loads(body)
            if not isinstance(request, dict) or set(request) != {'preset'}:
                raise ValueError('Choose a preset number from 1 to 100.')
            state = await session.switch(request['preset'])
        except (ValueError, UnicodeError) as error:
            raise HTTPError(400, str(error)) from error
        except RuntimeError as error:
            raise HTTPError(503, str(error)) from error
        return 200, 'application/json', json.dumps(state).encode()
    if method != 'GET':
        raise HTTPError(405, 'This page only supports GET requests.')
    if path in ('/', '/index.html', '/micro-plus'):
        path = '/micro-plus.html'
    target = (WEB_ROOT / path.removeprefix('/web_ui/').lstrip('/')).resolve()
    if not target.is_relative_to(WEB_ROOT.resolve()) or not target.is_file():
        raise HTTPError(404, 'Page not found.')
    return 200, mimetypes.guess_type(target.name)[0] or 'application/octet-stream', target.read_bytes()


async def handle(reader, writer, session, allowed_hosts=None):
    try:
        async with asyncio.timeout(15):
            raw = await reader.readuntil(b'\r\n\r\n')
            if len(raw) > 8192:
                raise HTTPError(431, 'Request headers are too large.')
            lines = raw.decode('iso-8859-1').split('\r\n')
            method, target, _version = lines[0].split()
            headers = {}
            for line in lines[1:]:
                if line:
                    key, value = line.split(':', 1)
                    key = key.lower()
                    if key in headers:
                        raise HTTPError(400, 'Duplicate request header.')
                    headers[key] = value.strip()
            if 'transfer-encoding' in headers:
                raise HTTPError(400, 'Chunked requests are not supported.')
            length = int(headers.get('content-length', '0'))
            if not 0 <= length <= 1024:
                raise HTTPError(413, 'Request body is too large.')
            body = await reader.readexactly(length)
            status, content_type, result = await dispatch(
                method, urlsplit(target).path, headers, body, session, allowed_hosts)
    except HTTPError as error:
        status, content_type, result = error.status, 'application/json', json.dumps({'error': error.message}).encode()
    except (ValueError, asyncio.IncompleteReadError, asyncio.LimitOverrunError):
        status, content_type, result = 400, 'application/json', b'{"error":"Invalid HTTP request."}'
    except TimeoutError:
        status, content_type, result = 504, 'application/json', b'{"error":"The request timed out. Refresh to check the connection."}'
    try:
        writer.write((f'HTTP/1.1 {status} Response\r\nContent-Type: {content_type}; charset=utf-8\r\n'
                      f'Content-Length: {len(result)}\r\nCache-Control: no-store\r\n'
                      'X-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n').encode() + result)
        await writer.drain()
    except (ConnectionError, BrokenPipeError):
        pass
    finally:
        writer.close()
        with contextlib.suppress(ConnectionError):
            await writer.wait_closed()


async def run(args):
    Path(args.log).parent.mkdir(parents=True, exist_ok=True)
    with open(args.log, 'a', encoding='utf-8') as log:
        session = MicroPlusSession(args.device, log)
        hostname = socket.gethostname().lower().removesuffix('.local')
        allowed_hosts = {'localhost', '127.0.0.1', '::1', hostname, hostname + '.local',
                         *args.allow_host}
        server = await asyncio.start_server(lambda r, w: handle(r, w, session, allowed_hosts),
                                            '0.0.0.0', args.port, limit=8192)
        task = asyncio.create_task(session.run())
        print(f'Maneline Micro Plus controls: http://localhost:{args.port}', flush=True)
        try:
            async with server:
                await server.serve_forever()
        finally:
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8080)
    parser.add_argument('--device')
    parser.add_argument('--log', default='_work/mmp-web.jsonl')
    parser.add_argument('--allow-host', action='append', default=[],
                        help='Additional hostname or IP address allowed to access the controls')
    args = parser.parse_args()
    try:
        asyncio.run(run(args))
    except KeyboardInterrupt:
        pass


if __name__ == '__main__':
    main()
