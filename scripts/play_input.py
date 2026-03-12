import asyncio
import sys
import traceback
import subprocess
import json
import select
import time
import tty

class GatttoolWrapper :
    def __init__(self):
        self.popen = subprocess.Popen(
            args=["/usr/bin/gatttool", "-I" ],
            bufsize=1,
            text=True,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT
        )

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


    def process_json_script(self, fn, read_delay=3.0):
        cmd_array = json.load(open(fn))
        for cmd in cmd_array:
            next_command = cmd.get("send", None)
            if next_command is None:
                continue
            lines_to_read = 2
            if "expect_lines" in cmd:
                lines_to_read=cmd.get("expect_lines")
            expect_text = cmd.get("expect_text", next_command)
            if "expect_text" in cmd:
                print("Expecting:", expect_text)
            print("Sending:", next_command)
            print(next_command,file=self.popen.stdin,flush=True)
            response_lines = self.read_lines(lines_to_read, read_delay, lines_to_read+5)
            if expect_text == "":
                print("\n".join(response_lines))
                continue
            else:
                if expect_text in "\n".join(response_lines):
                    print("Found:", expect_text)
                    continue
                one_more_line = self.read_lines(1,read_delay,2)
                response_lines += one_more_line
                if expect_text in "\n".join(response_lines):
                    print("Found:", expect_text)
                    continue
            print("Not found:",expect_text)
            break



if __name__ == "__main__":
    gtw = GatttoolWrapper()
    gtw.process_json_script(sys.argv[1],3.0)



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
