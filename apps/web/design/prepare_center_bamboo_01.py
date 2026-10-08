"""First missing lower-centre bamboo; approved front is the xy/UV authority.

Only this new specimen is written. Accepted plants and their textures remain
unchanged. The traced white broad leaves crossing the cane belong to existing
plants and are excluded from this bamboo silhouette.
"""
import json, math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT=Path(__file__).resolve().parents[3]
DESIGN=ROOT/'apps/web/design'; ASSETS=ROOT/'apps/web/src/assets'
source=Image.open(DESIGN/'bamboo-shoots-reference-20261007/01-approved-front.png').convert('RGB')
W,H=source.size
# Independently traced in the 4x inspection crop at (680,650). Four unequal
# clusters retain their own folds and spacing, rather than copied leaf fans.
fans=[
 ('Top',[
  [(186,294),(159,280),(133,242),(104,197),(76,145),(30,59),(72,94),(112,146),(148,197),(173,248)],
  [(192,297),(202,260),(234,211),(276,155),(333,91),(317,130),(283,182),(246,233),(217,275)],
  [(181,322),(143,311),(98,291),(43,256),(-4,226),(39,237),(90,252),(145,279),(177,303)],
  [(294,310),(315,279),(352,250),(393,225),(435,200),(413,232),(379,263),(340,289),(315,311)],
  [(303,322),(330,297),(355,301),(391,318),(431,343),(465,367),(425,358),(386,345),(349,330),(324,327)],
  [(312,336),(341,365),(359,408),(380,461),(416,566),(389,529),(365,480),(345,431),(328,387)],
  [(297,320),(279,341),(264,367),(236,398),(247,368),(263,338),(281,315)],
  [(205,322),(198,286),(196,232),(194,138),(202,182),(207,230),(213,277)],
  [(222,385),(242,366),(254,339),(252,303),(244,315),(238,343),(233,370)],
  [(210,425),(188,408),(150,400),(116,407),(150,410),(179,419),(209,444)],
 ]),
 ('Middle left',[
  [(228,604),(202,589),(156,580),(108,577),(56,581),(18,589),(62,593),(112,600),(162,616),(194,623)],
  [(219,601),(194,569),(164,541),(128,508),(109,501),(126,528),(151,552),(182,579)],
  [(234,620),(205,619),(177,631),(142,654),(114,681),(144,668),(176,652),(204,641)],
  [(237,625),(213,633),(187,658),(153,697),(127,724),(147,694),(178,663),(208,642)],
  [(211,611),(180,608),(156,615),(135,628),(160,624),(184,619)],
 ]),
 ('Middle right',[
  [(354,710),(374,695),(412,677),(461,661),(508,649),(531,651),(502,666),(458,686),(411,706),(377,716)],
  [(351,729),(383,728),(414,745),(449,771),(480,797),(517,835),(489,816),(451,790),(413,765),(379,747)],
  [(350,735),(359,763),(371,811),(381,858),(398,950),(389,919),(379,876),(365,825),(350,780),(342,747)],
  [(353,722),(359,687),(373,654),(391,630),(377,664),(369,694),(366,724)],
  [(342,734),(328,763),(323,799),(319,817),(317,793),(322,758),(333,733)],
  # The broad pale blade pointing down-left across this fan belongs to the
  # foreground broadleaf plant. It is intentionally not exported a second time.
 ]),
 ('Bottom left',[
  [(191,1284),(172,1264),(133,1251),(90,1244),(45,1241),(15,1233),(43,1229),(85,1233),(132,1244),(172,1262)],
  [(192,1290),(166,1280),(130,1283),(91,1296),(60,1314),(20,1345),(50,1330),(90,1315),(131,1300),(170,1294)],
  [(199,1299),(178,1310),(154,1338),(128,1371),(92,1414),(108,1380),(137,1342),(163,1312),(183,1298)],
  [(207,1307),(207,1342),(204,1384),(207,1434),(206,1495),(196,1469),(190,1423),(190,1379),(198,1328)],
  [(197,1284),(163,1266),(126,1261),(93,1265),(55,1278),(83,1276),(122,1274),(161,1282)],
 ])
]
parts=[]
for cluster,polygons in fans:
 for number,polygon in enumerate(polygons,1):
  parts.append({'name':f'Centre short bamboo {cluster} leaf {number:02}',
                'polygon':[[680+x/4,650+y/4] for x,y in polygon]})

def sample(points,widths,count=32):
 result=[]
 for i in range(len(points)-1):
  p1=points[i];p2=points[i+1];p0=points[i-1] if i else [2*a-b for a,b in zip(p1,p2)]
  p3=points[i+2] if i+2<len(points) else [2*b-a for a,b in zip(p1,p2)]
  for j in range(count):
   t=j/count
   q=[.5*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t) for a,b,c,d in zip(p0,p1,p2,p3)]
   result.append([*q,widths[i]*(1-t)+widths[i+1]*t])
 result.append([*points[-1],widths[-1]]);return result

