"""Publish exactly six pinned free-core release files through CurseForge Upload API."""
import hashlib
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent.parent
STATE = ROOT/'build/curseforge/upload-state.json'


def prepare():
    items = json.loads((ROOT/'tools/core-0.3.6.json').read_text(encoding='utf-8'))
    assert len(items)==6 and len({i['filename'] for i in items})==6
    directory = ROOT/'build/curseforge/files'
    directory.mkdir(parents=True,exist_ok=True)
    for item in items:
        assert item['projectID']==1633302
        name = item['filename']
        assert name.startswith('e4steam-') and name.endswith('-v0.3.6.jar') and '/' not in name and '\\' not in name
        url = 'https://github.com/Kamilhik/e4steam/releases/download/v0.3.6/'+name
        path = directory/name
        if not path.exists():
            with urllib.request.urlopen(url,timeout=120) as response:
                path.write_bytes(response.read())
        assert hashlib.sha256(path.read_bytes()).hexdigest()==item['sha256'], 'Artifact checksum mismatch'
        item['path']=path
    return items


def upload(item, token):
    boundary='e4steam'+uuid.uuid4().hex
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\nContent-Type: application/json\r\n\r\n'.encode()
          +json.dumps(item['metadata'],ensure_ascii=False).encode()
          +f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{item["filename"]}"\r\nContent-Type: application/java-archive\r\n\r\n'.encode()
          +item['path'].read_bytes()+f'\r\n--{boundary}--\r\n'.encode())
    request=urllib.request.Request('https://minecraft.curseforge.com/api/projects/1633302/upload-file',data=body,
        headers={'X-Api-Token':token,'Content-Type':'multipart/form-data; boundary='+boundary,'User-Agent':'K2-Studio-e4steam-release'})
    try:
        with urllib.request.urlopen(request,timeout=180) as response:
            result=json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError('CurseForge HTTP '+str(error.code)+'; request stopped, inspect upload journal') from None
    except (urllib.error.URLError,TimeoutError,OSError):
        raise RuntimeError('Upload outcome unknown; verify on CurseForge before retrying') from None
    assert isinstance(result.get('id'),int),'Upload response has no file ID; verify before retrying'
    return result['id']


def main():
    items=prepare()
    print('Six free 0.3.6 release assets verified',flush=True)
    if '--publish' not in sys.argv:
        return
    token=os.environ.get('CURSEFORGE_API_TOKEN','')
    assert token,'CURSEFORGE_API_TOKEN missing'
    state=json.loads(STATE.read_text()) if STATE.exists() else {}
    for item in items:
        name=item['filename']
        old=state.get(name)
        if old:
            assert old['sha256']==item['sha256']
            assert old.get('fileID'),'Earlier request needs verification before retry: '+name
            print('Previously accepted:',name,old['fileID'],flush=True)
            continue
        state[name]={'sha256':item['sha256'],'status':'request_started'}
        STATE.write_text(json.dumps(state,indent=2))
        try:
            file_id=upload(item,token)
        except Exception as error:
            state[name]['status']=str(error)
            STATE.write_text(json.dumps(state,indent=2))
            raise
        state[name].update(fileID=file_id,status='accepted')
        STATE.write_text(json.dumps(state,indent=2))
        print('Accepted:',name,'file ID:',file_id,flush=True)
    print('All six accepted; moderation may still be pending',flush=True)


if __name__=='__main__':
    try:
        main()
    except Exception as error:
        print('Stopped:',str(error),flush=True)
        sys.exit(1)
