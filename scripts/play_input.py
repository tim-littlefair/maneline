import asyncio
import sys
import traceback

async def play_input(fn, secs_per_line):
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
