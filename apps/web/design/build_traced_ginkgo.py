"""将用户确认的银杏叶及弯枝逐轮廓建模，保留原图 UV 与可编辑 Blender 源文件。"""
import bpy, math, json
from pathlib import Path
from mathutils import Vector

ROOT=Path('D:/repository');OUT=ROOT/'tmp/reference-ginkgo-20261006'
OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
image=bpy.data.images.load(str(ROOT/'apps/web/src/assets/ginkgo-approved-reference.png'));image.pack()
W,H=image.size[:];S=.002
def world(q,z):return (-1.80+(q.x-495)*S,1.20+(830-q.y)*S,z)
material=bpy.data.materials.new('Approved ginkgo fixed-view projection');material.use_nodes=True
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
        for j in range(12):
            t=j/12
            out.append(.5*(2*p1+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t))
    if not closed:out.append(points[-1])
    return out
def mesh_object(name,vertices,uv,faces,anchor=-.006):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(vertices,[],faces);mesh.update()
    for polygon in mesh.polygons:
        if polygon.normal.z<0:polygon.flip()
        polygon.use_smooth=True
    mesh.update();layer=mesh.uv_layers.new(name='Approved reference UV')
    for loop in mesh.loops:layer.data[loop.index].uv=uv[loop.vertex_index]
    obj=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(obj)
    obj.data.materials.append(material);obj['wall_anchor']=anchor;objects.append(obj)
def leaf(name,points,center,root):
    boundary=smooth(points);center=Vector(center);root=Vector(root)
    count=len(boundary);vertices=[];uv=[];faces=[]
    for ring in range(25):
        r=ring/24
        for edge in boundary:
            q=center.lerp(edge,r)
            # 叶片薄边与柔和叶腹；细密二叉叶脉来自原图，避免等距高刻线。
            z=.004+.013*(1-r*r)**2+.0025*math.sin((q.x-root.x)*.012)*(1-r*r)
            vertices.append(world(q,z));uv.append((q.x/W,1-q.y/H))
    for ring in range(24):
        for j in range(count):
            k=(j+1)%count;a=ring*count;b=a+count
            faces.append((a+j,a+k,b+k,b+j))
    mesh_object(name,vertices,uv,faces)

leaf('Large organic bilobed ginkgo',[(432,716),(389,665),(332,633),(263,600),(212,578),(163,586),(112,613),(80,620),(48,605),(38,577),(39,532),(47,484),(64,440),(85,407),(89,373),(109,344),(135,332),(149,299),(181,278),(206,248),(238,217),(268,213),(291,237),(301,280),(312,326),(320,273),(316,229),(332,207),(360,202),(390,216),(418,223),(440,256),(481,275),(513,295),(552,312),(584,347),(606,382),(604,416),(589,449),(556,483),(526,530),(488,580),(462,641)],(319,446),(432,716))
leaf('Upper asymmetric ginkgo',[(667,473),(669,422),(648,363),(620,310),(579,266),(549,227),(539,199),(549,171),(575,156),(607,139),(635,116),(666,114),(694,129),(717,123),(743,143),(773,165),(786,194),(784,225),(769,264),(800,224),(824,208),(847,207),(874,226),(892,255),(905,282),(925,311),(937,344),(929,375),(927,407),(909,436),(883,442),(846,432),(804,416),(763,406),(730,413),(698,438)],(719,295),(667,473))
leaf('Small side ginkgo',[(726,795),(752,762),(769,721),(756,688),(731,667),(728,642),(747,621),(774,611),(797,603),(825,597),(849,600),(873,619),(881,644),(878,670),(879,702),(900,707),(915,695),(934,709),(953,737),(966,766),(976,800),(971,838),(954,876),(932,902),(909,918),(887,916),(861,892),(842,861),(817,836),(785,818),(752,805)],(857,770),(726,795))

def branch(name,points,width):
    centers=smooth(points,False);vertices=[];uv=[];faces=[];columns=10
    for i,q in enumerate(centers):
        tangent=(centers[min(i+1,len(centers)-1)]-centers[max(0,i-1)]).normalized()
        normal=Vector((-tangent.y,tangent.x));fraction=i/(len(centers)-1)
        half=width*(1-.35*fraction)
        for j in range(columns+1):
            u=j/columns*2-1;pixel=q+normal*(half*u)
            vertices.append(world(pixel,.008+.006*math.sqrt(max(0,1-u*u))))
            uv.append((pixel.x/W,1-pixel.y/H))
    for i in range(len(centers)-1):
        for j in range(columns):
            a=i*(columns+1)+j;b=a+columns+1;faces.append((a,a+1,b+1,b))
    mesh_object(name,vertices,uv,faces)
branch('Continuous curved main woody stem',[(245,1400),(297,1281),(344,1165),(393,1062),(438,988),(465,937),(481,855),(498,769),(528,683),(581,595),(632,522),(667,473)],10)
branch('Large leaf curved petiole',[(481,855),(471,806),(451,761),(432,716)],8)
branch('Small leaf curved petiole',[(465,937),(516,903),(578,868),(648,836),(700,812),(726,795)],7)
# 原图枝节的轮廓及纹理独立保留，连接处不是黑球或细线交叉。
leaf('Upper woody node',[(463,807),(478,793),(493,793),(508,804),(505,817),(488,832),(479,846),(470,842)],(486,814),(476,842))
leaf('Lower woody node',[(448,936),(460,927),(477,925),(486,935),(478,955),(464,966),(450,960)],(467,946),(450,960))
# 页面下段保持落地位置，渐弯接入原图茎端；延长段复用原图真实木质茎纹理。
source=smooth([(245,1400),(297,1281),(344,1165),(393,1062)],False)
world_curve=smooth([(-4.45,-4.55),(-3.93,-2.88),(-3.10,-1.10),(-2.30,.06)],False)
vertices=[];uv=[];faces=[]
for i,q in enumerate(world_curve):
    f=i/(len(world_curve)-1);sample=source[int((1-f)*(len(source)-1))]
    tangent=(world_curve[min(i+1,len(world_curve)-1)]-world_curve[max(0,i-1)]).normalized();normal=Vector((-tangent.y,tangent.x))
    for j in range(11):
        u=j/10*2-1;v=q+normal*(.020*u)
        vertices.append((v.x,v.y,.008+.006*math.sqrt(max(0,1-u*u))))
        uv.append(((sample.x+10*u)/W,1-sample.y/H))
for i in range(len(world_curve)-1):
    for j in range(10):a=i*11+j;b=a+11;faces.append((a,a+1,b+1,b))
mesh_object('Lower continuous extension',vertices,uv,faces)

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
(ROOT/'apps/web/src/assets/ginkgo.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=(-1.8,1.2,8));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=3.1
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=16
scene.render.resolution_x=1000;scene.render.resolution_y=1200;scene.render.resolution_percentage=100
scene.render.film_transparent=True;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.render.filepath=str(OUT/'ginkgo-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/ginkgo-traced.blend'));bpy.ops.render.render(write_still=True)
print('TRACED_GINKGO',len(data['position'])//3,len(data['index'])//3)
