"""在 Blender 中制作中央两朵浅浮雕花；固定参考为用户已确认的生成图。

花瓣保留独立对象及细分/薄片修改器，交付可编辑 blend；网页只加载烘焙后的
轻量几何，沿用现有材质、鼠标深度场及阴影，不把离线灯光烘焙成背景图片。
"""
import bpy
import json
import math
from pathlib import Path
from mathutils import Vector

ROOT = Path('D:/repository')
OUT = ROOT / 'tmp/reference-flowers-blender-20261006'
OUT.mkdir(parents=True, exist_ok=True)
REFERENCE = Path('C:/Users/MAX/.codex/generated_images/01a1115a-3f6d-7ef1-bd50-96f77bc492cf/exec-1b97d707-f082-44fd-b110-5fdf0becf901.png')
ASSET = ROOT / 'apps/web/src/assets/central-flowers.blender.json'
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
model = bpy.data.collections.new('Reference flowers — editable petals')
bpy.context.scene.collection.children.link(model)

stone = bpy.data.materials.new('Warm white plaster')
stone.diffuse_color = (.72, .70, .66, 1)
stone.use_nodes = True
bsdf = stone.node_tree.nodes.get('Principled BSDF')
bsdf.inputs['Base Color'].default_value = (.72, .70, .66, 1)
bsdf.inputs['Roughness'].default_value = .95

def register(obj):
    for collection in list(obj.users_collection):
        collection.objects.unlink(obj)
    model.objects.link(obj)
    obj.data.materials.append(stone)
    for polygon in obj.data.polygons:
        polygon.use_smooth = True
    return obj

def patch(name, rows, columns, sample, thickness=0):
    vertices, faces = [], []
    for i in range(rows + 1):
        for j in range(columns + 1):
            vertices.append(sample(i / rows, j / columns * 2 - 1))
    for i in range(rows):
        for j in range(columns):
            a = i * (columns + 1) + j
            b = a + columns + 1
            faces.append((a, b, b + 1, a + 1))
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    model.objects.link(obj)
    obj.data.materials.append(stone)
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    subdivision = obj.modifiers.new('Smooth authored control surface', 'SUBSURF')
    subdivision.levels = 1
    subdivision.render_levels = 2
    if thickness:
        solidify = obj.modifiers.new('Petal paper thickness', 'SOLIDIFY')
        solidify.thickness = thickness
        solidify.offset = -1
    return obj

def flower(name, center, descriptors, scale=1, squash=1, tilt=0):
    def local(x, y, z):
        return (center[0]+x*scale, center[1]+y*scale*squash, center[2]+z*scale+y*scale*tilt)

    # 每片按参考图指定不同的轴向、宽度、弯曲和叠压；不是等角度旋转同一个花瓣。
    for i, (degrees, length, breadth, drift, curl, layer) in enumerate(descriptors):
        angle = math.radians(degrees)
        ca, sa = math.cos(angle), math.sin(angle)
        def sample(t, u, i=i, length=length, breadth=breadth, drift=drift, curl=curl, layer=layer, ca=ca, sa=sa):
            opening = min(1, t/.53)
            opening = opening*opening*(3-2*opening)
            width = .010+(breadth-.010)*opening
            # 圆端由完整半圆控制，横向两侧不收成叶片尖端。
            cap = breadth*(1-math.sqrt(max(0, 1-u*u)))
            r = .035+t*(length-.035-cap)
            cross = width*u+drift*math.sin(math.pi*t)
            envelope = math.sin(math.pi*t)
            cup = curl*envelope*(u*u)
            fold = .003*envelope*math.exp(-((u-.22*math.sin(i*1.3))/.20)**2)
            edge = .0007*envelope*abs(u)**6*math.sin(13*t+i)
            veins = .0011*envelope*(1-u*u)*math.sin(28*u+2*t+i*.6)
            z = layer+.007*envelope+cup+fold+edge+veins+.003*t*u*math.sin(i*1.2)
            return local(r*ca-cross*sa, r*sa+cross*ca, z)
        obj = patch(f'{name} / petal {i+1:02}', 12, 8, sample, .0012*scale)
        obj['reference_angle_degrees'] = degrees
        obj['role'] = 'individually shaped thin overlapping petal'

    # 花盘和花粒均是几何；旋转、换光和鼠标抬起时仍有真实表面。
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=12, location=local(0,0,.013))
    disk = register(bpy.context.object)
    disk.name = f'{name} / receptacle'
    disk.scale = (.071*scale, .071*scale*squash, .013*scale)
    for i in range(77):
        r=.066*math.sqrt((i+.5)/77)
        angle=i*2.399963229728653
        z=.022+.010*(1-(r/.071)**2)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=8, ring_count=6, location=local(r*math.cos(angle),r*math.sin(angle),z))
        grain=register(bpy.context.object)
        grain.name=f'{name} / floret {i+1:02}'
        size=.0073*(1+.18*math.sin(i*1.8))*scale
        grain.scale=(size,size*.82,size*.95)
        grain.rotation_euler.z=angle

    # 花托位于花盘后方，与当前真实茎端同坐标；浅萼片向瓣根包合。
    for side in [-1,1]:
        def calyx(t,u,side=side):
            e=math.sin(math.pi*t)
            return local(side*.055*t+u*.019*e,-.12+.15*t,-.015+.003*e*(1-u*u))
        patch(f'{name} / calyx {side}',10,6,calyx,.001)

