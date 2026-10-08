"""确认左侧五叶小花序弯枝：原图轮廓、独立纹理投影、连续茎柄与可编辑 Blender 场景。"""
import bpy, math, json
from pathlib import Path
from mathutils import Vector

ROOT=Path('D:/repository');OUT=ROOT/'tmp/left-clean-20261007';OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
image=bpy.data.images.load(str(ROOT/'apps/web/src/assets/left-sprig-approved-reference.png'));image.pack()
W,H=image.size[:];S=.0036
# 整株采用同一映射；叶柄及纹理不逐片缩放，替代左半边旧密集植物群。
def world(q,z):return (-4.65+(q.x-440)*S,-.50+(950-q.y)*S,z)
material=bpy.data.materials.new('Approved left sprig fixed-front projection');material.use_nodes=True
nodes=material.node_tree.nodes;nodes.clear();tex=nodes.new('ShaderNodeTexImage');tex.image=image
emission=nodes.new('ShaderNodeEmission');output=nodes.new('ShaderNodeOutputMaterial')
material.node_tree.links.new(tex.outputs['Color'],emission.inputs['Color']);material.node_tree.links.new(emission.outputs[0],output.inputs['Surface'])
objects=[]

def smooth(points,closed=False):
    points=[Vector(p) for p in points];out=[]
    for i in range(len(points) if closed else len(points)-1):
        p1=points[i];p2=points[(i+1)%len(points)]
        p0=points[i-1] if i or closed else 2*p1-p2
        p3=points[(i+2)%len(points)] if closed or i+2<len(points) else 2*p2-p1
        for j in range(10):
            t=j/10
            out.append(.5*(2*p1+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t))
    if not closed:out.append(points[-1])
    return out

def mesh_object(name,vertices,uv,faces):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(vertices,[],faces);mesh.update()
    for face in mesh.polygons:
        if face.normal.z<0:face.flip()
        face.use_smooth=True
    mesh.update();layer=mesh.uv_layers.new(name='Confirmed source coordinates')
    for loop in mesh.loops:layer.data[loop.index].uv=uv[loop.vertex_index]
    obj=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(obj);mesh.materials.append(material)
    obj['wall_anchor']=-.006;objects.append(obj)

def leaf(name,points,center,height=.026):
    boundary=smooth(points,True);center=Vector(center);count=len(boundary);rings=22
    vertices=[];uv=[];faces=[]
    for ring in range(rings+1):
        r=ring/rings
        for edge in boundary:
            q=center.lerp(edge,r)
            # 自由边薄，中腹一次宽弧；微卷沿局部生长方向，叶脉纤维来自原图而非规则刻纹。
            z=.006+height*(1-r*r)**2+.003*math.sin((q.x-center.x)*.014)*(1-r*r)
            vertices.append(world(q,z));uv.append((q.x/W,1-q.y/H))
    for ring in range(rings):
        for j in range(count):
            k=(j+1)%count;a=ring*count;b=a+count;faces.append((a+j,a+k,b+k,b+j))
    mesh_object(name,vertices,uv,faces)

