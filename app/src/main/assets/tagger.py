"""Structured local protocol; Mutagen 1.47.0. Never executes supplied text."""
import json, sys
import mutagen
from mutagen.id3 import ID3, TIT2,TPE1,TALB,TPE2,TDRC,TRCK,TPOS,TCON,TCOM,WOAS,COMM,TXXX
from mutagen.mp4 import MP4, MP4FreeForm
from mutagen.wave import WAVE

frames={'title':TIT2,'artist':TPE1,'album':TALB,'album_artist':TPE2,'date':TDRC,'track':TRCK,'disc':TPOS,'genre':TCON,'composer':TCOM}
atoms={'title':'\xa9nam','artist':'\xa9ART','album':'\xa9alb','album_artist':'aART','date':'\xa9day','track':'trkn','disc':'disk','genre':'\xa9gen','composer':'\xa9wrt','comment':'\xa9cmt'}
keys=list(frames)+['purl','comment']
aliases={'albumartist':'album_artist','tracknumber':'track','discnumber':'disc','year':'date','description':'comment','url':'purl','source':'purl'}
rgkeys=['replaygain_track_gain','replaygain_track_peak','replaygain_album_gain','replaygain_album_peak','r128_track_gain','r128_album_gain','itunnorm']
def canon(key): return aliases.get(key.lower(),key.lower())
def read(audio):
    tags=audio.tags
    if tags is None: return {}
    out={}
    if isinstance(tags,ID3):
        for key,cls in frames.items():
            values=tags.getall(cls.__name__)
            if values: out[key]=', '.join(str(v) for v in values[0].text)
        urls=tags.getall('WOAS')
        if urls: out['purl']=urls[0].url
        comments=tags.getall('COMM')
        if comments: out['comment']='\n'.join(str(v) for v in comments[0].text)
        for v in tags.getall('TXXX'):
            if canon(v.desc) in keys+rgkeys: out[canon(v.desc)]=', '.join(str(x) for x in v.text)
    elif isinstance(audio,MP4):
        for key,atom in atoms.items():
            if atom in tags:
                value=tags[atom][0]
                out[key]=str(value[0]) if key in ('track','disc') else str(value)
        for key in rgkeys:
            atom='----:com.apple.iTunes:'+key
            if atom in tags: out[key]=bytes(tags[atom][0]).decode('utf-8')
    else:
        for key,values in tags.items():
            k=canon(key)
            if k in keys+rgkeys: out[k]=', '.join(str(x) for x in values)
    return out

def write(audio,values,rg):
    if audio.tags is None: audio.add_tags()
    tags=audio.tags
    if isinstance(tags,ID3):
        for key,cls in frames.items(): tags.delall(cls.__name__)
        tags.delall('WOAS'); tags.delall('COMM'); tags.delall('RVA2')
        for frame in list(tags.getall('TXXX')):
            if canon(frame.desc) in keys+rgkeys: del tags[frame.HashKey]
        for key,value in values.items():
            if key in frames: tags.add(frames[key](encoding=3,text=[value]))
            elif key=='purl': tags.add(WOAS(url=value))
            elif key=='comment': tags.add(COMM(encoding=3,lang='eng',desc='',text=[value]))
        for key,value in rg.items(): tags.add(TXXX(encoding=3,desc=key.upper(),text=[value]))
    elif isinstance(audio,MP4):
        for atom in atoms.values(): tags.pop(atom,None)
        for atom in list(tags):
            if atom.startswith('----:com.apple.iTunes:') and atom.split(':',2)[-1].lower() in rgkeys: tags.pop(atom,None)
        for key,value in values.items():
            if key in ('track','disc'): tags[atoms[key]]=[(int(value.split('/')[0]),0)]
            elif key in atoms: tags[atoms[key]]=[value]
        for key,value in rg.items(): tags['----:com.apple.iTunes:'+key]=[MP4FreeForm(value.encode('utf-8'))]
    else:
        for key in list(tags):
            if canon(key) in keys+rgkeys: del tags[key]
        for key,value in dict(values,**rg).items(): tags[key]=[value]
    audio.save()

mode,path=sys.argv[1:3]
audio=mutagen.File(path)
if audio is None: raise ValueError('This container requires the FFmpeg metadata fallback')
if mode=='write':
    with open(sys.argv[3],encoding='utf-8') as f: payload=json.load(f)
    write(audio,payload['tags'],payload.get('replaygain',{}))
    audio=mutagen.File(path)
print(json.dumps(read(audio),ensure_ascii=False))
