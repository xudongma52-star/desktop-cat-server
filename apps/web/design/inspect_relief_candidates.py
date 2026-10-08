"""白模侧光审视候选素材；不覆盖原始下载文件。"""
import bpy
import json
from mathutils import Vector
from pathlib import Path

output = Path('D:/repository/tmp/corner-scene-review-20261003')
sources = Path('D:/compile/corner-round4/source')
for author, uid in [('kenchoo', 'kenchoo-bicolor-cat')]:
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    bpy.ops.import_scene.gltf(filepath=str(sources / (uid + '.glb')))
    meshes = [obj for obj in bpy.context.scene.objects if obj.type == 'MESH']
    bpy.context.view_layer.update()
    print('RELIEF_CANDIDATE', author, json.dumps([{'name':obj.name, 'vertices':len(obj.data.vertices), 'bounds':[list(obj.matrix_world @ Vector(corner)) for corner in obj.bound_box]} for obj in meshes]))
    material = bpy.data.materials.new('审视用灰白石膏')
    material.diffuse_color = (.65,.64,.60,1)
    material.use_nodes = True
    bsdf = material.node_tree.nodes.get('Principled BSDF')
    bsdf.inputs['Base Color'].default_value = (.65,.64,.60,1)
    bsdf.inputs['Roughness'].default_value = .94
    for obj in meshes:
        obj.data.materials.clear()
        obj.data.materials.append(material)
        for polygon in obj.data.polygons:
            polygon.material_index = 0
            polygon.use_smooth = True
    points = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    lower = Vector([min(p[i] for p in points) for i in range(3)])
    upper = Vector([max(p[i] for p in points) for i in range(3)])
    center = (lower + upper) / 2
    size = max(upper-lower)
    bpy.ops.object.camera_add(location=center + Vector((1.4,-2.3,1.6))*size)
    camera = bpy.context.object
    camera.rotation_euler = (center-camera.location).to_track_quat('-Z','Y').to_euler()
    camera.data.type = 'ORTHO'
    camera.data.ortho_scale = size*1.4
    bpy.context.scene.camera = camera
    bpy.ops.object.light_add(type='AREA', location=center+Vector((-1.3,-1.2,2.0))*size)
    light = bpy.context.object
    light.data.energy = 650*size*size
    light.data.shape = 'DISK'
    light.data.size = size*1.8
    light.rotation_euler = (center-light.location).to_track_quat('-Z','Y').to_euler()
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 24
    scene.cycles.use_denoising = True
    scene.world.color = (.25,.25,.25)
    scene.render.resolution_x = 1000
    scene.render.resolution_y = 800
    scene.render.resolution_percentage = 100
    scene.view_settings.view_transform = 'AgX'
    scene.render.image_settings.file_format = 'PNG'
    scene.render.filepath = str(output / (author+'-white-candidate.png'))
    bpy.ops.render.render(write_still=True)
