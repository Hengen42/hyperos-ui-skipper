"""Check the packaged legacy Xposed entry and ensure stubs stay compile-only."""
import re
import struct
import sys
import zipfile


def defined_classes(data):
    def uint(offset):
        return struct.unpack_from('<I', data, offset)[0]

    strings_size, strings_offset = uint(56), uint(60)
    strings = []
    for index in range(strings_size):
        offset = uint(strings_offset + index * 4)
        # Skip the ULEB128 UTF-16 length; descriptors are ASCII.
        while data[offset] & 128:
            offset += 1
        offset += 1
        strings.append(data[offset:data.index(b'\0', offset)].decode('utf-8', errors='replace'))
    types_offset = uint(68)
    for index in range(uint(96)):
        class_index = uint(uint(100) + index * 32)
        yield strings[uint(types_offset + class_index * 4)]


def verify(path):
    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        scope_files = {
            'META-INF/xposed/java_init.list',
            'META-INF/xposed/module.prop',
            'META-INF/xposed/scope.list',
        }
        if {name for name in names if name.startswith('META-INF/xposed/') and not name.endswith('/')} != scope_files:
            raise ValueError('Missing or unexpected scope metadata')
        if apk.read('META-INF/xposed/java_init.list').strip():
            raise ValueError('Modern entry list must stay empty; Entry uses the legacy loader')
        properties = dict(
            line.strip().split('=', 1)
            for line in apk.read('META-INF/xposed/module.prop').decode().splitlines()
            if line.strip() and not line.lstrip().startswith(('#', '!'))
        )
        if properties != {'minApiVersion': '93', 'targetApiVersion': '93', 'staticScope': 'true'}:
            raise ValueError('Scope metadata must retain legacy API 93 and staticScope=true')
        if apk.read('META-INF/xposed/scope.list').decode().split() != ['com.miui.securitycenter']:
            raise ValueError('Scope must contain SecurityCenter only')
        if apk.read('assets/xposed_init').decode().strip() != 'dev.hengen.uiskipper.Entry':
            raise ValueError('Missing or incorrect legacy entry registration')
        classes = set()
        for name in names:
            if re.fullmatch(r'classes\d*\.dex', name):
                classes.update(defined_classes(apk.read(name)))
        if 'Ldev/hengen/uiskipper/Entry;' not in classes:
            raise ValueError('Entry class missing from DEX')
        if any(name.startswith('Lde/robv/android/xposed/') for name in classes):
            raise ValueError('Compile-time Xposed stubs were packaged in DEX')
    print(f'PASS: legacy entry, API 93, fixed SecurityCenter scope, no Xposed stub definitions: {path}')


if __name__ == '__main__':
    verify(sys.argv[1])
