"""Blender: confirmed silhouettes, feather relief, source UVs and editable parts.

The page has a fixed orthographic front view. Side views illustrate the relief's
real depth; the AI angle sheet is auxiliary, not evidence of the original back.
"""
import bpy, json, math
from pathlib import Path
from mathutils import Vector
from mathutils.geometry import tessellate_polygon

ROOT = Path(__file__).resolve().parents[3]
DESIGN = ROOT / 'apps/web/design'
ASSETS = ROOT / 'apps/web/src/assets'
OUT = ROOT / 'tmp/birds-relief-20261007'
OUT.mkdir(parents=True, exist_ok=True)
specification = json.loads((DESIGN/'birds-contours.json').read_text(encoding='utf-8'))
W, H = specification['source_size']
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)

def distance_segment(x, y, line):
    ax, ay, bx, by = line
    vx, vy = bx-ax, by-ay
    t = max(0, min(1, ((x-ax)*vx+(y-ay)*vy)/max(1e-8, vx*vx+vy*vy)))
    return math.hypot(x-ax-t*vx, y-ay-t*vy), t

def ellipse(x, y, definition):
    cx, cy, rx, ry = definition
    return math.exp(-((x-cx)/rx)**2-((y-cy)/ry)**2)

def height(x, y, part, spec):
    edges = spec['parts'][part]
    d = min(distance_segment(x,y,(*a,*b))[0] for a,b in zip(edges,edges[1:]+edges[:1]))
    taper = 1-math.exp(-d/2.5)
    # Thin free edges, round torso and head, shallow individual feather ridges.
    # Every point retains source xy; relief changes only thickness, not the pose.
    thickness = {'far-wing':.040,'near-wing':.048,'body-head':.035,'tail':.019,'feet':.015}[part]
    if part == 'body-head':
        thickness += .095*ellipse(x,y,spec['body'])+.060*ellipse(x,y,spec['head'])
    if part in ['far-wing','near-wing']:
        thickness += .019*ellipse(x,y,spec['shoulder'])
    key = {'far-wing':'far_ridges','near-wing':'near_ridges','tail':'tail_ridges'}.get(part)
    if key:
        ridges = []
        for line in spec[key]:
            distance, t = distance_segment(x,y,line)
            ridges.append(math.exp(-(distance/2.4)**2)*math.sin(math.pi*t))
        thickness += (.010 if part != 'tail' else .006)*max(ridges,default=0)
    result=.004+taper*thickness
    if spec['name']=='wide' and part in ['far-wing','near-wing','body-head']:
        # All adjoining surfaces converge to the same shoulder surface. The
        # lower raised wing must meet the torso in z as well as source xy;
        # otherwise its independent thin edge casts a detached black seam.
        radius=math.hypot((x-784)/23,(y-183)/18)
        transition=max(0,min(1,(radius-.65)/.55))
        join=1-transition*transition*(3-2*transition)
        shoulder=.074+.037*ellipse(x,y,spec['body'])
        result=result*(1-join)+shoulder*join
    return result

def build_part(spec, part, material):
    boundary = [Vector((x,y,0)) for x,y in spec['parts'][part]]
    triangles = tessellate_polygon([boundary])
    points, faces, registry = [], [], {}
    def index(p):
        key = (round(p.x,6),round(p.y,6))
        if key not in registry:
            registry[key]=len(points);points.append((p.x,p.y))
        return registry[key]
    def refine(a,b,c):
        # Bisect the longest edge; shared edges use the same midpoint and retain
        # the traced boundary instead of approximating it with a pixel grid.
        lengths=[(a-b).length_squared,(b-c).length_squared,(c-a).length_squared]
        edge=lengths.index(max(lengths))
        if lengths[edge] <= 3.2**2:
            faces.append((index(a),index(b),index(c)));return
        if edge==0:
            m=(a+b)*.5;refine(a,m,c);refine(m,b,c)
        elif edge==1:
            m=(b+c)*.5;refine(a,b,m);refine(a,m,c)
        else:
            m=(c+a)*.5;refine(a,b,m);refine(m,b,c)
    for a,b,c in triangles:
        # Blender 5.2 returns polygon indices; older mathutils returned vectors.
        if isinstance(a,int):a,b,c=boundary[a],boundary[b],boundary[c]
        refine(a,b,c)
    # Preserve the existing plant layout: the right bird alone sits slightly
    # higher to avoid its tail crossing an already accepted leaf. Source UVs
    # remain unchanged and the field uses these final world coordinates.
    vertices=[((x/W-.5)*14.222222,(.5-y/H)*8+spec.get('world_y_offset',0),height(x,y,part,spec)) for x,y in points]
    mesh=bpy.data.meshes.new(spec['name']+' '+part)
    mesh.from_pydata(vertices,[],faces);mesh.update()
    for polygon in mesh.polygons:
        if polygon.normal.z<0:polygon.flip()
        polygon.use_smooth=True
    mesh.update()
    left,top,right,bottom=spec['crop']
    uv=mesh.uv_layers.new(name='Approved front pixel projection')
    for loop in mesh.loops:
        x,y=points[loop.vertex_index]
        uv.data[loop.index].uv=((x-left)/(right-left),1-(y-top)/(bottom-top))
    obj=bpy.data.objects.new(spec['name']+' / '+part,mesh)
    bpy.context.collection.objects.link(obj);mesh.materials.append(material)
    obj['wall_anchor']=-.006
    obj['source']='birds-approved-front.png'
    obj['projection']='Fixed front; individually traced silhouette and anatomical relief'
    # Native mesh thickness is exported untouched. This view transform is the
    # exact fully revealed web height, retained for Blender shadow references.
    obj.scale.z=.82;obj.location.z=.114
    return obj

