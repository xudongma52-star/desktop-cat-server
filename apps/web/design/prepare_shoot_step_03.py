"""Left taller shoot of the selected pair, traced from the unchanged front."""
import json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
ROOT=Path(__file__).resolve().parents[3];DESIGN=ROOT/'apps/web/design';ASSETS=ROOT/'apps/web/src/assets'
source=Image.open(DESIGN/'bamboo-shoots-reference-20261007/01-approved-front.png').convert('RGB');W,H=source.size
# Independently observed 5x diagnostic crop at (515,840). Keep the pointed
# growth, four projecting sheath lips and tapered body continuously connected.
observed=[(109,112),(124,135),(135,155),(144,174),(144,157),(148,142),(151,138),(159,175),(169,205),(178,233),(178,216),(173,191),(171,174),(181,208),(191,238),(203,276),(209,303),(210,318),(217,305),(224,292),(229,274),(241,247),(247,242),(245,263),(241,289),(232,317),(223,345),(218,372),(229,395),(241,424),(249,451),(267,478),(276,508),(285,540),(295,576),(306,569),(310,553),(314,546),(319,548),(314,576),(312,597),(312,621),(320,654),(332,697),(342,741),(351,781),(354,776),(360,756),(367,745),(371,748),(365,779),(362,794),(363,826),(370,863),(376,902),(382,943),(386,981),(392,1045),(143,1045),(146,1018),(155,989),(161,960),(168,932),(173,906),(177,875),(180,842),(174,807),(170,779),(165,742),(165,724),(156,708),(145,682),(136,657),(129,641),(131,632),(142,652),(158,670),(163,681),(161,660),(156,630),(156,605),(157,573),(153,545),(153,516),(154,500),(152,487),(139,476),(120,464),(101,455),(88,450),(88,437),(100,435),(121,440),(145,450),(157,455),(157,429),(151,402),(145,374),(144,355),(143,343),(141,323),(132,310),(123,299),(119,286),(110,271),(101,255),(100,247),(113,258),(126,272),(130,274),(130,261),(124,244),(122,230),(118,214),(115,196),(113,181),(112,164),(111,145)]
polygon=[[515+x/5,840+y/5] for x,y in observed]
seams=[[(157,455),(188,416),(223,345),(245,246)],[(154,500),(210,532),(269,561),(307,574)],[(166,727),(208,672),(269,603),(318,552)],[(181,948),(231,888),(304,821),(367,747)],[(174,923),(229,973),(295,1011),(360,1042)]]
seams=[[[515+x/5,840+y/5] for x,y in line] for line in seams]
crop=[531,860,596,H];mask=Image.new('L',(crop[2]-crop[0],H-crop[1]),0)
ImageDraw.Draw(mask).polygon([(x-crop[0],y-crop[1]) for x,y in polygon],fill=255)
texture=source.crop(crop).convert('RGBA');texture.putalpha(mask.filter(ImageFilter.GaussianBlur(.22)))
texture.save(ASSETS/'shoot-step-03-approved.png')
spec={'source_size':[W,H],'approved_front':'bamboo-shoots-reference-20261007/01-approved-front.png','crop':crop,'polygon':polygon,'seams':seams,'growth_y':[862,1049],'world_offset':[0,0],'wall_anchor':-.006,'shell_thickness':.003,'stage':'Only the taller left shoot of selected pair; await acceptance before shorter right shoot'}
(DESIGN/'shoot-step-03-contours.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
print('Prepared taller left shoot of pair',crop)
