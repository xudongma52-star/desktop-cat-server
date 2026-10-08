"""Second missing centre bamboo, traced independently from approved front.

Only the rear fine leaning cane and its three fans are included. The thicker
front cane, foreground ivy leaves and next shoot are separate acceptance steps.
"""
import json,math
from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
ROOT=Path(__file__).resolve().parents[3];DESIGN=ROOT/'apps/web/design';ASSETS=ROOT/'apps/web/src/assets'
source=Image.open(DESIGN/'bamboo-shoots-reference-20261007/01-approved-front.png').convert('RGB');W,H=source.size
# Individually observed in a 3x crop at (825,585). Retain the unequal narrow
# leaves and drooping blades, not the neighbouring front cane's fan at (350,603).
fans=[('Upper',[
 [(435,127),(418,111),(406,87),(394,59),(389,43),(409,62),(424,94),(439,121)],
 [(451,93),(464,72),(491,53),(531,38),(581,23),(561,41),(526,60),(489,78),(462,100)],
 [(451,101),(475,90),(501,94),(533,103),(574,119),(601,135),(570,126),(532,115),(491,106),(462,108)],
 [(456,116),(480,112),(507,132),(535,161),(571,207),(606,244),(578,222),(547,194),(517,163),(480,135)],
 [(445,127),(449,152),(459,189),(468,229),(475,285),(465,253),(454,212),(441,168)],
 [(459,122),(481,144),(497,173),(513,208),(536,255),(518,235),(501,209),(481,170),(465,143)],
 [(440,124),(430,138),(425,158),(420,173),(420,151),(428,129)],
]),('Middle right',[
 [(438,463),(448,437),(467,415),(494,393),(521,377),(500,398),(475,423),(454,450)],
 [(439,466),(466,444),(499,431),(533,425),(562,430),(590,432),(559,436),(529,442),(494,453),(460,470)],
 [(445,475),(472,465),(499,474),(524,491),(552,507),(528,498),(499,489),(471,482),(449,485)],
 [(448,481),(475,485),(503,505),(540,538),(582,579),(621,623),(595,605),(560,573),(520,538),(478,503)],
 [(445,488),(464,508),(481,542),(498,579),(526,653),(510,629),(491,592),(474,555),(454,518)],
 [(438,479),(415,489),(400,512),(382,539),(369,555),(376,529),(394,504),(421,482)],
]),('Lower left',[
 [(202,711),(179,713),(155,722),(132,734),(106,749),(130,743),(159,733),(190,724)],
 [(202,704),(185,692),(166,683),(146,676),(164,677),(185,684),(204,696)],
 [(202,721),(181,768),(165,826),(145,901),(127,977),(118,1010),(124,968),(139,894),(158,815),(179,752)],
 [(209,722),(208,753),(204,798),(200,842),(195,899),(189,872),(189,832),(195,781),(198,742)],
 [(214,725),(228,748),(238,778),(247,808),(255,838),(243,820),(229,792),(217,760)],
 [(194,726),(176,747),(160,774),(143,805),(155,772),(172,742),(188,725)],
])]
parts=[]
for cluster,polygons in fans:
 for i,polygon in enumerate(polygons,1):parts.append({'name':f'Centre second fine cane {cluster} leaf {i:02}','polygon':[[825+x/3,585+y/3] for x,y in polygon]})
def sample(points,widths,count=48):
 out=[]
 for i in range(len(points)-1):
  p1=points[i];p2=points[i+1];p0=points[i-1] if i else [2*a-b for a,b in zip(p1,p2)]
  p3=points[i+2] if i+2<len(points) else [2*b-a for a,b in zip(p1,p2)]
  for j in range(count):
   t=j/count
   q=[.5*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t) for a,b,c,d in zip(p0,p1,p2,p3)]
   out.append([*q,widths[i]*(1-t)+widths[i+1]*t])
 out.append([*points[-1],widths[-1]]);return out
