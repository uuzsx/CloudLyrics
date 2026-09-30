"""Check the six distributable JARs, including MC restrictions and Java bytecode levels."""
import hashlib
import json
from pathlib import Path
import re
import struct
import tomllib
import zipfile

root = Path(__file__).resolve().parents[1]
targets = json.loads((root / 'gradle/targets.json').read_text(encoding='utf-8'))
version = re.search(r"^version = '([^']+)'", (root / 'build.gradle').read_text(), re.M)[1]
expected_reader = (root / 'src/main/resources/cloudlyrics/reader.js').read_bytes()
for mc, target in targets.items():
    jar = root / f'build/{mc}/libs/cloudlyrics-{mc}-neoforge-{version}.jar'
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        metadata = tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode('utf-8'))
        assert metadata['mods'][0]['modId'] == 'cloudlyrics', jar
        assert metadata['mods'][0]['version'] == version, jar
        deps = {d['modId']: d for d in metadata['dependencies']['cloudlyrics']}
        assert deps['minecraft']['versionRange'] == f'[{mc}]', jar
        assert deps['neoforge']['versionRange'] == target['neoRange'], jar
        assert all(d['side'] == 'CLIENT' for d in deps.values()), jar
        assert z.read('cloudlyrics/reader.js') == expected_reader, jar
        assert names.count('dev/cloudlyrics/ClientPlatform.class') == 1, jar
        assert not any(n.lower().endswith(('.cmd', '.ps1', '.exe')) for n in names), jar
        assert not any(n.startswith(('net/minecraft/', 'net/neoforged/', 'config/', 'logs/')) for n in names), jar
        for name in names:
            if name.endswith('.class'):
                data = z.read(name)
                assert data == (root / f'build/{mc}/classes/java/main' / name).read_bytes(), (jar, name, 'stale compiled class')
                assert data[:4] == b'\xca\xfe\xba\xbe', (jar, name)
                assert struct.unpack('>H', data[6:8])[0] == target['java'] + 44, (jar, name)
    print(f'PASS: Minecraft {mc}, NeoForge {target["neoRange"]}, Java {target["java"]}; '
          f'SHA256={hashlib.sha256(jar.read_bytes()).hexdigest()}')
print(f'PASS: all {len(targets)} distributable JARs')
