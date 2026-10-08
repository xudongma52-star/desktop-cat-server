"""按已确认原图重建四叶木质枝：独立轮廓、连续茎柄、原图 UV、可编辑 Blender 源。"""
import bpy, math, json
from pathlib import Path
from mathutils import Vector

ROOT=Path('D:/repository');OUT=ROOT/'tmp/reference-broadleaf-20261007'
OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
image=bpy.data.images.load(str(ROOT/'apps/web/src/assets/broadleaf-approved-reference.png'));image.pack()
W,H=image.size[:];S=.0015
# 原三出叶所在的中偏左植物带；枝节中心为同一 XY 映射，不逐片拼贴缩放。
def world(q,z):return (-1+(q.x-622)*S,-1.25+(964-q.y)*S,z)
material=bpy.data.materials.new('Approved broadleaf fixed-view projection');material.use_nodes=True
nodes=material.node_tree.nodes;nodes.clear()
tex=nodes.new('ShaderNodeTexImage');tex.image=image
emission=nodes.new('ShaderNodeEmission');output=nodes.new('ShaderNodeOutputMaterial')
material.node_tree.links.new(tex.outputs['Color'],emission.inputs['Color']);material.node_tree.links.new(emission.outputs[0],output.inputs['Surface'])
objects=[]

def smooth(points,closed=True):
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
    for polygon in mesh.polygons:
        if polygon.normal.z<0:polygon.flip()
        polygon.use_smooth=True
    mesh.update();layer=mesh.uv_layers.new(name='Approved reference UV')
    for loop in mesh.loops:layer.data[loop.index].uv=uv[loop.vertex_index]
    obj=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(obj)
    obj.data.materials.append(material);obj['wall_anchor']=-.006;objects.append(obj)

def leaf(name,points,center,root,height=.022):
    boundary=smooth(points);center=Vector(center);root=Vector(root)
    count=len(boundary);vertices=[];uv=[];faces=[];rings=22
    for ring in range(rings+1):
        r=ring/rings
        for edge in boundary:
            q=center.lerp(edge,r)
            # 薄边、一次宽缓偏折；叶脉保留真实参考纹理，不雕成规则密集横条。
            z=.006+height*(1-r*r)**2+.002*math.sin((q.x-root.x)*.012)*(1-r*r)
            vertices.append(world(q,z));uv.append((q.x/W,1-q.y/H))
    for ring in range(rings):
        for j in range(count):
            k=(j+1)%count;a=ring*count;b=a+count
            faces.append((a+j,a+k,b+k,b+j))
    mesh_object(name,vertices,uv,faces)

# 四片分别描边，保留左主叶的反卷尖端、不同叶腹和第四片侧倾叶的凹边。
leaf('Large left thin pointed leaf',[(400,638),(376,612),(325,604),(278,594),(226,566),(183,526),(151,485),(130,435),(114,383),(104,329),(97,265),(94,205),(91,174),(83,153),(83,137),(95,157),(119,168),(159,180),(203,189),(251,210),(297,240),(336,279),(369,320),(396,369),(415,419),(421,467),(417,518),(406,571)],(255,411),(400,638),.028)
leaf('Upright asymmetric pointed leaf',[(525,706),(506,668),(480,644),(457,612),(442,575),(437,537),(439,497),(451,456),(474,416),(503,379),(532,342),(566,308),(602,280),(631,258),(660,239),(677,228),(667,253),(666,286),(675,324),(687,365),(693,409),(695,452),(689,492),(678,533),(660,570),(634,607),(604,638),(564,668)],(567,476),(525,706),.025)
leaf('Smaller right pointed leaf',[(697,793),(706,754),(707,716),(718,680),(738,655),(768,631),(798,617),(830,611),(859,612),(888,613),(915,611),(938,608),(953,602),(942,621),(921,644),(906,672),(885,702),(861,732),(832,760),(796,782),(759,791),(726,788)],(804,704),(697,793),.019)
leaf('Lower turned leaf',[(560,888),(528,886),(490,901),(453,928),(421,954),(385,978),(346,995),(310,1001),(282,1007),(267,1011),(276,991),(282,959),(290,923),(305,886),(327,856),(359,834),(393,822),(429,820),(465,826),(499,842),(532,864)],(405,903),(560,888),.018)
leaf('Closed pointed bud',[(770,275),(761,259),(763,237),(772,218),(788,202),(807,188),(829,180),(847,175),(834,197),(826,220),(811,242),(794,259)],(798,226),(770,275),.025)