stem=[(973,621),(964,647),(940,709),(926,752),(907,804),(886,858),(865,923),(854,983),(843,1055)]
curves=[{'name':'Second centre continuous fine leaning cane','points':sample(stem,[.35,.45,.7,.85,1.05,1.3,1.55,1.9,2.1],96),'stem':True}]
for name,points,widths in [
 ('Top short fork',[(971,627),(974,624),(977,618)],[.5,.45,.3]),
 ('Middle fan connected ascending branch',[(907,804),(921,792),(936,774),(952,754),(969,743)],[.8,.75,.6,.5,.3]),
 ('Lower fan joined left arch',[(901,819),(897,819),(892,821),(891,826)],[.8,.6,.5,.3]),
]:curves.append({'name':name,'points':sample(points,widths),'stem':False,'parent_curve':0})
attachment_curves=curves[:]
for part in parts:
 x,y=part['polygon'][0]
 parent,point=min(((i,q) for i,c in enumerate(attachment_curves) for q in c['points']),key=lambda pair:(pair[1][0]-x)**2+(pair[1][1]-y)**2)
 if math.hypot(point[0]-x,point[1]-y)>.7:
  curves.append({'name':part['name']+' attached petiole','points':sample([(point[0],point[1]),(x,y)],[.4,.25],12),'stem':False,'parent_curve':parent,'leaf_root':True})
crop=[833,590,1036,H];nodes=[647,804,923]
mask=Image.new('L',(crop[2]-crop[0],H-crop[1]),0);draw=ImageDraw.Draw(mask)
for part in parts:draw.polygon([(x-crop[0],y-crop[1]) for x,y in part['polygon']],fill=255)
for curve in curves:
 for x,y,half in curve['points']:draw.ellipse((x-crop[0]-half,y-crop[1]-half,x-crop[0]+half,y-crop[1]+half),fill=255)
texture=source.crop(crop).convert('RGBA');texture.putalpha(mask.filter(ImageFilter.GaussianBlur(.2)))
strip_width=32;atlas=Image.new('RGBA',(texture.width+strip_width,texture.height),(0,0,0,0));atlas.paste(texture,(0,0))
centers=curves[0]['points']
def cane_at(y):
 for a,b in zip(centers,centers[1:]):
  if a[1]<=y<=b[1]:
   t=(y-a[1])/max(1e-9,b[1]-a[1]);dx,dy=b[0]-a[0],b[1]-a[1];length=max(1e-9,math.hypot(dx,dy))
   return a[0]*(1-t)+b[0]*t,a[2]*(1-t)+b[2]*t,-dy/length,dx/length
 return centers[0][0],centers[0][2],-1,0
def source_colour(x,y):
 x=max(0,min(W-1.001,x));y=max(0,min(H-1.001,y));ix,iy=int(x),int(y);tx,ty=x-ix,y-iy
 a=source.getpixel((ix,iy));b=source.getpixel((ix+1,iy));c=source.getpixel((ix,iy+1));d=source.getpixel((ix+1,iy+1))
 return tuple(round((a[k]*(1-tx)+b[k]*tx)*(1-ty)+(c[k]*(1-tx)+d[k]*tx)*ty) for k in range(3))
for row in range(texture.height):
 y=row+crop[1];sy=float(y)
 # The pale ivy blade crosses the fine cane at y715..768. Reuse clean fibres
 # from this same internode, without printing the foreground leaf on the cane.
 if 715<=y<=768:sy=775+(y-715)%20
 x,half,nx,ny=cane_at(sy);collar=max(math.exp(-((sy-node)/1.2)**2) for node in nodes)
 for column in range(strip_width):
  u=(column/(strip_width-1)*2-1)*.92
  atlas.putpixel((texture.width+column,row),(*source_colour(x+nx*half*u*(1+.30*collar),sy+ny*half*u),255))
atlas.save(ASSETS/'center-bamboo-02-approved.png')
spec={'approved_front':'bamboo-shoots-reference-20261007/01-approved-front.png','source_size':[W,H],'crop':crop,
 'world_width':14.222222,'world_height':8,'world_offset':[0,0],'parts':parts,'curves':curves,'node_y':nodes,
 'cane_atlas':{'left':texture.width,'width':strip_width,'texture_width':atlas.width},
 'wall_anchor':-.006,'shell_thickness':.003,'root_clearance':{'start_y':1000,'end_y':H,'x_pixels':0,'depth_factor':.65},
 'occlusion_clearance':{'mesh':'ivy.traced.json','min_y':590,'max_y':1049,'gap':.003},
 'node_profile':{'lip_width':.30,'lip_height':.012,'groove_height':.003},
 'stage':'Second centre fine leaning cane only; previous short cane preserved; pending acceptance'}
(DESIGN/'center-bamboo-02-contours.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
print('Prepared second centre bamboo',len(parts),'independent leaves',len(curves),'connected curves')
