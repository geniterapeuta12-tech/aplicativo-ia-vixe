from PIL import Image, ImageDraw
from pathlib import Path

size = 256
img = Image.new('RGBA', (size, size), '#07111F')
d = ImageDraw.Draw(img)

# Soft blue frame / C-like developer mark
outer = [(48,48),(96,24),(174,24),(210,54),(210,84),(184,84),(165,62),(100,62),(74,78),(62,128),(74,178),(100,194),(165,194),(184,172),(210,172),(210,202),(174,232),(96,232),(48,208),(24,176),(24,80)]
d.polygon(outer, fill='#1479FF')
inner = [(68,76),(104,56),(160,56),(178,70),(158,86),(140,82),(106,82),(90,92),(82,128),(90,164),(106,174),(140,174),(158,170),(178,186),(160,200),(104,200),(68,180),(54,154),(54,102)]
d.polygon(inner, fill='#07111F')

# Code glyphs
white = '#F4F8FF'
cyan = '#19D5D2'
d.polygon([(92,108),(66,128),(92,148),(101,137),(88,128),(101,119)], fill=white)
d.polygon([(164,108),(190,128),(164,148),(155,137),(168,128),(155,119)], fill=white)
d.polygon([(132,94),(119,162),(132,162),(145,94)], fill=cyan)

Path('build').mkdir(exist_ok=True)
img.save('build/icon.ico', format='ICO', sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
img.save('build/icon.png')
print('CoderBuilder icon generated')
