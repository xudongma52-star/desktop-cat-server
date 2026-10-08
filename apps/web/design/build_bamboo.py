"""确认左侧双竹 alpha 轮廓浅浮雕；厚度与卷曲为设计参数，不从颜色亮度推算。"""
import bpy, json, math
import numpy as np
from pathlib import Path
ROOT=Path('D:/repository');OUT=ROOT/'tmp/bamboo-20261007';OUT.mkdir(exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
im=bpy.data.images.load(str(ROOT/'apps/web/src/assets/bamboo-cutout.png'));im.pack()
W,H=im.size[:];pixels=np.asarray(im.pixels[:],dtype=np.float32).reshape(H,W,4)
STEP=1
# 去除生成抠图中的半透明墙面光晕；仅保留实物轮廓，纹理仍使用原有 alpha。
# 每像素建立轮廓，花序细柄使用单像素网格，保留细小叶柄与花梗。
# 纹理保持原始高清尺寸，不让大叶面无意义地增加网格和下载体积。
alpha=pixels[:,:,3]
expanded=alpha.copy()
for dy,dx in [(0,1),(0,-1),(1,0),(-1,0)]:
    expanded=np.maximum(expanded,np.roll(alpha,(dy,dx),(0,1)))
mask=expanded[::STEP,::STEP]>.90
ny,nx=mask.shape;distance=np.zeros_like(mask,dtype=np.float32);inside=mask.copy()
for k in range(12):
    distance+=inside
    next_inside=np.zeros_like(inside)
    next_inside[1:-1,1:-1]=inside[1:-1,1:-1]&inside[:-2,1:-1]&inside[2:,1:-1]&inside[1:-1,:-2]&inside[1:-1,2:]
    inside=next_inside
def xy(x,y):
    # 提取图按固定前视构图映射至页面最左侧；竹顶及根部锚定原确认图范围。
    aligned_x=200.+(x-185.)*.8
    wx=((aligned_x-148.)/428.*.18-.5)*8*16/9
    wy=-4.+y/H*8.*.9
    if wy< -3.7:wy=-3.7+(wy+3.7)*1.7
    return wx,wy
verts=[];uv=[];faces=[];ids={}
# 四簇竹叶的独立中脉；根部起伏平滑连接竹枝，竹节与秆的原色由 UV 保留。
leaf_spines=[([(245,360),(320,286),(391,256)],23),
             ([(248,360),(363,327),(471,371)],26),
             ([(250,365),(365,440),(478,551)],24),
             ([(255,367),(318,438),(371,550)],23),
             ([(204,793),(269,719),(346,687)],22),
             ([(206,793),(320,741),(420,790)],22),
             ([(210,793),(280,853),(331,912)],20),
             ([(298,1071),(403,1006),(507,952)],22),
             ([(298,1075),(435,1044),(578,1168)],27),
             ([(302,1080),(385,1155),(410,1240)],23),
             ([(254,1510),(320,1436),(393,1420)],20),
             ([(254,1510),(359,1489),(445,1538)],22),
             ([(258,1515),(309,1585),(330,1650)],20)]
def surface_height(px,py,d):
    sy=H-py;z=.006+.015*d
    for path,width in leaf_spines:
        for a,b in zip(path,path[1:]):
            vx=b[0]-a[0];vy=b[1]-a[1];length2=vx*vx+vy*vy
            t=max(0.,min(1.,((px-a[0])*vx+(sy-a[1])*vy)/length2))
            r=math.hypot(px-a[0]-t*vx,sy-a[1]-t*vy)/width
            if r<1.:z=max(z,.008+.026*math.sin(math.pi*t)*max(0.,1-r*r))
    return z

def vertex(x,y):
    key=(x,y)
    if key in ids:return ids[key]
    px=x*STEP;py=y*STEP;wx,wy=xy(px,py)
    d=min(1.,float(distance[y,x])/12.)
    # 壳厚 .003，起伏由逐叶中脉设计；没有从参考测得毫米尺寸。
    z=surface_height(px,py,d)
    i=len(verts);ids[key]=i;verts.append((wx,wy,z));uv.append((px/W,py/H));return i
for y in range(ny-2):
    fine=False
    if not fine and y%2:continue
    step=1 if fine else 2
    for x in range(0,nx-step,step):
        for tri in [((x,y),(x+step,y),(x+step,y+step)),((x,y),(x+step,y+step),(x,y+step))]:
            if all(mask[yy,xx] for xx,yy in tri):faces.append(tuple(vertex(xx,yy) for xx,yy in tri))
# 去除透明提取边缘中的微小孤立残片，不将其变成悬空枝尖或单独投影。
parents=list(range(len(verts)))
def find_root(i):
    while parents[i]!=i:
        parents[i]=parents[parents[i]];i=parents[i]
    return i
for a,b,c in faces:
    for other in (b,c):parents[find_root(other)]=find_root(a)
counts={}
for i in range(len(verts)):
    root=find_root(i);counts[root]=counts.get(root,0)+1
faces=[f for f in faces if counts[find_root(f[0])]>=90]
# 封闭薄片背面与侧缘，保留真实几何投影；不是一个矩形贴图平面。
front_count=len(verts);front_faces=faces[:];edges={}
for f in front_faces:
    for a,b in zip(f,(f[1],f[2],f[0])):
        key=tuple(sorted((a,b)));edges[key]=edges.get(key,0)+1
verts += [(x,y,z-.003) for x,y,z in verts[:]];uv+=uv[:]
faces += [tuple(i+front_count for i in reversed(f)) for f in front_faces]
for (a,b),count in edges.items():
    if count==1:faces.append((a,b,b+front_count,a+front_count))
mesh=bpy.data.meshes.new('Exact alpha sprig silhouette with designed thin shell');mesh.from_pydata(verts,[],faces);mesh.update()
for p in mesh.polygons:p.use_smooth=True
layer=mesh.uv_layers.new(name='HD source UV')
for loop in mesh.loops:layer.data[loop.index].uv=uv[loop.vertex_index]
obj=bpy.data.objects.new('Sprig HD front master thin relief',mesh);bpy.context.collection.objects.link(obj)
obj['design_units']='Designed shallow relief: shell .003 units; surface .004-.048 units. Not measured physical thickness.'
material=bpy.data.materials.new('HD sprig exact source');material.use_nodes=True
nodes=material.node_tree.nodes;nodes.clear();tex=nodes.new('ShaderNodeTexImage');tex.image=im
em=nodes.new('ShaderNodeEmission');out=nodes.new('ShaderNodeOutputMaterial');material.node_tree.links.new(tex.outputs['Color'],em.inputs['Color']);material.node_tree.links.new(em.outputs[0],out.inputs['Surface']);mesh.materials.append(material)
mesh.calc_loop_triangles();data={'position':[],'normal':[],'uv':[],'anchor':[-.006]*len(verts),'index':[]}
for v in mesh.vertices:data['position'].extend(round(float(c),7) for c in v.co);data['normal'].extend(round(float(c),7) for c in v.normal)
for u in uv:data['uv'].extend(round(float(c),7) for c in u)
for tri in mesh.loop_triangles:data['index'].extend(tri.vertices[:])
(ROOT/'apps/web/src/assets/bamboo.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=(-6.01,0,8));cam=bpy.context.object;cam.data.type='ORTHO';cam.data.ortho_scale=8.4
scene=bpy.context.scene;scene.camera=cam;scene.render.engine='CYCLES';scene.cycles.samples=8;scene.render.film_transparent=True
scene.render.resolution_x=725;scene.render.resolution_y=2170;scene.render.resolution_percentage=100
scene.view_settings.view_transform='Standard';scene.render.filepath=str(OUT/'bamboo-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/bamboo.blend'));bpy.ops.render.render(write_still=True)
print('BAMBOO',len(verts),len(data['index'])//3)

# 独立投影检查：模拟当前无迷雾状态的共享高度与根部贴墙权重。
# 此图是 Blender 模型检查图，不能替代网页正常尺寸或鼠标三态验收。
for v in mesh.vertices:
    t=max(0.,min(1.,(v.co.y+4.08)/.53));weight=t*t*(3.-2.*t)
    v.co.z=-.006+(v.co.z*.82+.12)*weight
obj.scale.x=.705
bpy.ops.mesh.primitive_plane_add(size=20,location=(-2.25,-2.2,-.01))
wall=bpy.context.object;wall_material=bpy.data.materials.new('Grey mineral wall QA');wall_material.diffuse_color=(.40,.39,.37,1);wall.data.materials.append(wall_material)
from mathutils import Vector
bpy.ops.object.light_add(type='SUN',location=(-5,7,5.3));light=bpy.context.object;light.data.energy=1.8;light.data.angle=.1
light.rotation_euler=Vector((5,-7,-5.3)).to_track_quat('-Z','Y').to_euler()
scene.world.color=(.17,.17,.17)
cam.location=(-4.41,0.,8);cam.data.ortho_scale=8.
scene.render.film_transparent=False;scene.cycles.samples=24
scene.render.filepath=str(OUT/'bamboo-shadow-check.png')
bpy.ops.render.render(write_still=True)
