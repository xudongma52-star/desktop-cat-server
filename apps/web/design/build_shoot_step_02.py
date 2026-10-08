"""Blender: second shoot's traced silhouette, sheath relief, source UV and shadows."""
import bpy,json,math
from pathlib import Path
from mathutils import Vector
from mathutils.geometry import tessellate_polygon
ROOT=Path(__file__).resolve().parents[3];DESIGN=ROOT/'apps/web/design';ASSETS=ROOT/'apps/web/src/assets'
OUT=ROOT/'tmp/shoot-step-02-20261007';OUT.mkdir(parents=True,exist_ok=True)
spec=json.loads((DESIGN/'shoot-step-02-contours.json').read_text(encoding='utf-8'))
W,H=spec['source_size'];left,top,right,bottom=spec['crop'];polygon=spec['polygon']
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
def segment_distance(x,y,a,b):
    vx,vy=b[0]-a[0],b[1]-a[1];t=max(0,min(1,((x-a[0])*vx+(y-a[1])*vy)/max(1e-9,vx*vx+vy*vy)))
    return math.hypot(x-a[0]-t*vx,y-a[1]-t*vy)
def height(x,y):
    d=min(segment_distance(x,y,a,b) for a,b in zip(polygon,polygon[1:]+polygon[:1]))
    maturity=max(0,min(1,(y-906)/143))
    z=.008+(1-math.exp(-d/3.1))*(.025+.095*maturity)
    # Sheath lips follow four independently traced seams. Fine longitudinal
    # fibres remain source texture, rather than invented geometric noise.
    for seam in spec['seams']:
        dist=min(segment_distance(x,y,a,b) for a,b in zip(seam,seam[1:]))
        z+=.004*math.exp(-(dist/.6)**2)*(1-math.exp(-d/1.2))
    return z
points=[];registry={};faces=[]
def index(p):
    key=(round(p.x,6),round(p.y,6))
    if key not in registry:registry[key]=len(points);points.append((p.x,p.y))
    return registry[key]
def refine(a,b,c):
    edges=[(a-b).length_squared,(b-c).length_squared,(c-a).length_squared];edge=edges.index(max(edges))
    if edges[edge]<=1.3**2:
        ids=[index(a),index(b),index(c)]
        if (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)>0:ids.reverse()
        faces.append(tuple(ids));return
    if edge==0:m=(a+b)*.5;refine(a,m,c);refine(m,b,c)
    elif edge==1:m=(b+c)*.5;refine(a,b,m);refine(a,m,c)
    else:m=(c+a)*.5;refine(a,b,m);refine(m,b,c)
boundary=[Vector((x,y,0)) for x,y in polygon]
for a,b,c in tessellate_polygon([boundary]):
    if isinstance(a,int):a,b,c=boundary[a],boundary[b],boundary[c]
    refine(a,b,c)
ox,oy=spec['world_offset']
vertices=[((x/W-.5)*14.222222+ox,(.5-y/H)*8+oy,height(x,y)) for x,y in points]
uv=[((x-left)/(right-left),1-(y-top)/(bottom-top)) for x,y in points]
front_count=len(vertices);front_faces=faces[:];edges={}
for face in faces:
    for a,b in zip(face,face[1:]+face[:1]):edge=tuple(sorted((a,b)));edges[edge]=edges.get(edge,0)+1
vertices+= [(x,y,z-spec['shell_thickness']) for x,y,z in vertices[:]];uv+=uv[:]
faces+=[tuple(v+front_count for v in reversed(face)) for face in front_faces]
faces.extend((a,b,b+front_count,a+front_count) for (a,b),count in edges.items() if count==1)
mesh=bpy.data.meshes.new('Connected right-side shoot with traced sheath folds');mesh.from_pydata(vertices,[],faces);mesh.update()
for face in mesh.polygons:face.use_smooth=True
layer=mesh.uv_layers.new(name='Unchanged approved front UV')
for loop in mesh.loops:layer.data[loop.index].uv=uv[loop.vertex_index]
obj=bpy.data.objects.new('First right-side shoot / continuous body and young growth',mesh);bpy.context.collection.objects.link(obj)
obj['wall_anchor']=spec['wall_anchor'];obj['thickness_note']='Designed shallow relief, not a measured physical scan'
image=bpy.data.images.load(str(ASSETS/'shoot-step-02-approved.png'));image.pack()
material=bpy.data.materials.new('Approved warm pale sheath and quiet sage growth');material.use_nodes=True
tex=material.node_tree.nodes.new('ShaderNodeTexImage');tex.image=image
bsdf=material.node_tree.nodes.get('Principled BSDF');bsdf.inputs['Roughness'].default_value=.86
material.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color']);mesh.materials.append(material)
mesh.calc_loop_triangles();data={'position':[],'normal':[],'uv':[],'anchor':[],'index':[]}
for v in mesh.vertices:
    data['position'].extend(round(float(n),7) for n in v.co);data['normal'].extend(round(float(n),7) for n in v.normal)
    data['uv'].extend(round(float(n),7) for n in uv[v.index]);data['anchor'].append(spec['wall_anchor'])
for tri in mesh.loop_triangles:data['index'].extend(tri.vertices[:])
(ASSETS/'shoot-step-02.traced.json').write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
# Native .blend retains editable undeformed vertices. The reference render uses
# a duplicate with the exact fully revealed root height used by the webpage.
preview=obj.copy();preview.data=mesh.copy();preview.name='Preview / shared web height';bpy.context.collection.objects.link(preview);obj.hide_render=True
for v in preview.data.vertices:
    t=max(0,min(1,(v.co.y+4.08)/.53));weight=t*t*(3-2*t)
    v.co.z=spec['wall_anchor']+(v.co.z*.82+.12)*weight
bpy.ops.mesh.primitive_plane_add(size=20,location=(0,0,-.012));wall=bpy.context.object
wm=bpy.data.materials.new('Warm grey wall');wm.diffuse_color=(.65,.64,.60,1);wall.data.materials.append(wm)
bpy.ops.object.light_add(type='SUN',location=(-5,7,5.3));sun=bpy.context.object;sun.data.energy=1.8;sun.data.angle=.08
sun.rotation_euler=Vector((5,-7,-5.3)).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add(location=(-3.61,-3.45,12));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=1.5
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=12
scene.world.color=(.25,.25,.25);scene.view_settings.view_transform='Standard'
scene.render.resolution_x=600;scene.render.resolution_y=850;scene.render.resolution_percentage=100
scene.render.filepath=str(OUT/'shoot-step-02-blender-front.png')
scene['stage']=spec['stage'];scene['web_height']='anchor+(z*D+.12*clamp((D-.06)/.76,0,1))*rootWeight'
bpy.ops.wm.save_as_mainfile(filepath=str(DESIGN/'shoot-step-02.blend'));bpy.ops.render.render(write_still=True)
print('SECOND_SHOOT',len(data['anchor']),'vertices',len(data['index'])//3,'triangles')
