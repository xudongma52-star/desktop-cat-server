"""Right shoot beside the tall pair; independently trace the confirmed front."""
import json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
ROOT=Path(__file__).resolve().parents[3];DESIGN=ROOT/'apps/web/design';ASSETS=ROOT/'apps/web/src/assets'
source=Image.open(DESIGN/'bamboo-shoots-reference-20261007/01-approved-front.png').convert('RGB');W,H=source.size
# Coordinates observed in a 5x source crop at (358,875). Only the connected
# sheath, pointed green growth and short side blade are included; no wall shadow.
observed=[(80,157),(105,196),(117,221),(125,248),(139,278),(141,261),(151,239),(153,231),(155,269),(165,291),(178,323),(187,342),(190,335),(190,300),(193,280),(199,283),(202,318),(200,347),(199,368),(197,390),(204,410),(215,433),(223,459),(235,491),(252,536),(258,550),(265,551),(268,547),(270,552),(267,577),(267,596),(277,634),(290,681),(300,727),(307,741),(311,737),(312,750),(312,777),(314,805),(314,843),(317,870),(165,870),(165,836),(161,798),(160,755),(158,711),(155,676),(149,639),(145,595),(140,550),(135,504),(133,484),(130,472),(115,456),(90,443),(67,428),(72,420),(94,432),(117,438),(135,451),(135,430),(132,402),(129,377),(119,349),(112,325),(100,302),(89,280),(90,272),(103,289),(111,290),(108,273),(100,253),(95,230),(95,211),(88,193),(80,164)]
polygon=[[358+x/5,875+y/5] for x,y in observed]
seams=[[(133,478),(159,449),(179,405),(196,372)],[(145,591),(178,557),(204,510),(221,461)],[(159,622),(207,649),(240,590),(268,551)],[(166,789),(208,829),(255,789),(310,740)]]
seams=[[[358+x/5,875+y/5] for x,y in line] for line in seams]
crop=[370,904,424,H];mask=Image.new('L',(crop[2]-crop[0],H-crop[1]),0)
draw=ImageDraw.Draw(mask);draw.polygon([(x-crop[0],y-crop[1]) for x,y in polygon],fill=255)
texture=source.crop(crop).convert('RGBA');texture.putalpha(mask.filter(ImageFilter.GaussianBlur(.22)))
texture.save(ASSETS/'shoot-step-02-approved.png')
# The existing five-leaf branch occupies more space than in the proposal.
# Preserve it and both canes; shift only this new shoot 30 source pixels left.
spec={'source_size':[W,H],'approved_front':'bamboo-shoots-reference-20261007/01-approved-front.png','crop':crop,'polygon':polygon,'seams':seams,'world_offset':[-30/W*14.222222,0],'wall_anchor':-.006,'shell_thickness':.003,'stage':'One right-side shoot only; existing plants preserved; await user acceptance'}
(DESIGN/'shoot-step-02-contours.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
print('Prepared right-side shoot',crop,'placement correction -30 source pixels')
