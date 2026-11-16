"""Package the user-provided SVG artwork as Android launcher assets, without redrawing it."""
from pathlib import Path
import base64, io, shutil, xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[2]
DESIGN=Path(__file__).resolve().parent
SOURCE=DESIGN/'sakuya.svg'
RES=ROOT/'app/src/main/res'

root=ET.parse(SOURCE).getroot()
embedded=[]
for node in root.iter():
    if node.tag.endswith('image'):
        value=node.attrib.get('{http://www.w3.org/1999/xlink}href',node.attrib.get('href',''))
        if value.startswith('data:image/'):
            embedded.append(Image.open(io.BytesIO(base64.b64decode(value.split(',',1)[1]))).convert('RGBA'))
assert len(embedded)==1,'Expected the SVG’s one embedded character image.'
art=embedded[0]
# Preserve the whole headdress, hair and face. The lower-body artwork remains
# untouched in the source; the launcher uses a head-and-shoulders portrait crop.
bounds=art.getchannel('A').getbbox()
portrait=art.crop((bounds[0],0,bounds[2],355))
portrait=portrait.resize((250,round(355*250/portrait.width)),Image.Resampling.LANCZOS)
foreground=Image.new('RGBA',(432,432))
foreground.alpha_composite(portrait,((432-portrait.width)//2,80))
(RES/'drawable-nodpi').mkdir(parents=True,exist_ok=True)
foreground.save(RES/'drawable-nodpi/icon_character.png')

background=Image.new('RGBA',(432,432))
pixels=background.load()
for y in range(432):
    for x in range(432):
        t=(x+y)/862
        start=(208,198,255);end=(121,100,213)
        pixels[x,y]=tuple(round(a*(1-t)+b*t) for a,b in zip(start,end))+(255,)
composed=Image.alpha_composite(background,foreground)
# Android adaptive-icon masks reveal the central 72dp of the 108dp layers.
visible=composed.crop((72,72,360,360))
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    folder=RES/('mipmap-'+density);folder.mkdir(parents=True,exist_ok=True)
    icon=visible.copy();mask=Image.new('L',icon.size);ImageDraw.Draw(mask).rounded_rectangle((0,0,287,287),radius=64,fill=255);icon.putalpha(mask)
    icon.resize((size,size),Image.Resampling.LANCZOS).save(folder/'ic_launcher.png')

def preview(round_icon=False):
    icon=visible.resize((768,768),Image.Resampling.LANCZOS)
    mask=Image.new('L',icon.size);draw=ImageDraw.Draw(mask)
    if round_icon:draw.ellipse((0,0,767,767),fill=255)
    else:draw.rounded_rectangle((0,0,767,767),radius=170,fill=255)
    icon.putalpha(mask);return icon
preview().save(DESIGN/'icon-preview.png')
preview(True).save(DESIGN/'icon-round-preview.png')
print('Generated adaptive foreground, five legacy densities, and square/circle previews.')
