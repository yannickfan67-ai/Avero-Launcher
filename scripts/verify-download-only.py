"""Inspect compiled DEX files and packaged native bridge names in a produced APK."""
from pathlib import Path
import sys
import zipfile
apk = Path(sys.argv[1] if len(sys.argv) > 1 else 'android/app/build/outputs/apk/debug/app-debug.apk')
forbidden = [b'Lio/yannickfan/avero/auth/', b'Lio/yannickfan/avero/game/', b'Lio/yannickfan/avero/runtime/', b'Lio/yannickfan/avero/androidnative/', b'MICROSOFT_CLIENT_ID']
with zipfile.ZipFile(apk) as archive:
    dex = [name for name in archive.namelist() if name.endswith('.dex')]
    assert dex, 'No compiled Android code found'
    for name in dex:
        data = archive.read(name)
        for marker in forbidden:
            assert marker not in data, f'Unshipped authentication/game code found in {name}: {marker!r}'
    assert not any(name.endswith(('/libavero_bridge.so', '/libjvm.so')) for name in archive.namelist()), 'Game JVM bridge was packaged'
print('PASS: compiled APK excludes authentication, game Activities, JVM bridge and Client ID config')
