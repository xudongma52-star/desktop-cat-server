"""Trace only the second tall cane, preserving the accepted first cane/shoot."""
import json,math
from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
ROOT=Path(__file__).resolve().parents[3];DESIGN=ROOT/'apps/web/design';ASSETS=ROOT/'apps/web/src/assets'
source=Image.open(DESIGN/'bamboo-shoots-reference-20261007/01-approved-front.png').convert('RGB');W,H=source.size
# Independently observed leaf edges in 2x crops: upper (330,65), lower (320,340).
# Every fan follows its own attachment and asymmetric folds; no rotated copies.
upper=[
 [(253,66),(276,52),(311,44),(354,36),(337,47),(301,56),(271,69)],
 [(267,68),(294,66),(330,77),(380,99),(431,121),(387,108),(337,92),(281,78)],
 [(278,77),(309,86),(349,111),(402,145),(451,181),(422,171),(378,150),(329,118),(290,95)],
 [(284,87),(311,112),(333,143),(357,182),(384,231),(355,200),(326,165),(301,124)],
 [(279,88),(294,118),(304,158),(316,205),(326,270),(310,229),(299,187),(285,139)],
 [(263,73),(251,91),(224,113),(192,138),(170,153),(193,121),(223,94),(247,77)],
 [(260,75),(271,94),(269,122),(264,158),(266,181),(277,160),(282,122),(273,91)],
 [(180,371),(198,351),(224,335),(266,318),(294,307),(277,322),(246,340),(212,358),(191,376)],
 [(188,374),(213,366),(244,368),(285,381),(318,397),(289,388),(249,380),(214,379),(192,382)],
 [(187,384),(220,393),(248,412),(287,446),(320,480),(294,460),(259,435),(216,409)],
 [(190,393),(205,412),(220,446),(235,489),(249,543),(230,514),(211,479),(198,441)],
 [(181,395),(183,418),(188,447),(187,480),(179,512),(178,477),(174,444),(174,413)],
 [(178,382),(158,393),(141,414),(131,434),(149,417),(170,400),(184,389)],
]
lower=[
 [(194,84),(219,75),(251,73),(280,76),(307,83),(279,85),(250,82),(220,86)],
 [(203,86),(228,88),(256,96),(302,113),(337,126),(365,142),(325,130),(285,119),(239,101)],
 [(211,93),(234,108),(267,130),(305,151),(345,175),(396,205),(367,192),(320,174),(270,146),(229,116)],
 [(201,97),(208,119),(225,145),(245,169),(266,190),(251,162),(228,128),(216,103)],
 [(208,98),(235,107),(257,125),(275,140),(244,128),(219,117)],
 [(240,174),(270,153),(302,141),(330,133),(362,128),(335,143),(303,155),(267,175),(250,184)],
 [(245,178),(268,179),(296,190),(327,212),(356,240),(374,260),(344,241),(309,217),(272,198),(250,191)],
 [(254,188),(276,215),(295,245),(310,282),(335,340),(317,316),(293,280),(272,235)],
 [(246,185),(251,214),(254,256),(251,301),(253,359),(251,397),(243,361),(239,311),(238,257),(238,213)],
 [(238,186),(221,207),(203,228),(180,246),(166,250),(183,220),(205,197),(229,180)],
 [(227,239),(219,254),(214,275),(208,282),(212,260)],
 [(263,198),(285,213),(313,235),(345,267),(371,304),(352,285),(318,254),(285,226)],
 [(333,530),(317,529),(296,540),(270,558),(235,583),(256,562),(285,539),(315,525)],
 [(332,529),(322,552),(309,578),(300,598),(315,582),(333,555),(341,535)],
 [(343,536),(369,547),(399,568),(431,596),(461,625),(434,607),(400,586),(367,560)],
 [(340,541),(359,570),(374,603),(388,647),(400,696),(379,660),(363,623),(347,584)],
 [(333,541),(336,570),(340,611),(349,655),(361,683),(355,652),(353,609),(342,563)],
 [(349,533),(368,531),(396,542),(424,560),(454,584),(426,570),(393,554),(360,542)],
 [(341,525),(353,510),(374,502),(406,507),(436,521),(413,516),(381,513),(360,519)],
]
parts=[]
for name,polygons,origin in [('Upper',upper,(330,65)),('Lower',lower,(320,340))]:
    for polygon in polygons:
        parts.append({'name':f'Second cane {name} leaf {len(parts)+1:02}','polygon':[[origin[0]+x/2,origin[1]+y/2] for x,y in polygon]})
