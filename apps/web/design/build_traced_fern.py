"""确认左下蕨羽：原图轮廓、独立纹理投影、连续茎柄与可编辑 Blender 场景。"""
import bpy, math, json
from pathlib import Path
from mathutils import Vector

ROOT=Path('D:/repository');OUT=ROOT/'tmp/fern-20261007';OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
image=bpy.data.images.load(str(ROOT/'apps/web/src/assets/fern-approved-reference.png'));image.pack()
W,H=image.size[:];S=8/H
# 整株采用同一映射；叶柄及纹理不逐片缩放，替代底部增补植物；现有主体不参与生成。
def world(q,z):return ((q.x/W-.5)*(8*16/9),(.5-q.y/H)*8,z)
material=bpy.data.materials.new('Approved quiet bottom layer projection');material.use_nodes=True
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
    boundary=smooth(points,True);center=Vector(center);count=len(boundary);rings=10
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



# 原图逐节羽轴位置：向上逐渐收窄，左右羽片错开，尖端和叶缘具有细小裂片。
branch('fern-rachis',[(50,1064),(63,1004),(79,945),(96,884),(116,829),(129,804)],[1.8,1.5,1.2,.9,.6,.1],.014)
def pinna(name,base,tip,width):
    a=Vector(base);b=Vector(tip);d=b-a;n=Vector((-d.y,d.x)).normalized();outline=[tuple(a)]
    # 顺生长方向渐变裂片，原图 UV 保留每个羽片自然的灰绿、浅褐和细纹。
    for t,k in [(.15,.6),(.25,.9),(.31,.45),(.40,1),(.46,.5),(.56,.92),(.62,.4),(.72,.7),(.79,.28),(.87,.38)]:
        outline.append(tuple(a+d*t+n*width*k))
    outline.append(tuple(b))
    for t,k in reversed([(.15,.6),(.25,.9),(.31,.45),(.40,1),(.46,.5),(.56,.92),(.62,.4),(.72,.7),(.79,.28),(.87,.38)]):
        outline.append(tuple(a+d*t-n*width*k*.8))
    leaf(name,outline,tuple(a+d*.48),.020)
    branch(name+'-midrib',[tuple(a),tuple(a+d*.48),tuple(b)],[.55,.35,.03],.011)
for i,(a,l,r,w) in enumerate([
((126,811),(122,807),(130,807),1.1),
((121,822),(113,815),(132,815),1.6),
((116,835),(106,824),(133,826),2.3),
((112,848),(95,834),(137,836),3),
((108,861),(85,846),(142,849),3.8),
((104,876),(76,860),(145,864),4.2),
((98,891),(65,873),(149,881),4.8),
((94,907),(52,885),(153,901),5),
((89,924),(42,902),(157,919),5.5),
((84,941),(29,920),(158,936),5.8),
((80,958),(24,937),(148,958),5.8),
((74,977),(19,955),(139,976),5.5),
((69,996),(18,978),(126,995),5),
((64,1014),(18,999),(118,1015),4.5),
((60,1031),(25,1016),(106,1031),3.8)]):
    pinna('fern-left-'+str(i),a,l,w)
    pinna('fern-right-'+str(i),(a[0]-1,a[1]+3),r,w*.93)
data={'position':[],'normal':[],'uv':[],'index':[],'anchor':[]}
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles();offset=len(data['anchor']);uv={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
    for vertex in mesh.vertices:
        data['position'].extend(round(v,7) for v in vertex.co);data['normal'].extend(round(v,7) for v in vertex.normal)
        data['uv'].extend(round(v,7) for v in uv[vertex.index]);data['anchor'].append(-.006)
    for tri in mesh.loop_triangles:data['index'].extend(offset+v for v in tri.vertices)
(ROOT/'apps/web/src/assets/fern.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=world(Vector((W/2,H/2)),8));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=8*16/9
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=8
scene.render.resolution_x=W;scene.render.resolution_y=H;scene.render.resolution_percentage=100;scene.render.pixel_aspect_x=(16/9)/(W/H)
scene.render.film_transparent=True;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.render.filepath=str(OUT/'fern-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/fern-traced.blend'));bpy.ops.render.render(write_still=True)
print('TRACED_FERN',W,H,len(data['anchor']),len(data['index'])//3)
