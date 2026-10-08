"""First of the user-selected tall pair: trace the approved front independently."""
import json, math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT=Path(__file__).resolve().parents[3]
DESIGN=ROOT/'apps/web/design';ASSETS=ROOT/'apps/web/src/assets'
source=Image.open(DESIGN/'bamboo-shoots-reference-20261007/01-approved-front.png').convert('RGB')
W,H=source.size
# Observed coordinates are from the 2x inspection crop, whose source origin is
# (200,0). Each contour is traced separately, including its asymmetric free edge.
leaves=[
 [(532,68),(576,59),(620,62),(694,89),(747,111),(697,93),(619,77),(548,77)],
 [(530,79),(568,83),(614,103),(677,151),(736,199),(684,170),(622,126),(549,95)],
 [(529,84),(557,109),(590,145),(642,193),(698,236),(659,213),(599,168),(551,119)],
 [(527,88),(539,124),(562,165),(593,212),(613,247),(591,222),(553,173),(532,126)],
 [(524,90),(521,116),(528,150),(547,187),(552,207),(539,180),(522,139),(515,109)],
 [(486,61),(465,49),(440,43),(405,44),(447,59),(480,72)],
 [(486,55),(500,35),(536,4),(518,39),(496,61)],
 [(474,72),(454,91),(433,118),(413,143),(439,123),(467,95)],
 [(295,350),(276,328),(251,311),(146,268),(201,300),(247,329),(283,357)],
 [(286,352),(254,339),(224,347),(176,370),(215,358),(248,359),(278,362)],
 [(282,360),(258,363),(211,389),(165,429),(126,466),(176,433),(237,391),(280,369)],
 [(287,362),(266,371),(235,393),(209,427),(244,404),(277,379)],
 [(292,360),(274,376),(239,401),(216,441),(197,518),(229,460),(260,413),(287,382)],
 [(296,362),(287,388),(269,430),(261,489),(253,553),(274,503),(293,445),(302,392)],
 [(307,414),(300,443),(295,488),(291,536),(305,506),(318,454)],
 [(312,418),(328,444),(342,493),(354,558),(355,508),(340,451),(322,418)],
 [(311,413),(292,403),(265,400),(249,405),(281,413),(307,422)],
 [(247,710),(212,710),(166,723),(103,746),(148,731),(207,727),(245,720)],
 [(245,718),(218,724),(177,749),(126,802),(79,862),(126,827),(187,775),(243,726)],
 [(246,722),(231,752),(202,808),(157,885),(173,821),(213,760),(242,718)],
 [(248,721),(244,757),(233,800),(220,859),(243,821),(257,761)],
 [(251,722),(264,750),(270,775),(275,810),(280,780),(268,737)],
 [(210,1143),(189,1125),(162,1105),(97,1063),(151,1081),(193,1119)],
 [(210,1147),(187,1155),(163,1170),(124,1222),(163,1191),(198,1165)],
 [(213,1149),(202,1177),(181,1209),(157,1273),(183,1240),(207,1203),(220,1167)],
 [(216,1150),(221,1183),(219,1227),(208,1305),(225,1265),(232,1210),(224,1166)],
 [(218,1151),(238,1174),(249,1207),(255,1260),(257,1230),(248,1180),(229,1159)],
]
stem=[(480,-12),(454,126),(404,324),(357,600),(319,860),(292,1120),(275,1380),(251,1630),(221,1900),(201,2135)]
stem_halfwidth=[5.5,7,8.5,12,12,11.5,12,12,13,13]
branches=[
 {'name':'top connected twig','points':[(462,83),(479,68),(510,74),(533,88)],'widths':[2.2,2,1.6,.8]},
 {'name':'upper left arched branch','points':[(357,600),(353,501),(344,434),(320,394),(290,357)],'widths':[2.6,2,1.8,1.5,1]},
 {'name':'upper secondary petiole','points':[(344,434),(330,417),(312,414)],'widths':[1.7,1.3,.8]},
 {'name':'middle arched branch','points':[(319,860),(309,806),(302,740),(284,707),(248,717)],'widths':[2.6,2.1,1.8,1.4,.9]},
 {'name':'lower arched branch','points':[(275,1380),(264,1305),(248,1215),(224,1158),(212,1149)],'widths':[2.4,1.9,1.6,1.3,.8]},
]

def sample_curve(points,widths,count=12):
    sampled=[]
    for i in range(len(points)-1):
        p1=points[i];p2=points[i+1]
        p0=points[i-1] if i else [2*a-b for a,b in zip(p1,p2)]
        p3=points[i+2] if i+2<len(points) else [2*b-a for a,b in zip(p1,p2)]
        for j in range(count):
            t=j/count
            q=[.5*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t) for a,b,c,d in zip(p0,p1,p2,p3)]
            sampled.append([200+q[0]/2,q[1]/2,(widths[i]*(1-t)+widths[i+1]*t)/2])
    sampled.append([200+points[-1][0]/2,points[-1][1]/2,widths[-1]/2]);return sampled