# 主花八瓣的轴向取自固定参考：上、右上、右、右下、后层下瓣、左下、左、左上。
flower('Main blossom', (.14,.56,.017), [
    (-67,.435,.104,.011,.010,-.001),
    (102,.455,.111,-.010,.016,.001),
    (65,.426,.111,.014,.013,.004),
    (15,.442,.137,.005,.009,.002),
    (-36,.428,.127,-.012,.010,.006),
    (-104,.399,.111,-.004,.012,.009),
    (-163,.420,.137,.010,.011,.007),
    (145,.412,.114,-.006,.015,.003),
])
# 次花在参考图里倾斜且前方一瓣回卷，五瓣不作主花缩小复制。
flower('Side blossom',(1.28,-.24,.023),[
    (96,.430,.119,-.009,.015,.002),
    (12,.425,.139,.014,.012,.004),
    (-47,.439,.122,-.009,.032,.008),
    (-132,.294,.106,.024,.046,.018),
    (151,.385,.112,-.010,.018,.006),
],scale=.70,squash=.89,tilt=.035)

if REFERENCE.exists():
    image=bpy.data.images.load(str(REFERENCE))
    image.pack()
    ref=bpy.data.objects.new('Approved image — composition and petal reference',None)
    bpy.context.scene.collection.objects.link(ref)
    ref.empty_display_type='IMAGE'
    ref.data=image
    ref.empty_display_size=2.2
    ref.location=(.7,.3,-.08)
    ref.hide_render=True

# 烘焙已求值修改器，保留正确向外法线，输出网页现有合并几何所需的 uv/index。
depsgraph=bpy.context.evaluated_depsgraph_get()
positions,normals,indices=[],[],[]
for obj in model.objects:
    evaluated=obj.evaluated_get(depsgraph)
    mesh=evaluated.to_mesh()
    mesh.calc_loop_triangles()
    offset=len(positions)//3
    normal_matrix=obj.matrix_world.to_3x3().inverted().transposed()
    for vertex in mesh.vertices:
        position=obj.matrix_world@vertex.co
        normal=(normal_matrix@vertex.normal).normalized()
        positions.extend(round(v,7) for v in position)
        normals.extend(round(v,7) for v in normal)
    for triangle in mesh.loop_triangles:
        indices.extend(offset+v for v in triangle.vertices)
    evaluated.to_mesh_clear()
payload={'position':positions,'normal':normals,'index':indices}
ASSET.write_text(json.dumps(payload,separators=(',',':')),encoding='utf-8')
(OUT/'mesh-stats.json').write_text(json.dumps({'vertices':len(positions)//3,'triangles':len(indices)//3,'bytes':ASSET.stat().st_size},indent=2))
bpy.ops.object.select_all(action='DESELECT')
for obj in model.objects:
    obj.select_set(True)
bpy.context.view_layer.objects.active=next(iter(model.objects))
bpy.ops.export_scene.gltf(filepath=str(OUT/'central-flowers.glb'),export_format='GLB',use_selection=True,export_apply=True)

# 独立造型验收使用 Cycles 柔和侧光；灯光和墙面不导出到网页植物几何。
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.012))
wall=bpy.context.object
wall.name='Preview wall only'
wall.data.materials.append(stone)
bpy.ops.object.camera_add(location=(.69,.24,8))
camera=bpy.context.object
camera.data.type='ORTHO'
camera.data.ortho_scale=2.25
bpy.context.scene.camera=camera
bpy.ops.object.light_add(type='AREA',location=(-3,4,6))
light=bpy.context.object
light.data.energy=450
light.data.shape='DISK'
light.data.size=3
light.rotation_euler=(Vector((.6,.2,0))-light.location).to_track_quat('-Z','Y').to_euler()
scene=bpy.context.scene
scene.world.color=(.35,.35,.35)
scene.world.use_nodes=True
scene.world.node_tree.nodes.get('Background').inputs['Color'].default_value=(.72,.70,.66,1)
scene.world.node_tree.nodes.get('Background').inputs['Strength'].default_value=.55
scene.render.engine='CYCLES'
scene.cycles.samples=32
scene.cycles.use_denoising=True
scene.render.resolution_x=1000
scene.render.resolution_y=760
scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.render.filepath=str(OUT/'flowers-cycles.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/central-flowers.blend'))
bpy.ops.render.render(write_still=True)
print('FLOWER_EXPORT_COMPLETE',len(positions)//3,len(indices)//3,str(ASSET))
