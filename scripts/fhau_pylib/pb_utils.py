#! python3

# protobuf_utils.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)

import subprocess

def _run_protoc_with_args(protobuf_bytes,protoc_args):
    cli_args = [ "protoc", ]
    cli_args += protoc_args
    completed_process = subprocess.run(
        args = cli_args,
        input = protobuf_bytes,
        capture_output = True,
        timeout = 10
    )
    if completed_process.stderr:
        print("Protobuf error: " + str(completed_process.stderr,"utf-8"))
    else:
        assert completed_process.returncode==0
        assert len(completed_process.stderr)==0
    return str(completed_process.stdout, "UTF-8")

def _extract_varint(byte_stream):
    varint_value = 0
    multiplier = 1
    while True:
        next_byte = 0xFF & int(byte_stream[0])
        byte_stream = byte_stream[1:]
        varint_value += multiplier * (0x7F&next_byte)
        if next_byte>=0x80:
            multiplier*=1<<7
        else:
            break
    return varint_value, byte_stream

def parse(message_bytes):
    assert isinstance(message_bytes,bytes)
    # The Mustang message format appears to consist of:
    # + constant tag 0x12; followed by
    # + the message length expressed as a varint; followed by
    # + the protobuf of the message
    #assert message_bytes[0] == 0x12
    message_length, protobuf_bytes = _extract_varint(message_bytes[1:])
    print(message_bytes[0],message_length,len(protobuf_bytes))
    raw_pb_parse = _run_protoc_with_args(protobuf_bytes,[ "--decode_raw" ] )
    return raw_pb_parse, protobuf_bytes


## Unit tests
import pytest

def test_extract_varint_01():
    value, remaining_bytes = _extract_varint(b'\x01')
    assert value==1
    assert remaining_bytes == b''

def test_extract_varint_7f():
    value, remaining_bytes = _extract_varint(b'\x7f')
    assert value==127
    assert remaining_bytes == b''

def test_extract_varint_80():
    # Invalid input stream - if varint contains byte >= 0x80
    # that must not be the last byte
    with pytest.raises(IndexError):
        value, remaining_bytes = _extract_varint(b'\x80')

def test_extract_varint_8000():
    # valid but stupid way of encoding 0
    value, remaining_bytes = _extract_varint(b'\x80\x00')
    assert value==0
    assert remaining_bytes == b''

def test_extract_varint_8001():
    value, remaining_bytes = _extract_varint(b'\x80\x01')
    assert value==128
    assert remaining_bytes == b''

def test_extract_varint_8101():
    value, remaining_bytes = _extract_varint(b'\x81\x01')
    assert value==129
    assert remaining_bytes == b''

def test_extract_varint_ff7f():
    value, remaining_bytes = _extract_varint(b'\xff\x7f')
    assert value==16383
    assert remaining_bytes == b''

def test_extract_varint_ffff():
    # Invalid input stream - if varint contains byte >= 0x80
    # that must not be the last byte
    with pytest.raises(IndexError):
        value, remaining_bytes = _extract_varint(b'\xff\xff')

def test_extract_varint_ffff7f():
    value, remaining_bytes = _extract_varint(b'\xff\xff\x7f')
    assert value==2097151
    assert remaining_bytes == b''


