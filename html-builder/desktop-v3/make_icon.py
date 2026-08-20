from pathlib import Path
from PIL import Image, ImageDraw

out = Path('build')
out.mkdir(parents=True, exist_ok=True)
size = 256
img = Image.new('RGBA', (size, size), (7, 17, 31, 255))
d = ImageDraw.Draw(img)

# Rounded blue/cyan tile
for i in range(90):
    pad = 24 + i // 6
    c1 = max(20, 35 - i // 8)
    c2 = max(95, 169 - i // 3)
    c3 = 255
    d.rounded_rectangle((pad, pad, size-pad, size-pad), radius=48, fill=(c1, c2, c3, 255))

# Dark center to keep the CoderBuilder neon identity
pad = 54
d.rounded_rectangle((pad, pad, size-pad, size-pad), radius=34, fill=(8, 29, 52, 255), outline=(25, 213, 210, 255), width=8)

# Code glyph < / >
stroke = 14
color = (240, 248, 255, 255)
d.line((104, 96, 76, 128, 104, 160), fill=color, width=stroke, joint='curve')
d.line((152, 96, 180, 128, 152, 160), fill=color, width=stroke, joint='curve')
d.line((140, 88, 116, 168), fill=(25, 213, 210, 255), width=stroke)

img.save(out / 'icon.png')
img.save(out / 'icon.ico', sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
