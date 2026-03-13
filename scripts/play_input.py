import asyncio
import sys
import traceback
import subprocess
import json
import select
import time
import tty


def hcitool_run(args_string, timeout=None):
    return _tool_run("/usr/bin/hcitool")


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
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout
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


def process_script_section(section_name, timeout=None, only_start=False):
    global _SCRIPT_SECTIONS
    if timeout is not None:
        pass
    elif only_start is True:
        timeout = 10.0
    else:
        timeout = 1.0
    next_command = None
    output_lines = None
    for cmd in _SCRIPT_SECTIONS.get(section_name):
        try:
            next_command = cmd.get("send", None)
            if next_command is None:
                continue
            print(f"Sending '{next_command}'", flush=True, end="")
            result = gatttool_run(next_command)
            output_lines = result.stdout.split("\n")
            if "expect_lines" in cmd:
                assert len(output_lines) == cmd.get("expect_lines")
            if "expect_text" in cmd:
                expect_text = cmd.get("expect_text")
                assert expect_text in "\n".join(output_lines)
                print(f" ... found '{expect_text}'")
            else:
                print(f"\n{"\n".join(output_lines)}")
        except AssertionError:
            print(sys.exc_info()[1])
            traceback.print_tb(sys.exc_info()[2])
            print("\n".join(["", "Output:"] + output_lines))
            break


if __name__ == "__main__":
    global _SCRIPT_SECTIONS
    _SCRIPT_SECTIONS = json.load(open(sys.argv[1]))
    # process_script_section("connection", 5.0)
    process_script_section("gatt-preamble-1", 5.0)

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
