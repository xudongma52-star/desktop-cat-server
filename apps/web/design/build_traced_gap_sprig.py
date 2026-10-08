"""确认空隙五叶小枝：原图轮廓、独立纹理投影、连续茎柄与可编辑 Blender 场景。"""
import bpy, math, json
from pathlib import Path
from mathutils import Vector

ROOT=Path('D:/repository');OUT=ROOT/'tmp/gap-sprig-20261007';OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
image=bpy.data.images.load(str(ROOT/'apps/web/src/assets/gap-sprig-approved-reference.png'));image.pack()
W,H=image.size[:];S=8/H
# 整株采用同一映射；叶柄及纹理不逐片缩放，替代底部增补植物；现有主体不参与生成。
# 确认图是局部截图：映射到左侧两条已完成主茎之间的空隙。
def world(q,z):return ((q.x/W*.46-.5)*(8*16/9),(.5-(.525+q.y/H*.475))*8,z)
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



leaf('top-lance',[(911,645),(899,609),(900,579),(916,547),(944,521),(933,554),(934,587),(925,619)],(918,584),.025)
leaf('left-turned',[(906,730),(883,708),(855,696),(823,675),(797,639),(825,658),(858,662),(882,681)],(859,682),.022)
leaf('right-curled',[(940,795),(952,756),(974,727),(1003,709),(1034,706),(1078,711),(1058,716),(1038,713),(1007,739),(975,768)],(990,737),.028)
leaf('left-main',[(891,885),(856,867),(820,842),(784,803),(744,738),(776,749),(815,770),(852,798),(877,838)],(828,803),.030)
leaf('lower-right',[(891,994),(921,946),(952,914),(986,897),(1020,895),(1054,906),(1021,905),(985,926),(950,952),(920,969)],(967,930),.025)
branch('main-curved-stem',[(837,1088),(863,1037),(892,973),(916,905),(927,834),(926,768),(915,693),(911,635)],[4,3.8,3.3,2.8,2.6,2.3,1.7,1],.016)
branch('left-upper-petiole',[(921,772),(908,734),(895,709)],[2,1.5,.7],.013)
branch('right-upper-petiole',[(923,845),(941,796),(957,775)],[2.2,1.8,.8],.013)
branch('left-main-petiole',[(914,922),(892,882),(870,848)],[2.2,1.8,.8],.013)
branch('right-lower-petiole',[(858,1052),(891,995),(922,966)],[2.5,2,.8],.013)
branch('grass-left',[(838,1088),(821,1020),(796,940),(751,866)],[1.8,1.5,1,.1],.008)
branch('grass-right',[(864,1088),(927,1037),(1001,995),(1063,988),(1119,999)],[2,1.5,1,.5,.1],.008)
data={'position':[],'normal':[],'uv':[],'index':[],'anchor':[]}
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles();offset=len(data['anchor']);uv={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
    for vertex in mesh.vertices:
        data['position'].extend(round(v,7) for v in vertex.co);data['normal'].extend(round(v,7) for v in vertex.normal)
        data['uv'].extend(round(v,7) for v in uv[vertex.index]);data['anchor'].append(-.006)
    for tri in mesh.loop_triangles:data['index'].extend(offset+v for v in tri.vertices)
(ROOT/'apps/web/src/assets/gap-sprig.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=(0,0,8));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=8*16/9
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=8
scene.render.resolution_x=1111;scene.render.resolution_y=790;scene.render.resolution_percentage=100;scene.render.pixel_aspect_x=(16/9)/(1111/790)
scene.render.film_transparent=True;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.render.filepath=str(OUT/'gap-sprig-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/gap-sprig-traced.blend'));bpy.ops.render.render(write_still=True)
print('TRACED_GAP_SPRIG',W,H,len(data['anchor']),len(data['index'])//3)
