#! python3

# parse_captures.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)
# COPYRIGHT: 
# To the extent possible, the intent of the author Tim Littlefair 
# is that this script should become part of the public domain.


import enum
import subprocess

# Every message will arrive wrapped in a frame of type bytes, where
# frame[0] gives an encoding type and the length of the message
# is encoded as a varint either in frame[1] only (length < 128)
# or frame[1:2] (128 <= length <= 16383).  In theory the varint
# could expand to cover more than 2 bytes, but we do not expect
# to find messages long enough to need this. The message length
# is measured from the index of the byte after the length varint.

# Constants from the different types of encoding which have been seen
class EncodingType(enum.Enum):
    FIXED = 0x0a,
    PROTOBUF = 0x12,
    TLV_ARRAY = 0x26,

class MessageType(enum.StrEnum):
    NAME_LIST = "080262",

# Captures of Bluetooth traffic between Android Fender Tone and Mustang Micro Plus
# shows that there are some cases where the framing layer which reassembles
# packets into whole messages delivers a frame which is shorter than the
# message length read from the header at the start of the message.
# When this happens, the exception below will be raised, and the caller
# will need back out of the logic to convert the frame to a message
# and continue to append more packets until another frame-closing packet is
# received (at which time a fresh attempt to convert the frame to a message
# will be made).
class IncompleteFrameException(Exception):
    def __init__(self, incomplete_frame_bytes, expected_length, ):
        available_length = len(incomplete_frame_bytes)
        exception_message = (
            f"frame contains incomplete message {available_length} out of {expected_length} bytes"
        )
        self.incomplete_frame_bytes = incomplete_frame_bytes
        super(IncompleteFrameException,self).__init__(exception_message)

def parse_message_frame(frame_bytes):
    assert isinstance(frame_bytes,bytes)
    # The Mustang message format appears to consist of:
    # + constant tag 0x12; followed by
    # + the message length expressed as a varint; followed by
    # + the protobuf of the message
    #assert message_bytes[0] == 0x12
    message_length, message_bytes = _extract_varint(frame_bytes[1:])
    if len(message_bytes) != message_length:
        raise IncompleteFrameException(frame_bytes,message_length)
    raw_pb_parse = _run_protoc_with_args(message_bytes,[ "--decode_raw" ] )
    return raw_pb_parse, message_bytes

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



## Unit tests
if __name__ == "__main__":
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


