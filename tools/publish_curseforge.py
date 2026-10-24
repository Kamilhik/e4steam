"""Publish the pinned core release before its addon through the official Upload API."""
from pathlib import Path
import hashlib, json, os, re, sys, urllib.request, urllib.error, uuid

ROOT = Path(__file__).resolve().parent.parent
STATE = ROOT / 'build/curseforge/upload-state.json'
MANIFEST = ROOT / 'tools/curseforge-release.json'


def prepare():
    items = json.loads(MANIFEST.read_text(encoding='utf-8'))
    assert len(items) == 13
    assert all(i['kind'] == 'core' and i['projectID'] == '1633302' for i in items[:6])
    assert all(i['kind'] == 'addon' and i['projectID'] == '1764196' for i in items[6:])
    destination = ROOT / 'build/curseforge/files'
    destination.mkdir(parents=True, exist_ok=True)
    for item in items:
        name = item['filename']
        assert re.fullmatch(r'[a-zA-Z0-9._-]+[.]jar', name)
        repo, tag = ('Kamilhik/e4steam', 'v0.3.4') if item['kind'] == 'core' else ('K2-Studio-Development/e4steam-Public-Servers', 'v0.1.0')
        expected = 'https://github.com/' + repo + '/releases/download/' + tag + '/' + name
        assert item['downloadURL'] == expected
        path = destination / name
        if not path.exists():
            with urllib.request.urlopen(expected, timeout=120) as response:
                path.write_bytes(response.read())
        assert hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256'], 'Hash mismatch: ' + name
        assert item['metadata']['releaseType'] == 'release'
        if item['kind'] == 'addon':
            assert {'slug': 'e4steam', 'projectID': '1633302', 'type': 'requiredDependency'} in item['metadata']['relations']['projects']
        item['path'] = str(path)
    return items


def upload(item, token):
    path = Path(item['path'])
    assert hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256']
    boundary = 'K2Studio' + uuid.uuid4().hex
    body = ('--' + boundary + '\r\nContent-Disposition: form-data; name="metadata"\r\n'
            'Content-Type: application/json\r\n\r\n').encode() + json.dumps(item['metadata']).encode()
    body += ('\r\n--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' +
             path.name + '"\r\nContent-Type: application/java-archive\r\n\r\n').encode()
    body += path.read_bytes() + ('\r\n--' + boundary + '--\r\n').encode()
    url = 'https://minecraft.curseforge.com/api/projects/' + item['projectID'] + '/upload-file'
    req = urllib.request.Request(url, data=body, method='POST', headers={
        'X-Api-Token': token, 'Content-Type': 'multipart/form-data; boundary=' + boundary,
        'Accept': 'application/json', 'User-Agent': 'K2-Studio-release-publisher'})
    try:
        with urllib.request.urlopen(req, timeout=180) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        text = error.read().decode('utf-8', errors='replace')
        category = 'Cloudflare security challenge' if 'Just a moment' in text or 'cf-chl-' in text else 'API rejection'
        raise RuntimeError('CurseForge HTTP ' + str(error.code) + ': ' + category) from None
    assert isinstance(result, dict) and isinstance(result.get('id'), int), 'Upload result has no file ID'
    return result['id']


def main():
    items = prepare()
    print('All 13 GitHub release assets downloaded and SHA-256 verified.', flush=True)
    if '--publish' not in sys.argv:
        print('Preparation only: no CurseForge requests sent.')
        return
    token = os.environ.get('CURSEFORGE_API_TOKEN', '')
    assert token, 'CURSEFORGE_API_TOKEN is missing'
    state = json.loads(STATE.read_text(encoding='utf-8')) if STATE.exists() else {}
    for item in items:
        name = item['filename']
        previous = state.get(name)
        if previous:
            assert previous['sha256'] == item['sha256'] and previous['projectID'] == item['projectID']
            assert previous.get('fileID'), 'Previous request needs verification before retry: ' + name
            print('Already accepted:', name, previous['fileID'], flush=True)
            continue
        if item['kind'] == 'addon':
            assert all(state.get(i['filename'], {}).get('fileID') for i in items[:6])
        state[name] = {'sha256': item['sha256'], 'projectID': item['projectID'], 'status': 'request_started'}
        STATE.write_text(json.dumps(state, indent=2), encoding='utf-8')
        try:
            file_id = upload(item, token)
        except Exception as error:
            state[name]['status'] = str(error)
            STATE.write_text(json.dumps(state, indent=2), encoding='utf-8')
            raise
        state[name].update(fileID=file_id, status='accepted')
        STATE.write_text(json.dumps(state, indent=2), encoding='utf-8')
        print('Accepted:', name, 'file ID:', file_id, flush=True)
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a', encoding='utf-8') as output:
            output.write('# CurseForge uploads\n\nAll six core files were accepted before the seven addon files.\n\n| File | CurseForge ID |\n| --- | --- |\n')
            for item in items:
                output.write('| ' + item['filename'] + ' | ' + str(state[item['filename']]['fileID']) + ' |\n')
    print('All 13 files accepted. CurseForge moderation can still be pending.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Stopped:', str(error), flush=True)
        sys.exit(1)