# 五片分别追踪确认图轮廓、叶尖与卷边，不旋转复制同一叶面。
leaf('Upper right narrow curled leaf',[(522,535),(528,514),(537,497),(552,482),(570,466),(584,443),(594,417),(606,393),(621,370),(628,347),(625,325),(621,345),(612,360),(596,374),(579,390),(564,407),(552,429),(541,451),(535,474),(531,494)],(568,439),.026)
leaf('Middle left long thin leaf',[(425,863),(413,843),(395,828),(375,817),(354,804),(335,789),(321,772),(310,753),(303,731),(298,708),(289,684),(280,661),(266,641),(251,624),(232,612),(244,616),(262,616),(283,623),(304,635),(327,649),(349,668),(370,691),(388,717),(401,744),(415,774),(421,801),(422,833)],(361,743),.031)
leaf('Middle right small tilted leaf',[(494,951),(506,931),(525,920),(545,909),(563,895),(579,877),(589,858),(603,837),(620,820),(639,814),(622,812),(608,818),(592,828),(572,837),(551,846),(532,862),(518,881),(508,904)],(552,879),.022)
leaf('Lower left broad lanceolate leaf',[(337,1460),(317,1436),(295,1417),(270,1405),(246,1394),(224,1376),(200,1360),(184,1340),(171,1315),(160,1289),(151,1260),(145,1233),(137,1204),(125,1177),(111,1150),(96,1127),(82,1106),(66,1080),(55,1056),(49,1030),(55,1049),(68,1060),(85,1070),(110,1075),(138,1088),(166,1105),(194,1127),(220,1151),(246,1179),(265,1206),(282,1233),(296,1263),(306,1297),(316,1331),(322,1366),(323,1405)],(228,1254),.034)
leaf('Lower right turned leaf',[(468,1364),(490,1345),(525,1334),(556,1323),(581,1305),(608,1289),(628,1266),(648,1243),(669,1220),(693,1201),(713,1191),(699,1188),(675,1189),(645,1196),(614,1204),(588,1217),(563,1235),(540,1255),(521,1274),(505,1298),(486,1325)],(587,1261),.026)
def branch(name,points,widths,height=.012):
    centers=smooth(points);vertices=[];uv=[];faces=[];columns=12
    for i,q in enumerate(centers):
        tangent=(centers[min(i+1,len(centers)-1)]-centers[max(0,i-1)]).normalized();normal=Vector((-tangent.y,tangent.x))
        segment=min(i//10,len(widths)-2);fraction=(i-segment*10)/10;half=widths[segment]*(1-fraction)+widths[segment+1]*fraction
        for j in range(columns+1):
            u=j/columns*2-1;pixel=q+normal*(half*u)
            vertices.append(world(pixel,.006+height*math.sqrt(max(0,1-u*u))));uv.append((pixel.x/W,1-pixel.y/H))
    for i in range(len(centers)-1):
        for j in range(columns):a=i*(columns+1)+j;b=a+columns+1;faces.append((a,a+1,b+1,b))
    mesh_object(name,vertices,uv,faces)

stem=[(224,1818),(235,1750),(263,1664),(304,1583),(339,1540),(377,1480),(408,1420),(433,1390),(448,1330),(465,1250),(477,1150),(478,1070),(476,1000),(462,960),(445,923),(451,902),(459,809),(469,719),(493,628),(509,573),(512,538),(513,469),(504,405),(488,347)]
branch('Continuous woody bowed main stem',stem,[13,12,11,10,9,9,9,9,8,8,8,8,7,7,7,7,7,6,6,5,5,4,4,3])
branch('Lower left continuous petiole',[(339,1540),(345,1511),(342,1485),(337,1460)],[9,8,6,3])
branch('Lower right curved petiole',[(433,1390),(442,1380),(454,1372),(468,1364)],[8,7,5,3])
branch('Middle left continuous petiole',[(445,923),(439,906),(431,884),(425,863)],[7,6,5,3])
branch('Middle right curved petiole',[(476,1000),(481,979),(489,963),(494,951)],[6,5,4,3])
branch('Lower right curled tip',[(698,1194),(716,1188),(731,1195),(743,1217)],[2,1.5,1.2,.8],.006)
branch('Upper leaf continuous petiole',[(509,573),(514,561),(519,547),(522,535)],[5,4,3,2])
# 六组疏小花序的曲梗依据原图分别追踪，避免规则放射和大圆盘。
for name,pts,widths in [
 ('Left lower crown ray',[(488,347),(465,320),(431,295),(390,278),(345,264)],[3,3,2,2,1.4]),
 ('Left upper crown ray',[(488,347),(471,302),(437,243),(397,199),(363,175)],[3,2.7,2,1.7,1.3]),
 ('Highest crown ray',[(488,347),(462,279),(431,211),(420,166),(422,139)],[3,2.6,2,1.5,1.2]),
 ('Middle crown ray',[(488,347),(474,299),(470,251),(473,214),(476,193)],[3,2.5,2,1.5,1.2]),
 ('Right upper crown ray',[(488,347),(503,289),(530,242),(552,211),(561,191)],[3,2.5,2,1.5,1.2]),
 ('Right lower crown ray',[(488,347),(512,314),(536,293),(555,275)],[3,2.5,1.8,1.2]),
]:branch(name,pts,widths,.008)
# 逐组按原图实际外轮廓描边；小瓣起伏是浅浮雕，表面粒度仍采用原图 UV。
clusters=[
 ('Highest miniature flower cluster',[(410,120),(417,110),(426,114),(431,110),(438,119),(447,126),(445,135),(450,141),(441,149),(440,157),(431,160),(425,169),(417,163),(408,160),(406,153),(398,147),(402,140),(396,131),(403,126)],(423,140)),
 ('Left upper miniature flower cluster',[(350,153),(358,146),(365,150),(374,147),(380,156),(382,165),(390,173),(384,181),(386,190),(377,195),(371,202),(362,199),(355,195),(344,194),(341,185),(335,180),(339,173),(335,165),(343,160)],(364,175)),
 ('Middle miniature flower cluster',[(461,173),(470,166),(478,170),(485,166),(491,175),(498,177),(503,185),(502,193),(507,201),(500,209),(491,210),(483,218),(474,216),(466,214),(462,207),(453,206),(449,198),(452,190),(447,183),(454,181)],(477,193)),
 ('Right upper miniature flower cluster',[(546,172),(554,165),(561,169),(568,167),(578,174),(580,181),(588,187),(586,197),(579,202),(578,211),(569,215),(562,212),(553,218),(546,211),(537,209),(535,199),(530,193),(535,184),(535,179)],(561,191)),
 ('Left lower miniature flower cluster',[(333,243),(341,237),(351,240),(359,242),(365,251),(371,257),(365,265),(369,271),(362,279),(352,278),(347,288),(337,286),(331,282),(323,280),(319,272),(313,265),(318,258),(317,250),(325,247)],(344,263)),
 ('Right lower miniature flower cluster',[(543,256),(551,250),(559,254),(569,255),(571,263),(579,269),(575,277),(578,285),(571,292),(563,290),(557,298),(548,295),(541,293),(534,288),(530,280),(529,273),(535,266),(534,261)],(555,275)),
]
for name,pts,center in clusters:leaf(name,pts,center,.024)
# 根部按同一连续曲线向屏幕底缘延伸；投影仅取图内原木质茎，不另加交叉长杆。
end=Vector(world(Vector(stem[0]),0)[:2]);curve=smooth([(-5.58,-4.55),(-5.54,-4.06),tuple(end)])
source=smooth(stem[:3]);vertices=[];uv=[];faces=[]
for i,q in enumerate(curve):
    t=i/(len(curve)-1);si=(1-t)*(len(source)-1);k=min(int(si),len(source)-2);sample=source[k].lerp(source[k+1],si-k)
    source_t=(source[k+1]-source[k]).normalized();sn=Vector((source_t.y,-source_t.x))
    tangent=(curve[min(i+1,len(curve)-1)]-curve[max(i-1,0)]).normalized();normal=Vector((-tangent.y,tangent.x));half=.043
    for j in range(13):
        u=j/12*2-1;p=q+normal*(half*u);pixel=sample+sn*(half/S*u)
        vertices.append((p.x,p.y,.006+.012*math.sqrt(max(0,1-u*u))));uv.append((pixel.x/W,1-pixel.y/H))
for i in range(len(curve)-1):
    for j in range(12):a=i*13+j;b=a+13;faces.append((a,a+1,b+1,b))
mesh_object('Continuous rooted lower stem',vertices,uv,faces)
data={'position':[],'normal':[],'uv':[],'index':[],'anchor':[]}
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles();offset=len(data['anchor']);uv={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
    for vertex in mesh.vertices:
        data['position'].extend(round(v,7) for v in vertex.co);data['normal'].extend(round(v,7) for v in vertex.normal)
        data['uv'].extend(round(v,7) for v in uv[vertex.index]);data['anchor'].append(-.006)
    for tri in mesh.loop_triangles:data['index'].extend(offset+v for v in tri.vertices)
(ROOT/'apps/web/src/assets/left-sprig.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=world(Vector((413,951)),8));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=H*S
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=8
scene.render.resolution_x=W;scene.render.resolution_y=H;scene.render.resolution_percentage=100
scene.render.film_transparent=True;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.render.filepath=str(OUT/'left-sprig-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/left-sprig-traced.blend'));bpy.ops.render.render(write_still=True)
print('TRACED_LEFT_SPRIG',W,H,len(data['anchor']),len(data['index'])//3)
