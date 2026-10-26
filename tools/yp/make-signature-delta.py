"""Build a public APK reconstruction patch; accepts APKs, never a private key."""
from pathlib import Path
import json,base64,zipfile,struct,hashlib,sys
assert len(sys.argv) == 4, 'Usage: make-signature-delta.py input.apk signed.apk delta.json'
ap=Path(sys.argv[1]);bp=Path(sys.argv[2]);a=ap.read_bytes();b=bp.read_bytes();ops=[]
def literal(data):
 if data:ops.append({'data':base64.b64encode(data).decode()})
def copy(offset,size):
 if ops and 'copy' in ops[-1] and sum(ops[-1]['copy'])==offset:ops[-1]['copy'][1]+=size
 else:ops.append({'copy':[offset,size]})
def body(buf,info):
 n,x=struct.unpack_from('<HH',buf,info.header_offset+26);return info.header_offset+30+n+x
with zipfile.ZipFile(ap) as za,zipfile.ZipFile(bp) as zb:
 old={x.filename:x for x in za.infolist()};cursor=0
 for y in sorted(zb.infolist(),key=lambda x:x.header_offset):
  x=old.get(y.filename)
  if x is None or x.compress_size!=y.compress_size:continue
  ax=body(a,x);by=body(b,y)
  if a[ax:ax+x.compress_size]!=b[by:by+y.compress_size]:continue
  start,off=by,ax
  if a[x.header_offset:ax]==b[y.header_offset:by]:start,off=y.header_offset,x.header_offset
  literal(b[cursor:start]);copy(off,by+y.compress_size-start);cursor=by+y.compress_size
 literal(b[cursor:])
patch={'input_sha256':hashlib.sha256(a).hexdigest(),'output_sha256':hashlib.sha256(b).hexdigest(),'output_size':len(b),'ops':ops}
p=Path(sys.argv[3]);p.write_text(json.dumps(patch,separators=(',',':'))+'\n')
out=bytearray()
for op in ops:
 if 'copy' in op:o,n=op['copy'];out+=a[o:o+n]
 else:out+=base64.b64decode(op['data'])
assert out==b
print('Patch bytes:',p.stat().st_size,'operations:',len(ops),'APK:',len(b),'input:',patch['input_sha256'],'output:',patch['output_sha256'])
