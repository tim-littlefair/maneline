#! python3

# hexdump.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)
# COPYRIGHT: 
# To the extent possible, the intent of the author Tim Littlefair 
# is that this script should become part of the public domain.

# Utility to dump binary data in a format comparable to 'hexdump -C'.
# TODO: Re-implement so that Windows users can run scripts that use this.

import subprocess

def hexdump(bytes_to_dump):
    cli_args = [ "hexdump", "-C", ]
    completed_process = subprocess.run(
        args = cli_args,
        input = bytes_to_dump,
        capture_output = True,
        timeout = 10
    )
    assert completed_process.returncode==0
    assert len(completed_process.stderr)==0
    return str(completed_process.stdout, "UTF-8")