def branch(name,points,widths):
    centers=smooth(points,False);vertices=[];uv=[];faces=[];columns=12
    for i,q in enumerate(centers):
        tangent=(centers[min(i+1,len(centers)-1)]-centers[max(0,i-1)]).normalized()
        normal=Vector((-tangent.y,tangent.x));segment=min(i//10,len(widths)-2);fraction=(i-segment*10)/10
        half=widths[segment]*(1-fraction)+widths[segment+1]*fraction
        for j in range(columns+1):
            u=j/columns*2-1;pixel=q+normal*(half*u)
            # 茎边贴壁，截面柔和，整体深度与叶片共用同一显现与投影变形。
            vertices.append(world(pixel,.006+.010*math.sqrt(max(0,1-u*u))))
            uv.append((pixel.x/W,1-pixel.y/H))
    for i in range(len(centers)-1):
        for j in range(columns):
            a=i*(columns+1)+j;b=a+columns+1;faces.append((a,a+1,b+1,b))
    mesh_object(name,vertices,uv,faces)

stem=[(440,1400),(457,1351),(481,1290),(497,1250),(531,1194),(569,1130),(594,1085),(611,1027),(622,975),(631,919),(642,871),(653,829),(656,769),(660,709),(680,653),(707,600),(728,541),(754,458),(746,395),(743,339),(751,301),(770,275)]
branch('Entire continuous woody main axis',stem,[16,15,14,14,12,12,11,10,10,9,8,8,7,7,6,6,5,5,4,4,4,4])
branch('Large left leaf branch',[(622,962),(607,911),(584,857),(559,812),(528,758),(491,723),(449,693),(420,662),(400,638)],[9,8,8,7,7,6,5,4,3])
branch('Upright leaf petiole',[(528,758),(525,736),(521,716),(525,706)],[6,5,4,3])
branch('Right leaf petiole',[(653,829),(668,818),(684,802),(697,793)],[6,5,4,3])
branch('Lower turned leaf petiole',[(622,962),(612,935),(588,910),(560,888)],[7,6,4,3])
# 几处原图自然枝节独立描边，遮住汇接缝而不添加球状节点。
leaf('Main fork knot',[(602,943),(616,939),(626,929),(637,935),(635,951),(625,969),(611,976),(602,966)],(619,953),(610,972),.012)
leaf('Upper petiole knot',[(513,750),(525,744),(536,747),(543,757),(534,765),(522,766),(514,760)],(527,756),(524,764),.008)
leaf('Right node',[(643,819),(652,812),(663,815),(674,829),(665,839),(652,840),(643,833)],(657,828),(650,837),.008)
leaf('Lower woody knot',[(576,1103),(585,1092),(596,1091),(605,1101),(600,1114),(587,1121),(577,1117)],(590,1107),(584,1118),.008)

# 网页下段接回原植株落地根点，沿原图下段茎的切线继续；仅复用木质茎内的 UV。
endpoint=Vector(world(Vector(stem[0]),0)[:2])
curve=smooth([(-2.18,-4.55),(-1.90,-3.73),(-1.65,-2.96),(-1.45,-2.38),tuple(endpoint)],False)
source=smooth(stem[:4],False);vertices=[];uv=[];faces=[]
for i,q in enumerate(curve):
    f=i/(len(curve)-1);source_t=(1-f)*(len(source)-1)
    source_i=min(int(source_t),len(source)-2);sample=source[source_i].lerp(source[source_i+1],source_t-source_i)
    source_tangent=(source[source_i+1]-source[source_i]).normalized()
    source_normal=Vector((source_tangent.y,-source_tangent.x))
    tangent=(curve[min(i+1,len(curve)-1)]-curve[max(0,i-1)]).normalized();normal=Vector((-tangent.y,tangent.x))
    half=.026*(1-f)+16*S*f
    for j in range(13):
        u=j/12*2-1;v=q+normal*(half*u)
        vertices.append((v.x,v.y,.006+.010*math.sqrt(max(0,1-u*u))))
        pixel=sample+source_normal*(half/S*u)
        uv.append((pixel.x/W,1-pixel.y/H))
for i in range(len(curve)-1):
    for j in range(12):a=i*13+j;b=a+13;faces.append((a,a+1,b+1,b))
mesh_object('Curved lower rooted extension',vertices,uv,faces)

data={'position':[],'normal':[],'uv':[],'index':[],'anchor':[]}
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles();offset=len(data['position'])//3
    uv_by_vertex={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
    for vertex in mesh.vertices:
        data['position'].extend(round(v,7) for v in vertex.co)
        data['normal'].extend(round(v,7) for v in vertex.normal)
        data['uv'].extend(round(v,7) for v in uv_by_vertex[vertex.index])
        data['anchor'].append(obj['wall_anchor'])
    for tri in mesh.loop_triangles:data['index'].extend(offset+v for v in tri.vertices)
(ROOT/'apps/web/src/assets/broadleaf.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=world(Vector((512,768)),8));camera=bpy.context.object
camera.data.type='ORTHO';camera.data.ortho_scale=1536*S
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=8
scene.render.resolution_x=1024;scene.render.resolution_y=1536;scene.render.resolution_percentage=100
scene.render.film_transparent=True;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.render.filepath=str(OUT/'broadleaf-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/broadleaf-traced.blend'))
bpy.ops.render.render(write_still=True)
print('TRACED_BROADLEAF',len(data['position'])//3,len(data['index'])//3)
