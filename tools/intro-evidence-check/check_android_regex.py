"""Check production regex constants against ICU4C, the Android Pattern backend.

JDK Pattern accepts an unescaped literal right brace; Android's ICU rejects it.
The original regression fixture below must fail, while production patterns and
subtitle markup removal must succeed. This is a host ICU check, not a TV test.
"""
import ast
import ctypes as c
import ctypes.util
from pathlib import Path
import re


class ParseError(c.Structure):
    _fields_ = [('line', c.c_int32), ('offset', c.c_int32),
                ('before', c.c_uint16 * 16), ('after', c.c_uint16 * 16)]


library_name = ctypes.util.find_library('icui18n')
assert library_name, 'ICU4C is required for the Android regex compatibility check'
library = c.CDLL(library_name)
suffix_match = re.search(r'\.so\.(\d+)', library_name)
suffix = '_' + suffix_match.group(1) if suffix_match else ''
open_regex = getattr(library, 'uregex_open' + suffix)
close_regex = getattr(library, 'uregex_close' + suffix)
set_text = getattr(library, 'uregex_setText' + suffix)
replace_all = getattr(library, 'uregex_replaceAll' + suffix)
uchar = c.POINTER(c.c_uint16)
error_ptr = c.POINTER(c.c_int32)
open_regex.argtypes = [uchar, c.c_int32, c.c_uint32, c.POINTER(ParseError), error_ptr]
open_regex.restype = c.c_void_p
close_regex.argtypes = [c.c_void_p]
set_text.argtypes = [c.c_void_p, uchar, c.c_int32, error_ptr]
replace_all.argtypes = [c.c_void_p, uchar, c.c_int32, uchar, c.c_int32, error_ptr]
replace_all.restype = c.c_int32


def utf16(text):
    data = text.encode('utf-16-le')
    return (c.c_uint16 * (len(data) // 2)).from_buffer_copy(data)


def compile_pattern(pattern):
    text = utf16(pattern)
    error, position = c.c_int32(0), ParseError()
    handle = open_regex(text, len(text), 0, c.byref(position), c.byref(error))
    return handle, error.value, position.offset


original = '<[^>]*>|\\{[^}]*}'
handle, error, _ = compile_pattern(original)
assert error > 0 and not handle, 'Regression fixture must expose the Android/JDK mismatch'

source = Path('app/src/main/java/com/nuvio/tv/core/intro/SubtitleEvidenceAnalyzer.java').read_text()
constants = re.findall(r'Pattern (\w+) = Pattern.compile\((.*?)\);', source, re.S)
assert len(constants) >= 7, 'Production regex constants were not found'
for name, expression in constants:
    pattern = ''.join(ast.literal_eval(x) for x in re.findall(r'"(?:\\.|[^"\\])*"', expression))
    handle, error, offset = compile_pattern(pattern)
    assert error <= 0 and handle, f'{name} rejected by ICU: status {error}, offset {offset}'
    try:
        if name == 'TAGS':
            text = utf16(r'{\an8}<i>[Theme song]</i>')
            status = c.c_int32(0)
            set_text(handle, text, len(text), c.byref(status))
            empty, output = (c.c_uint16 * 1)(), (c.c_uint16 * 200)()
            size = replace_all(handle, empty, 0, output, len(output), c.byref(status))
            assert status.value <= 0
            assert c.string_at(c.addressof(output), size * 2).decode('utf-16-le') == '[Theme song]'
    finally:
        close_regex(handle)
print(f'Passed ICU compilation of {len(constants)} production patterns, old-pattern rejection and markup removal.')
