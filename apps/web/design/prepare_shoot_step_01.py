"""Trace only the leftmost missing shoot from the unchanged approved front."""
import json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
ROOT=Path(__file__).resolve().parents[3]
DESIGN=ROOT/'apps/web/design';ASSETS=ROOT/'apps/web/src/assets'
source=Image.open(DESIGN/'bamboo-shoots-reference-20261007/01-approved-front.png').convert('RGB')
W,H=source.size
# Contour observed in a 5x diagnostic crop whose source origin is (205,875).
# The brown sheath, green tip and two small lateral blades stay one connected
# specimen. No surrounding stems, other plants, or baked wall shadow are traced.
observed=[(107,149),(125,195),(144,228),(147,259),(166,278),(177,308),(193,333),(192,312),(205,280),(218,250),(219,275),(211,329),(206,349),(234,414),(259,478),(281,552),(299,626),(314,703),(330,780),(347,868),(180,870),(178,833),(173,791),(166,748),(159,704),(157,660),(151,616),(156,583),(150,552),(138,510),(132,488),(122,460),(90,446),(59,424),(72,416),(113,432),(133,451),(133,423),(133,389),(125,356),(120,328),(113,293),(103,263),(101,239),(97,216),(99,187)]
polygon=[[205+x/5,875+y/5] for x,y in observed]
seams=[[(133,451),(164,433),(194,376),(212,329)],[(155,609),(191,581),(236,533),(280,552)],[(178,778),(221,748),(267,686),(298,625)],[(189,852),(244,813),(329,779)]]
seams=[[[205+x/5,875+y/5] for x,y in curve] for curve in seams]
crop=[214,902,280,H];mask=Image.new('L',(crop[2]-crop[0],H-crop[1]),0)
draw=ImageDraw.Draw(mask);draw.polygon([(x-crop[0],y-crop[1]) for x,y in polygon],fill=255)
mask=mask.filter(ImageFilter.GaussianBlur(.22))
texture=source.crop(crop).convert('RGBA');texture.putalpha(mask)
texture.save(ASSETS/'shoot-step-01-approved.png')
spec={'source_size':[W,H],'approved_front':'bamboo-shoots-reference-20261007/01-approved-front.png','crop':crop,'polygon':polygon,'seams':seams,'world_offset':[0,0],'wall_anchor':-.006,'shell_thickness':.003,'stage':'One leftmost shoot only; existing plants unchanged; await user acceptance'}
(DESIGN/'shoot-step-01-contours.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
print('Prepared one leftmost bamboo shoot',crop)