stem=[(728,720),(734,761),(747,813),(758,863),(769,931),(778,985),(782,1055)]
curves=[{'name':'Centre short bamboo continuous tapered cane','points':sample(stem,[.65,1.2,1.8,2.05,2.25,2.35,2.5],96),'stem':True}]
for name,points,widths in [
 ('Top connected fork',[(734,761),(737,746),(744,729),(754,728),(759,733)],[.8,.8,.65,.5,.3]),
 ('Middle left branch',[(747,813),(740,804),(735,805),(731,808)],[1.0,.85,.6,.3]),
 ('Middle right bowed branch',[(749,826),(757,824),(766,829),(770,834)],[1.0,.8,.6,.3]),
 ('Bottom left bowed branch',[(776,972),(761,968),(742,969),(729,973),(727,978)],[1.0,.8,.65,.45,.3]),
]:curves.append({'name':name,'points':sample(points,widths),'stem':False,'parent_curve':0})

# Each leaf root joins the nearest point of the actual cane/twig curves. These
# short petioles are rebuilt from the same coordinates, never left at stale
# positions when the cane shape changes.
attachment_curves=curves[:]
for part in parts:
 x,y=part['polygon'][0]
 parent,nearest=min(((number,q) for number,curve in enumerate(attachment_curves) for q in curve['points']),key=lambda item:(item[1][0]-x)**2+(item[1][1]-y)**2)
 if math.hypot(nearest[0]-x,nearest[1]-y)>.7:
  curves.append({'name':part['name']+' attached petiole','points':sample([(nearest[0],nearest[1]),(x,y)],[.45,.30],12),'stem':False,'parent_curve':parent,'leaf_root':True})

crop=[675,660,816,H];nodes=[761,813,985]
mask=Image.new('L',(crop[2]-crop[0],H-crop[1]),0);draw=ImageDraw.Draw(mask)
for part in parts:draw.polygon([(x-crop[0],y-crop[1]) for x,y in part['polygon']],fill=255)
for curve in curves:
 for x,y,r in curve['points']:draw.ellipse((x-crop[0]-r,y-crop[1]-r,x-crop[0]+r,y-crop[1]+r),fill=255)
texture=source.crop(crop).convert('RGBA');texture.putalpha(mask.filter(ImageFilter.GaussianBlur(.20)))
# Cane fibres are taken from this same specimen. The white foreground leaves
# at y835..948 are not bamboo texture: use clean adjacent internode fibres in
# its dedicated opaque atlas strip so the cane stays intact behind the leaves.
strip_width=32;atlas=Image.new('RGBA',(texture.width+strip_width,texture.height),(0,0,0,0));atlas.paste(texture,(0,0))
centers=curves[0]['points']
def cane_at(y):
 for a,b in zip(centers,centers[1:]):
  if a[1]<=y<=b[1]:
   t=(y-a[1])/max(1e-9,b[1]-a[1]);dx,dy=b[0]-a[0],b[1]-a[1];length=max(1e-9,math.hypot(dx,dy))
   return a[0]*(1-t)+b[0]*t,a[2]*(1-t)+b[2]*t,-dy/length,dx/length
 return centers[-1][0],centers[-1][2],-1,0
def source_colour(x,y):
 # Subpixel sampling keeps narrow original cane fibres smooth. Nearest-pixel
 # reads expanded four source pixels into visible rectangular colour stripes.
 x=max(0,min(W-1.001,x));y=max(0,min(H-1.001,y));ix,iy=int(x),int(y);tx,ty=x-ix,y-iy
 a=source.getpixel((ix,iy));b=source.getpixel((ix+1,iy));c=source.getpixel((ix,iy+1));d=source.getpixel((ix+1,iy+1))
 return tuple(round((a[k]*(1-tx)+b[k]*tx)*(1-ty)+(c[k]*(1-tx)+d[k]*tx)*ty) for k in range(3))
for row in range(texture.height):
 y=row+crop[1];sy=float(y)
 if 833<=y<=955:sy=960+(y-833)%20
 x,r,nx,ny=cane_at(sy);collar=max(math.exp(-((sy-n)/1.2)**2) for n in nodes)
 for column in range(strip_width):
  u=(column/(strip_width-1)*2-1)*.92
  px=x+nx*r*u*(1+.30*collar);py=sy+ny*r*u
  atlas.putpixel((texture.width+column,row),(*source_colour(px,py),255))
atlas.save(ASSETS/'center-bamboo-01-approved.png')
spec={'approved_front':'bamboo-shoots-reference-20261007/01-approved-front.png','source_size':[W,H],
 'crop':crop,'world_width':14.222222,'world_height':8,'world_offset':[0,0],'parts':parts,'curves':curves,
 'node_y':nodes,'wall_anchor':-.006,'shell_thickness':.003,
 'stage':'Only first missing lower-centre bamboo; wait for acceptance before proceeding rightward',
 'cane_atlas':{'left':texture.width,'width':strip_width,'texture_width':atlas.width},
 'root_clearance':{'start_y':1000,'end_y':H,'x_pixels':0,'depth_factor':.65},
 # Query the actual accepted oval-leaf triangles instead of pressing an entire
 # y band down. The new cane and its joints share the same sampled clearance.
 'occlusion_clearance':{'mesh':'bottom-layer-without-fern.traced.json','min_y':955,'max_y':1008,'gap':.003},
 'node_profile':{'lip_width':.30,'lip_height':.012,'groove_height':.003}}
(DESIGN/'center-bamboo-01-contours.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
print('Prepared first centre bamboo',len(parts),'individual leaves',len(curves),'continuous curves')
