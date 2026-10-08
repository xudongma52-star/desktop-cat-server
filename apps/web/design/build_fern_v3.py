"""高清蕨叶 alpha 轮廓浅浮雕；厚度与卷曲为设计参数，不从颜色亮度推算。"""
import bpy, json, math
import numpy as np
from pathlib import Path
ROOT=Path('D:/repository');OUT=ROOT/'tmp/fern-v3-20261007';OUT.mkdir(exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
im=bpy.data.images.load(str(ROOT/'apps/web/src/assets/fern-v3-cutout.png'));im.pack()
W,H=im.size[:];pixels=np.asarray(im.pixels[:],dtype=np.float32).reshape(H,W,4)
STEP=2
# 去除生成抠图中的半透明墙面光晕；仅保留实物轮廓，纹理仍使用原有 alpha。
# 采样邻域覆盖细羽轴；只用轮廓 alpha，防止两像素网格把细茎截成断点。
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
    # 正面参考完整植株落在原左下蕨羽范围；原图根部与上尖均保留。
    return ((.008+x/W*.125-.5)*8*16/9,(.5-(.755+(1-y/H)*.245))*8)
verts=[];uv=[];faces=[];ids={}
def vertex(x,y):
    key=(x,y)
    if key in ids:return ids[key]
    px=x*STEP;py=y*STEP;wx,wy=xy(px,py)
    d=min(1.,float(distance[y,x])/7.)
    # .003 模型单位约对应设计薄片 .4mm；弯曲约 2–6mm，边缘薄且连续。
    curl=(.012+.023*(.5+.5*math.sin(px*.012+py*.007)))*min(1.,d*3.)
    z=.004+.009*d+curl
    i=len(verts);ids[key]=i;verts.append((wx,wy,z));uv.append((px/W,py/H));return i
for y in range(ny-1):
    for x in range(nx-1):
        for tri in [((x,y),(x+1,y),(x+1,y+1)),((x,y),(x+1,y+1),(x,y+1))]:
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
mesh=bpy.data.meshes.new('Exact alpha fern silhouette with designed thin shell');mesh.from_pydata(verts,[],faces);mesh.update()
for p in mesh.polygons:p.use_smooth=True
layer=mesh.uv_layers.new(name='HD source UV')
for loop in mesh.loops:layer.data[loop.index].uv=uv[loop.vertex_index]
obj=bpy.data.objects.new('Fern HD front master thin relief',mesh);bpy.context.collection.objects.link(obj)
obj['design_units']='250mm height; lamina 0.3-0.6mm; curl 2-6mm, approximate scaled design'
material=bpy.data.materials.new('HD fern exact source');material.use_nodes=True
nodes=material.node_tree.nodes;nodes.clear();tex=nodes.new('ShaderNodeTexImage');tex.image=im
em=nodes.new('ShaderNodeEmission');out=nodes.new('ShaderNodeOutputMaterial');material.node_tree.links.new(tex.outputs['Color'],em.inputs['Color']);material.node_tree.links.new(em.outputs[0],out.inputs['Surface']);mesh.materials.append(material)
mesh.calc_loop_triangles();data={'position':[],'normal':[],'uv':[],'anchor':[-.006]*len(verts),'index':[]}
for v in mesh.vertices:data['position'].extend(round(float(c),7) for c in v.co);data['normal'].extend(round(float(c),7) for c in v.normal)
for u in uv:data['uv'].extend(round(float(c),7) for c in u)
for tri in mesh.loop_triangles:data['index'].extend(tri.vertices[:])
(ROOT/'apps/web/src/assets/fern-v3.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=(-6.107, -3.02,8));cam=bpy.context.object;cam.data.type='ORTHO';cam.data.ortho_scale=2.25
scene=bpy.context.scene;scene.camera=cam;scene.render.engine='CYCLES';scene.cycles.samples=8;scene.render.film_transparent=True
scene.render.resolution_x=900;scene.render.resolution_y=1300;scene.render.resolution_percentage=100
scene.view_settings.view_transform='Standard';scene.render.filepath=str(OUT/'fern-v3-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/fern-v3.blend'));bpy.ops.render.render(write_still=True)
print('FERN_V3',len(verts),len(data['index'])//3)
