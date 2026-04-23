import asyncio
import sys
import traceback
import subprocess
import json
import select
import time
import tty

def _read_with_timeout(fd, timeout):
    bytes_read = bytes()
    while False:
        print(".",flush=True, end="")
        r, _, _ = select.select([fd],[],[],timeout)
        if len(r)==1:
            print("r",flush=True, end="")
            assert r[0]==fd
            chunk = fd.read()
            print("+",flush=True, end="")
            print(chunk)
            assert len(chunk)>0
            bytes_read += chunk
        else:
            break
    print(".",flush=True, end="")
    time.sleep(timeout)
    chunk = fd.read()
    print("+",flush=True, end="")
    print(chunk)
    assert len(chunk)>0
    bytes_read += chunk

    return bytes_read


def hcitool_run(args_string, timeout=None):
    return _tool_run("/usr/bin/hcitool")

class GattCommander:
    def __init__(self, bdaddr, debug=True, send_timeout=0.1, proc_timeout=1.0, recv_timeout=10.0):
        self.bdaddr = bdaddr
        self.debug = debug
        self.send_timeout = send_timeout
        self.proc_timeout = proc_timeout
        self.recv_timeout = recv_timeout
        self.subprocess = None

    async def start_subprocess(self):
        self.subprocess = await asyncio.create_subprocess_shell(
            cmd = f"/usr/bin/gatttool -b {self.bdaddr} -I",
            stdin = asyncio.subprocess.PIPE,
            stdout = asyncio.subprocess.PIPE,
            stderr = asyncio.subprocess.STDOUT
        )


    async def send(self, cmd):
        async with asyncio.timeout(self.send_timeout):
            self.subprocess.stdin.write(cmd.encode())
            await self.subprocess.stdin.drain()
            print(f">{cmd}")
        await asyncio.sleep(self.proc_timeout)

    async def recv(self):
        received = str()
        try:
            while True:
                await asyncio.sleep(self.proc_timeout)
                async with asyncio.timeout(self.recv_timeout):
                    line = str(await self.subprocess.stdout.readline(), "utf-8")
                    received += line
                    print(f"\n<{line}", flush=True)
        except TimeoutError:
            print(received)
            return received

    async def connect_or_reconnect(self):
        await self.send_command("connect","connected")

    async def send_command(self, cmd, expected_reply):
        await self.send(cmd)
        response = ""
        while expected_reply not in response:
            response += await self.recv()
            if self.debug is True:
                print(response)
        return response

    async def disconnect_and_exit(self):
        await self.send_command("disconnect","")
        self.send_command("exit", reply_expected_text="", reply_timeout=5.0)




def gatttool_run(args_string, timeout=None):
    return _tool_run("/usr/bin/gatttool -b 84:17:15:2B:4E:7E", args_string, timeout)


def _tool_run(tool_cmd_prefix, args_string, timeout, only_start=False):
    if timeout is None:
        timeout = 10.0
    if only_start is True:
        return subprocess.Popen(
            args=tool_cmd_prefix + " " + args_string,
            shell=True,
            text=True,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT
        )
    else:
        return subprocess.run(
            args=tool_cmd_prefix + " " + args_string,
            shell=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout
        )


"""
    def read_lines(self, lines_to_read, per_read_timeout, max_read_attempts):
        lines_read = []
        for i in range(0,max_read_attempts):
            r, _, _ = select.select([self.popen.stdout],[],[],per_read_timeout)
            if len(r)>0:
                line = r[0].readline().strip()
                print(line,flush=True)
                lines_read += [ line ]
                if len(lines_read)==lines_to_read:
                    return lines_read
            else:
                print(".",flush=True,end='')
        return lines_read
"""

class ScriptRunner:
    def __init__(self, gatt_commander):
        self.gatt_commander = gatt_commander

    def process_script_section(self, section_name, timeout=None, only_start=False):
        global _SCRIPT_SECTIONS
        if timeout is not None:
            pass
        elif only_start is True:
            timeout = 10.0
        else:
            timeout = 1.0
        next_command = None
        response = None
        for cmd in _SCRIPT_SECTIONS.get(section_name):
            try:
                next_command = cmd.get("send", None)
                if next_command is None:
                    continue
                print(f"Sending '{next_command}'", flush=True, end="")
                expect_text = cmd.get("expect_text","")
                response = gatt_commander.send_command(next_command,expect_text)
                print(response)
                assert expect_text in response
            except AssertionError:
                print(sys.exc_info()[1])
                traceback.print_tb(sys.exc_info()[2])
                break


if __name__ == "__main__":
    async def main():
        global _SCRIPT_SECTIONS
        _SCRIPT_SECTIONS = json.load(open(sys.argv[1]))
        # process_script_section("connection", 5.0)
        gc = GattCommander("84:17:15:2B:4E:7E")
        await gc.start_subprocess()
        await gc.connect_or_reconnect()
        sr = ScriptRunner(gc)
        for i in range(1,5):
            sr.process_script_section(f"gatt-preamble-{i}", 5.0)
        gc.disconnect_and_exit()

    asyncio.run(main())

"""



async def play_input1(fn, secs_per_line):
    with open(fn,"rt") as input_lines:
        while input_lines:
            await asyncio.sleep(secs_per_line/2)
            input_line = input_lines.readline()
            if len(input_line)<2:
                break
            print(input_line,file=sys.stderr)
            print(input_line, flush=True)
            print(input_line,file=sys.stderr)
            await asyncio.sleep(secs_per_line/2)

try:
    asyncio.run(play_input(sys.argv[1],8.0))
except Exception:
    traceback.print_exception(*sys.exc_info())
"""