parts=[{'name':f'Individually traced leaf {i+1:02}','polygon':[[200+x/2,y/2] for x,y in polygon]} for i,polygon in enumerate(leaves)]
# The top crown is rechecked against a 2x source crop at (260,0), retaining
# broad folded leaves and irregular tips. The lower right crown belongs to the
# SECOND cane and is deliberately left for its own acceptance round.
upper_crown=[
 [(398,65),(445,60),(487,65),(542,78),(590,94),(549,86),(491,75),(438,74),(407,79)],
 [(400,76),(434,79),(462,92),(509,125),(558,161),(615,203),(575,184),(521,157),(466,118),(418,95)],
 [(398,81),(420,104),(453,143),(489,183),(528,220),(561,248),(533,233),(492,206),(449,166),(412,121)],
 [(396,86),(411,118),(431,155),(456,195),(473,230),(480,259),(459,231),(435,193),(410,151),(392,113)],
 [(392,90),(389,115),(395,141),(415,175),(422,203),(413,185),(396,156),(383,126),(382,107)],
 [(348,72),(324,58),(299,49),(285,44),(327,46),(348,58),(358,73)],
 [(348,61),(364,35),(389,14),(420,6),(393,35),(364,58),(352,72)],
 [(348,84),(333,102),(310,125),(292,143),(302,121),(326,91),(343,75)],
]
for part,polygon in zip(parts,upper_crown):
    part['polygon']=[[260+x/2,y/2] for x,y in polygon]
curves=[{'name':'Continuous bowed main cane','points':sample_curve(stem,stem_halfwidth,48),'stem':True}]
curves.extend({'name':branch['name'],'points':sample_curve(branch['points'],branch['widths'],24),'stem':False} for branch in branches)
crop=[230,0,580,H]
mask=Image.new('L',(crop[2]-crop[0],crop[3]-crop[1]),0);draw=ImageDraw.Draw(mask)
for leaf in parts:draw.polygon([(x-crop[0],y-crop[1]) for x,y in leaf['polygon']],fill=255)
for curve in curves:
    for x,y,r in curve['points']:
        draw.ellipse((x-crop[0]-r,y-r,x-crop[0]+r,y+r),fill=255)
mask=mask.filter(ImageFilter.GaussianBlur(.25))
texture=source.crop(crop).convert('RGBA');texture.putalpha(mask)
# Keep leaves on the unchanged source projection. An independent opaque strip
# gives the cane continuous UVs: wheat crosses its lower part in the reference,
# and moving UVs between narrower sections of the silhouette caused alpha gaps.
# Only the occluded lower section borrows clean fibres from this SAME cane.
strip_width=32
atlas=Image.new('RGBA',(texture.width+strip_width,H),(0,0,0,0));atlas.paste(texture,(0,0))
centers=curves[0]['points']
def cane_at(y):
    for a,b in zip(centers,centers[1:]):
        if a[1]<=y<=b[1]:
            t=(y-a[1])/max(1e-9,b[1]-a[1]);dx,dy=b[0]-a[0],b[1]-a[1]
            length=max(1e-9,math.hypot(dx,dy))
            return a[0]*(1-t)+b[0]*t,a[2]*(1-t)+b[2]*t,-dy/length,dx/length
    return centers[-1][0],centers[-1][2],-1,0
for y in range(H):
    sy=float(y)
    if y>=735:
        node=min([815,950],key=lambda n:abs(y-n))
        sy=690+(y-node) if abs(y-node)<=4 else 580+(y-735)%100
    x,r,nx,ny=cane_at(sy)
    collar=max(math.exp(-((sy-node)/1.2)**2) for node in [63,162,300,430,560,690,815,950])
    for column in range(strip_width):
        u=(column/(strip_width-1)*2-1)*.92
        px=max(0,min(W-1,round(x+nx*r*u*(1+.12*collar))))
        py=max(0,min(H-1,round(sy+ny*r*u)))
        atlas.putpixel((texture.width+column,y),(*source.getpixel((px,py)),255))
atlas.save(ASSETS/'bamboo-tall-01-approved.png')
spec={'approved_front':'bamboo-shoots-reference-20261007/01-approved-front.png','source_size':[W,H],'crop':crop,'world_width':14.222222,'world_height':8,'world_offset':[0,0],'parts':parts,'curves':curves,'node_y':[63,162,300,430,560,690,815,950],'wall_anchor':-.006,'shell_thickness':.003}
spec['stage']='First tall cane of selected pair only; existing shoot accepted; await cane acceptance'
spec['cane_atlas']={'left':texture.width,'width':strip_width,'texture_width':atlas.width,'repaired_from_y':735,'note':'Occluded lower UV reconstructed with clean source fibres; original silhouette and spacing preserved'}
# Only the new cane's lowest section bends slightly left, avoiding the accepted
# wheat stem at the lower edge. Existing shoot/plant positions remain untouched.
spec['root_clearance']={'start_y':875,'end_y':H,'x_pixels':-13,'depth_factor':.65}
(DESIGN/'bamboo-tall-01-contours.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
print('Prepared only the first new bamboo:',len(parts),'traced leaves;',len(curves),'continuous curves')
