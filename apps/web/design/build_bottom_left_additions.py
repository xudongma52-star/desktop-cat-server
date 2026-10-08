"""确认左下三组新增植物 alpha 轮廓浅浮雕；厚度与卷曲为设计参数，不从颜色亮度推算。"""
import bpy, json, math
import numpy as np
from pathlib import Path
ROOT=Path('D:/repository');OUT=ROOT/'tmp/bottom-left-additions-20261007';OUT.mkdir(exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
im=bpy.data.images.load(str(ROOT/'apps/web/src/assets/bottom-left-additions-cutout.png'));im.pack()
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
    # 正面完整植株沿原左侧位置映射；统一像素比例，保留新设计整体构图。

    wx=(-.006+x/W*.56-.5)*8*16/9
    wy=-4.+y/H*3.6
    if wy< -3.7:wy=-3.7+(wy+3.7)*1.6
    return wx,wy
verts=[];uv=[];faces=[];ids={}
# 每片小叶分别指定连接点、中脉和尖部，三叶草以独立叶组作柔和腹部起伏。
leaf_spines=[([(532,450),(511,429),(491,399)],22), ([(532,450),(565,431),(590,406)],20),
             ([(508,549),(477,535),(445,510)],24), ([(508,572),(548,561),(588,551)],20),
             ([(478,656),(416,629),(382,610)],20), ([(470,687),(522,655),(555,636)],20),
             ([(459,759),(411,735),(384,714)],22), ([(459,759),(499,740),(530,714)],23),
             ([(454,846),(416,807),(386,780)],23), ([(454,846),(503,810),(531,784)],23)]
clover_centers=[(276,750,62),(365,821,53),(249,876,61)]
def surface_height(px,py,d):
    sy=H-py
    z=.005+.011*d
    for cx,cy,r in clover_centers:
        q=math.hypot(px-cx,sy-cy)/r
        if q<1.15:z=max(z,.009+.026*max(0.,1-q*q))
    for path,width in leaf_spines:
        for a,b in zip(path,path[1:]):
            vx=b[0]-a[0];vy=b[1]-a[1];length2=vx*vx+vy*vy
            t=max(0.,min(1.,((px-a[0])*vx+(sy-a[1])*vy)/length2))
            r=math.hypot(px-a[0]-t*vx,sy-a[1]-t*vy)/width
            if r<1.:z=max(z,.008+.022*math.sin(math.pi*t)*max(0.,1-r*r))
    # 两支穗头的轮廓来自 alpha；细颗粒沿穗轴起伏，不使用颜色亮度。
    if px>580 and sy<780:z=max(z,.008+.022*d)
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
    fine=True
    if not fine and y%2:continue
    step=1 if fine else 2
    for x in range(0,nx-step,step):
        for tri in [((x,y),(x+step,y),(x+step,y+step)),((x,y),(x+step,y+step),(x,y+step))]:
            if all(mask[yy,xx] for xx,yy in tri):faces.append(tuple(vertex(xx,yy) for xx,yy in tri))
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
(ROOT/'apps/web/src/assets/bottom-left-additions.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=(-3.2,-2.2,8));cam=bpy.context.object;cam.data.type='ORTHO';cam.data.ortho_scale=8.3
scene=bpy.context.scene;scene.camera=cam;scene.render.engine='CYCLES';scene.cycles.samples=8;scene.render.film_transparent=True
scene.render.resolution_x=1592;scene.render.resolution_y=988;scene.render.resolution_percentage=100
scene.view_settings.view_transform='Standard';scene.render.filepath=str(OUT/'bottom-left-additions-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/bottom-left-additions.blend'));bpy.ops.render.render(write_still=True)
print('BOTTOM_LEFT_ADDITIONS',len(verts),len(data['index'])//3)

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
cam.location=(-2.26,-2.2,8);cam.data.ortho_scale=5.68
scene.render.film_transparent=False;scene.cycles.samples=24
scene.render.filepath=str(OUT/'bottom-left-additions-shadow-check.png')
bpy.ops.render.render(write_still=True)