all_objects=[]
for spec in specification['birds']:
    texture=bpy.data.images.load(str(ASSETS/spec['texture']));texture.pack()
    material=bpy.data.materials.new('Approved '+spec['name']+' subtle feather colour')
    material.use_nodes=True
    nodes=material.node_tree.nodes;bsdf=nodes.get('Principled BSDF')
    bsdf.inputs['Roughness'].default_value=.85
    tex=nodes.new('ShaderNodeTexImage');tex.image=texture
    material.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])
    objects=[build_part(spec,part,material) for part in ['tail','far-wing','body-head','near-wing','feet']]
    data={'position':[],'normal':[],'uv':[],'index':[],'anchor':[]}
    for obj in objects:
        mesh=obj.data;mesh.calc_loop_triangles();offset=len(data['anchor'])
        uv={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
        for vertex in mesh.vertices:
            data['position'].extend(round(v,7) for v in vertex.co)
            data['normal'].extend(round(v,7) for v in vertex.normal)
            data['uv'].extend(round(v,7) for v in uv[vertex.index])
            data['anchor'].append(-.006)
        for triangle in mesh.loop_triangles:data['index'].extend(offset+v for v in triangle.vertices)
    (ASSETS/f"birds-{spec['name']}.traced.json").write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
    print('EXPORTED',spec['name'],len(data['anchor']),'vertices',len(data['index'])//3,'triangles')
    all_objects.extend(objects)

# A shared wall and directional light make the editable source useful for
# inspecting geometry and actual shadow cast, rather than only a cutout image.
bpy.ops.mesh.primitive_plane_add(size=40,location=(0,0,-.012))
wall=bpy.context.object;wall.name='Wall / shared anchor'
wall_material=bpy.data.materials.new('Warm neutral stone wall');wall_material.diffuse_color=(.65,.64,.60,1)
wall.data.materials.append(wall_material)
bpy.ops.object.light_add(type='AREA',location=(-5,7,5.3))
light=bpy.context.object;light.name='Upper-left shared shadow light';light.data.energy=650;light.data.size=3
light.rotation_euler=(Vector((0,0,0))-light.location).to_track_quat('-Z','Y').to_euler()
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=16
scene.world.color=(.3,.3,.3);scene.view_settings.view_transform='Standard'
scene.render.resolution_x=900;scene.render.resolution_y=630;scene.render.resolution_percentage=100
bpy.ops.object.camera_add(location=(0,0,15))
camera=bpy.context.object;camera.name='Orthographic / confirmed full frame'
camera.data.type='ORTHO';camera.data.ortho_scale=14.222222;scene.camera=camera
scene['approved_image']='birds-approved-front.png'
scene['angle_reference']='birds-multiview-reference.png (auxiliary plausible views)'
scene['web_height']='anchor + z*D + 0.12*clamp((D-0.06)/0.76,0,1)'
scene.render.image_settings.file_format='PNG'
bpy.ops.wm.save_as_mainfile(filepath=str(DESIGN/'birds-approved-relief.blend'))
scene.render.filepath=str(OUT/'birds-blender-front.png')
bpy.ops.render.render(write_still=True)
center=Vector((2.17,1.5,.12))
camera.location=center+Vector((6,0,10.4))
camera.rotation_euler=(0,math.radians(30),0)
camera.data.ortho_scale=7.3
scene.render.filepath=str(OUT/'birds-blender-oblique.png')
bpy.ops.render.render(write_still=True)
