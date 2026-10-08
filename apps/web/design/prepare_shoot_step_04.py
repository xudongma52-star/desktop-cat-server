"""Right shorter shoot of the confirmed pair, independently traced."""
import json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
ROOT=Path(__file__).resolve().parents[3];DESIGN=ROOT/'apps/web/design';ASSETS=ROOT/'apps/web/src/assets'
source=Image.open(DESIGN/'bamboo-shoots-reference-20261007/01-approved-front.png').convert('RGB');W,H=source.size
# Observed 6x crop at (600,910). Trace its upright thin growth and three sheath
# lips independently, rather than scaling the accepted taller shoot.
observed=[(173,86),(174,100),(171,122),(170,146),(171,169),(172,185),(169,207),(172,226),(172,252),(176,271),(178,290),(187,283),(208,274),(228,265),(238,265),(224,278),(210,294),(198,309),(184,327),(177,344),(173,369),(175,392),(175,419),(178,449),(180,478),(183,506),(188,534),(198,529),(207,527),(207,534),(199,549),(192,566),(189,581),(189,602),(193,634),(195,665),(198,697),(200,724),(205,740),(213,735),(213,741),(202,758),(199,774),(202,793),(207,814),(211,834),(28,834),(32,805),(35,774),(37,744),(40,714),(39,686),(39,662),(41,637),(45,613),(53,594),(54,568),(60,543),(63,517),(65,493),(64,473),(59,458),(48,442),(33,429),(12,421),(6,415),(15,402),(34,403),(55,415),(83,431),(88,415),(89,395),(88,376),(93,355),(97,339),(96,330),(95,316),(96,310),(104,302),(113,290),(117,279),(103,273),(99,269),(94,261),(91,243),(90,224),(89,208),(89,197),(99,212),(105,233),(111,251),(120,268),(122,273),(128,262),(135,244),(138,229),(134,217),(134,194),(137,178),(145,164),(144,148),(151,127),(160,107),(170,90)]
polygon=[[600+x/6,910+y/6] for x,y in observed]
seams=[[(87,433),(113,392),(158,347),(207,292),(238,265)],[(61,574),(98,614),(145,587),(205,531)],[(43,739),(78,794),(136,781),(210,738)]]
seams=[[[600+x/6,910+y/6] for x,y in line] for line in seams]
crop=[600,923,641,H];mask=Image.new('L',(crop[2]-crop[0],H-crop[1]),0)
ImageDraw.Draw(mask).polygon([(x-crop[0],y-crop[1]) for x,y in polygon],fill=255)
texture=source.crop(crop).convert('RGBA');texture.putalpha(mask.filter(ImageFilter.GaussianBlur(.22)))
texture.save(ASSETS/'shoot-step-04-approved.png')
spec={'source_size':[W,H],'approved_front':'bamboo-shoots-reference-20261007/01-approved-front.png','crop':crop,'polygon':polygon,'seams':seams,'growth_y':[924,1049],'world_offset':[0,0],'wall_anchor':-.006,'shell_thickness':.003,'depth_profile':{'base':.022,'growth':.072,'edge_width':2.3},'stage':'Only shorter right shoot of pair; taller left shoot preserved; await user acceptance'}
(DESIGN/'shoot-step-04-contours.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
print('Prepared shorter right shoot of pair',crop)