def sample(points,widths,count=24):
    out=[]
    for i in range(len(points)-1):
        p1=points[i];p2=points[i+1];p0=points[i-1] if i else [2*a-b for a,b in zip(p1,p2)]
        p3=points[i+2] if i+2<len(points) else [2*b-a for a,b in zip(p1,p2)]
        for j in range(count):
            t=j/count
            q=[.5*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t) for a,b,c,d in zip(p0,p1,p2,p3)]
            out.append([*q,widths[i]*(1-t)+widths[i+1]*t])
    out.append([*points[-1],widths[-1]]);return out
stem=[(460,34),(451,88),(434,144),(419,212),(407,286),(399,346),(390,424),(384,493),(365,605),(351,719),(344,816),(334,916),(326,965),(320,1060)]
widths=[.4,.55,.8,1.4,2.2,2.8,3.4,4.1,4.5,5.3,5.5,6,6,6]
curves=[{'name':'Second continuous cane with individual node collars','points':sample(stem,widths,48),'stem':True}]
for name,points,ws in [
 ('Top fan connected twig',[(445,108),(452,99),(461,102),(470,107)],[.8,.8,.6,.4]),
 ('Upper right fan arch',[(407,286),(408,272),(413,256),(421,253)],[1.4,1.2,.9,.6]),
 ('Middle high fan arch',[(397,409),(404,394),(411,384),(420,382)],[1.6,1.3,1,.6]),
 ('Middle lower fan arch',[(384,493),(397,477),(414,467),(431,436),(442,430)],[1.8,1.5,1.2,.9,.6]),
 ('Lower broad arched branch',[(365,605),(393,609),(423,601),(460,586),(482,591),(492,607)],[1.8,1.6,1.3,1.1,.8,.5]),
]:curves.append({'name':name,'points':sample(points,ws),'stem':False})
crop=[300,25,567,H];nodes=[286,493,605,719,965]
mask=Image.new('L',(crop[2]-crop[0],crop[3]-crop[1]),0);draw=ImageDraw.Draw(mask)
for part in parts:draw.polygon([(x-crop[0],y-crop[1]) for x,y in part['polygon']],fill=255)
for curve in curves:
    for x,y,r in curve['points']:draw.ellipse((x-crop[0]-r,y-crop[1]-r,x-crop[0]+r,y-crop[1]+r),fill=255)
texture=source.crop(crop).convert('RGBA');texture.putalpha(mask.filter(ImageFilter.GaussianBlur(.25)))
# An opaque same-cane strip preserves taper without UV/alpha tears. At the
# wheat crossing, use neighbouring clean fibres, keeping the lower node at 965.
strip_width=32;atlas=Image.new('RGBA',(texture.width+strip_width,texture.height),(0,0,0,0));atlas.paste(texture,(0,0))
centers=curves[0]['points']
def cane_at(y):
    for a,b in zip(centers,centers[1:]):
        if a[1]<=y<=b[1]:
            t=(y-a[1])/max(1e-9,b[1]-a[1]);dx,dy=b[0]-a[0],b[1]-a[1];length=max(1e-9,math.hypot(dx,dy))
            return a[0]*(1-t)+b[0]*t,a[2]*(1-t)+b[2]*t,-dy/length,dx/length
    return centers[-1][0],centers[-1][2],-1,0
for row in range(texture.height):
    y=row+crop[1];sy=float(y)
    if 775<=y<=900:sy=630+(y-775)%70
    x,r,nx,ny=cane_at(sy);collar=max(math.exp(-((sy-n)/1.2)**2) for n in nodes)
    for column in range(strip_width):
        u=(column/(strip_width-1)*2-1)*.9
        px=max(0,min(W-1,round(x+nx*r*u*(1+.12*collar))));py=max(0,min(H-1,round(sy+ny*r*u)))
        atlas.putpixel((texture.width+column,row),(*source.getpixel((px,py)),255))
atlas.save(ASSETS/'bamboo-tall-02-approved.png')
spec={'approved_front':'bamboo-shoots-reference-20261007/01-approved-front.png','source_size':[W,H],'crop':crop,'world_width':14.222222,'world_height':8,'world_offset':[0,0],'parts':parts,'curves':curves,'node_y':nodes,'wall_anchor':-.006,'shell_thickness':.003,'stage':'Second tall cane only; first cane and shoot already accepted; await second cane acceptance','cane_atlas':{'left':texture.width,'width':strip_width,'texture_width':atlas.width},'root_clearance':{'start_y':875,'end_y':H,'x_pixels':0,'depth_factor':.65}}
(DESIGN/'bamboo-tall-02-contours.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
print('Prepared SECOND tall bamboo',len(parts),'individually traced leaves',len(curves),'continuous curves')
