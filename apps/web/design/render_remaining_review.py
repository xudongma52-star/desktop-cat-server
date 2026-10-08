"""Read-only Blender composition diagnostic, not a screenshot of the webpage.

Existing accepted mesh/UV files are only read. Render copies apply a uniform
fully-revealed web height and responsive x scale; actual mouse states still
require review in the normal running page.
"""
import bpy,json,math
import numpy as np
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[3];ASSETS=ROOT/'apps/web/src/assets'
OUT=ROOT/'tmp/botanical-remaining-20261008';OUT.mkdir(parents=True,exist_ok=True)
entries=[
 ('main-flower','flower-approved-reference',False),('flower-stems','flower-stems-approved-reference',False),
 ('ginkgo','ginkgo-approved-reference',False),('broadleaf','broadleaf-approved-reference',False),
 ('ivy','ivy-approved-reference',False),('left-sprig-v2','left-sprig-v2-cutout',True),
 ('umbel','umbel-approved-reference',False),('right-pair','right-pair-approved-reference',False),
 ('bottom-layer-without-fern','bottom-layer-approved-reference',False),('gap-sprig','gap-sprig-approved-reference',False),
 ('fern-v3','fern-v3-cutout',True),('bottom-left-additions-v2','bottom-left-additions-cutout',True),
 ('bamboo','bamboo-cutout',True),('shoot-step-01','shoot-step-01-approved',True),
 ('shoot-step-02','shoot-step-02-approved',True),('shoot-step-03','shoot-step-03-approved',True),('shoot-step-04','shoot-step-04-approved',True),
 ('bamboo-tall-01','bamboo-tall-01-approved',True),('bamboo-tall-02','bamboo-tall-02-approved',True),
 ('center-bamboo-01','center-bamboo-01-approved',True),('center-bamboo-02','center-bamboo-02-approved',True),
 ('birds-wide','birds-wide-approved',False),('birds-gathered','birds-gathered-approved',False),
 *[(slug,slug+'-approved',True) for slug in ['center-bamboo-03','right-bamboo-01','shoot-step-05','shoot-step-06','shoot-step-07','shoot-step-08']]]
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
scale_x=(1499/1049)/(14.222222/8)
for slug,texture_name,rooted in entries:
    data=json.loads((ASSETS/(slug+'.traced.json')).read_text(encoding='utf-8'))
    p=np.asarray(data['position'],dtype=np.float32).reshape(-1,3)
    anchors=np.asarray(data['anchor'],dtype=np.float32)
    weight=np.clip((p[:,1]+4.08)/.53,0,1) if rooted else np.ones(len(p))
    if rooted:weight=weight*weight*(3-2*weight)
    p[:,2]=anchors+(p[:,2]*.82+.12)*weight;p[:,0]*=scale_x
    faces=np.asarray(data['index'],dtype=np.int32).reshape(-1,3)
    mesh=bpy.data.meshes.new(slug+' read-only render copy');mesh.from_pydata(p.tolist(),[],faces.tolist());mesh.update()
    uv=np.asarray(data['uv'],dtype=np.float32).reshape(-1,2)
    layer=mesh.uv_layers.new(name='Existing source UV unchanged')
    indices=np.empty(len(mesh.loops),dtype=np.int32);mesh.loops.foreach_get('vertex_index',indices)
    layer.data.foreach_set('uv',uv[indices].ravel())
    for poly in mesh.polygons:poly.use_smooth=True
    material=bpy.data.materials.new(slug+' source appearance diagnostic');material.use_nodes=True
    nodes=material.node_tree.nodes;bsdf=nodes.get('Principled BSDF');bsdf.inputs['Roughness'].default_value=.88
    texture=nodes.new('ShaderNodeTexImage');texture.image=bpy.data.images.load(str(ASSETS/(texture_name+'.png')))
    material.node_tree.links.new(texture.outputs['Color'],bsdf.inputs['Base Color'])
    emission=nodes.new('ShaderNodeEmission');emission.inputs['Strength'].default_value=1
    material.node_tree.links.new(texture.outputs['Color'],emission.inputs['Color'])
    blend=nodes.new('ShaderNodeMixShader');blend.inputs[0].default_value=.92
    material.node_tree.links.new(bsdf.outputs[0],blend.inputs[1]);material.node_tree.links.new(emission.outputs[0],blend.inputs[2])
    material.node_tree.links.new(blend.outputs[0],nodes.get('Material Output').inputs['Surface'])
    mesh.materials.append(material);obj=bpy.data.objects.new(slug+' / diagnostic only',mesh);bpy.context.collection.objects.link(obj)
    print('REVIEW_COPY',slug,len(p),flush=True)
bpy.ops.mesh.primitive_plane_add(size=30,location=(0,0,-.012));wall=bpy.context.object
wm=bpy.data.materials.new('Matching warm neutral wall');wm.use_nodes=True
wm.node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value=(.54,.53,.48,1)
wm.node_tree.nodes.get('Principled BSDF').inputs['Roughness'].default_value=.97;wall.data.materials.append(wm)
bpy.ops.object.light_add(type='SUN',location=(-5,7,5.3));sun=bpy.context.object;sun.data.energy=3.55;sun.data.angle=.08
sun.rotation_euler=Vector((5,-7,-5.3)).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add(location=(0,0,12));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=8*1499/1049
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=20
scene.world.use_nodes=True;scene.world.node_tree.nodes.get('Background').inputs['Color'].default_value=(.65,.64,.60,1)
scene.world.node_tree.nodes.get('Background').inputs['Strength'].default_value=.4;scene.view_settings.view_transform='Standard'
scene.render.resolution_x=1499;scene.render.resolution_y=1049;scene.render.resolution_percentage=100
scene['diagnostic_note']='Blender uniform reveal, not normal webpage; source projection has baked light'
scene.render.filepath=str(OUT/'all-plants-blender-diagnostic.png');bpy.ops.render.render(write_still=True)
camera.location=(1.13*scale_x,-2.58,12);camera.data.ortho_scale=2.45
scene.render.resolution_x=900;scene.render.resolution_y=1050
scene.render.filepath=str(OUT/'center-overlap-blender-diagnostic.png');bpy.ops.render.render(write_still=True)
camera.location=(5.51*scale_x,-2.14,12);camera.data.ortho_scale=4.0
scene.render.resolution_x=780;scene.render.resolution_y=1050
scene.render.filepath=str(OUT/'right-overlap-blender-diagnostic.png');bpy.ops.render.render(write_still=True)
print('COMPOSITION_DIAGNOSTICS',len(entries),'independent meshes; existing files read only',flush=True)
