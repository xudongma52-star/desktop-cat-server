"""确认五叶小花序 alpha 轮廓浅浮雕；厚度与卷曲为设计参数，不从颜色亮度推算。"""
import bpy, json, math
import numpy as np
from pathlib import Path
ROOT=Path('D:/repository');OUT=ROOT/'tmp/left-sprig-v2-20261007';OUT.mkdir(exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
im=bpy.data.images.load(str(ROOT/'apps/web/src/assets/left-sprig-v2-cutout.png'));im.pack()
W,H=im.size[:];pixels=np.asarray(im.pixels[:],dtype=np.float32).reshape(H,W,4)
STEP=1
# 去除生成抠图中的半透明墙面光晕；仅保留实物轮廓，纹理仍使用原有 alpha。
# 每像素建立轮廓，花序细柄使用单像素网格，较宽叶面使用两像素网格。
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
    return (-5.43+(x-255)*.00303,-3.72+(y-32)*.00303)
verts=[];uv=[];faces=[];ids={}
# 五片叶各自的根部、弯曲中脉、叶尖及半宽（图像像素坐标）。
# 沿生长方向组织曲面，根部回到茎柄高度；叶脉颜色仍由高清 UV 保存。
leaf_spines=[([(439,581),(466,459),(544,319)],35),
             ([(390,970),(316,816),(220,627)],48),
             ([(418,1080),(478,949),(557,849)],32),
             ([(340,1655),(233,1400),(96,1126)],68),
             ([(388,1515),(488,1370),(620,1270)],43)]
def surface_height(px,py,d):
    top_y=H-py
    best=None
    for path,width in leaf_spines:
        lengths=[math.dist(a,b) for a,b in zip(path,path[1:])];total=sum(lengths);offset=0
        for a,b,length in zip(path,path[1:],lengths):
            vx=b[0]-a[0];vy=b[1]-a[1]
            t=max(0.,min(1.,((px-a[0])*vx+(top_y-a[1])*vy)/(length*length)))
            cross=math.hypot(px-a[0]-t*vx,top_y-a[1]-t*vy)
            score=cross/width
            if best is None or score<best[0]:best=(score,(offset+t*length)/total)
            offset+=length
    # 叶根连续，中腹沿中脉展开，尖部稍卷；不从像素明暗反推高度。
    if best and best[0]<1.25:
        r,along=best;growth=math.sin(math.pi*along)
        return .012+.028*growth*max(0.,1-r*r)+.008*along**3*min(1.,d*3.)
    return .005+.014*d
def vertex(x,y):
    key=(x,y)
    if key in ids:return ids[key]
    px=x*STEP;py=y*STEP;wx,wy=xy(px,py)
    d=min(1.,float(distance[y,x])/12.)
    # 壳厚 .003，起伏由逐叶中脉设计；没有从参考测得毫米尺寸。
    z=surface_height(px,py,d)
    i=len(verts);ids[key]=i;verts.append((wx,wy,z));uv.append((px/W,py/H));return i
for y in range(ny-2):
    fine=y>=H-380
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
(ROOT/'apps/web/src/assets/left-sprig-v2.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=(-4.98,-.51,8));cam=bpy.context.object;cam.data.type='ORTHO';cam.data.ortho_scale=6.65
scene=bpy.context.scene;scene.camera=cam;scene.render.engine='CYCLES';scene.cycles.samples=8;scene.render.film_transparent=True
scene.render.resolution_x=724;scene.render.resolution_y=2172;scene.render.resolution_percentage=100
scene.view_settings.view_transform='Standard';scene.render.filepath=str(OUT/'left-sprig-v2-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/left-sprig-v2.blend'));bpy.ops.render.render(write_still=True)
print('LEFT_SPRIG_V2',len(verts),len(data['index'])//3)
