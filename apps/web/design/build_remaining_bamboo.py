"""Blender remaining approved bamboo: individually traced leaves and continuous cane.

Only this specimen is exported. Existing meshes, textures and placements are
never opened for writing. Angled AI references help design thickness, while the
approved front controls xy/UV. Thickness values are design units, not measured.
"""
import bpy, json, math, sys
import numpy as np
from pathlib import Path
from mathutils import Vector
from mathutils.geometry import tessellate_polygon

ROOT=Path(__file__).resolve().parents[3]
DESIGN=ROOT/'apps/web/design';ASSETS=ROOT/'apps/web/src/assets'
OUT=ROOT/'tmp/botanical-remaining-20261008';OUT.mkdir(parents=True,exist_ok=True)
slug=sys.argv[sys.argv.index('--')+1]
spec=json.loads((DESIGN/(slug+'-contours.json')).read_text(encoding='utf-8'))
W,H=spec['source_size'];left,top,right,bottom=spec['crop']
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
image=bpy.data.images.load(str(ASSETS/(slug+'-approved.png')));image.pack()
material=bpy.data.materials.new('Remaining approved bamboo / approved source projection');material.use_nodes=True
nodes=material.node_tree.nodes;texture=nodes.new('ShaderNodeTexImage');texture.image=image
bsdf=nodes.get('Principled BSDF');bsdf.inputs['Roughness'].default_value=.88
material.node_tree.links.new(texture.outputs['Color'],bsdf.inputs['Base Color'])
# Match the existing webpage's reference projection: the source already has
# light. Mixing its appearance prevents multiplying that light a second time;
# actual mesh silhouettes still cast wall shadows in every camera view.
emission=nodes.new('ShaderNodeEmission');emission.inputs['Strength'].default_value=1
material.node_tree.links.new(texture.outputs['Color'],emission.inputs['Color'])
appearance=nodes.new('ShaderNodeMixShader');appearance.inputs[0].default_value=.92
material.node_tree.links.new(bsdf.outputs['BSDF'],appearance.inputs[1])
material.node_tree.links.new(emission.outputs[0],appearance.inputs[2])
material.node_tree.links.new(appearance.outputs[0],nodes.get('Material Output').inputs['Surface'])
objects=[]

# Read-only foreground triangles supply actual surface height. This preserves
# the accepted foreground plant and avoids an invented depressed band across the cane.
foreground=json.loads((ASSETS/spec['occlusion_clearance']['mesh']).read_text())
fp=np.asarray(foreground['position']).reshape(-1,3)
ft=fp[np.asarray(foreground['index']).reshape(-1,3)]
xmin=(left/W-.5)*spec['world_width'];xmax=(right/W-.5)*spec['world_width']
ymin=(.5-bottom/H)*8;ymax=(.5-top/H)*8
keep=(ft[:,:,0].max(axis=1)>xmin)&(ft[:,:,0].min(axis=1)<xmax)&(ft[:,:,1].max(axis=1)>ymin)&(ft[:,:,1].min(axis=1)<ymax)
ft=ft[keep];fu=ft[:,1,:2]-ft[:,0,:2];fv=ft[:,2,:2]-ft[:,0,:2]
fd=fu[:,0]*fv[:,1]-fu[:,1]*fv[:,0];valid=np.abs(fd)>1e-10
ft=ft[valid];fu=fu[valid];fv=fv[valid];fd=fd[valid]
clearance_cache={}
# Spatial cells retain only triangles whose xy bounds cover each cell. This
# read-only query keeps the identical foreground-height rule while avoiding a
# full triangle scan for every vertex of the tall, densely traced specimen.
cell_size=.08;cells={}
for number,tri in enumerate(ft):
    lo=np.floor(tri[:,:2].min(axis=0)/cell_size).astype(int)
    hi=np.floor(tri[:,:2].max(axis=0)/cell_size).astype(int)
    for ix in range(lo[0],hi[0]+1):
        for iy in range(lo[1],hi[1]+1):cells.setdefault((ix,iy),[]).append(number)
cells={key:np.asarray(value,dtype=int) for key,value in cells.items()}
def foreground_height(wx,wy):
    key=(round(wx,6),round(wy,6))
    if key not in clearance_cache:
        candidates=cells.get((math.floor(wx/cell_size),math.floor(wy/cell_size)))
        if candidates is None:clearance_cache[key]=None;return None
        local_ft=ft[candidates];local_fu=fu[candidates];local_fv=fv[candidates];local_fd=fd[candidates]
        w=np.asarray([wx,wy])-local_ft[:,0,:2]
        a=(w[:,0]*local_fv[:,1]-w[:,1]*local_fv[:,0])/local_fd
        b=(local_fu[:,0]*w[:,1]-local_fu[:,1]*w[:,0])/local_fd
        inside=(a>=0)&(b>=0)&(a+b<=1)
        zs=local_ft[:,0,2]+a*(local_ft[:,1,2]-local_ft[:,0,2])+b*(local_ft[:,2,2]-local_ft[:,0,2])
        clearance_cache[key]=float(zs[inside].max()) if inside.any() else None
    return clearance_cache[key]

def world(x,y,z):
    ox,oy=spec['world_offset']
    clearance=spec['root_clearance']
    t=max(0,min(1,(y-clearance['start_y'])/(clearance['end_y']-clearance['start_y'])))
    bend=t*t*(3-2*t)
    x+=clearance['x_pixels']*bend
    z*=1-(1-clearance['depth_factor'])*bend
    wx,wy=(x/W-.5)*spec['world_width']+ox,(.5-y/H)*spec['world_height']+oy
    overlap=spec['occlusion_clearance']
    if overlap['min_y']<=y<=overlap['max_y']:
        surface=foreground_height(wx,wy)
        if surface is not None:z=min(z,max(.002,surface-overlap['gap']))
    return (wx,wy,z)

def source_uv(x,y):return ((x-left)/spec['cane_atlas']['texture_width'],1-(y-top)/(bottom-top))

def mesh_object(name,vertices,uv,faces):
    # A closed .003-unit back shell makes cast shadows follow actual edges,
    # while all surfaces retain the same continuous field and wall anchor.
    front_count=len(vertices);edges={}
    for face in faces:
        for a,b in zip(face,face[1:]+face[:1]):
            edge=tuple(sorted((a,b)));edges[edge]=edges.get(edge,0)+1
    front_faces=faces[:]
    vertices=vertices+[(x,y,z-spec['shell_thickness']) for x,y,z in vertices]
    uv=uv+uv[:]
    faces=faces+[tuple(v+front_count for v in reversed(face)) for face in front_faces]
    faces.extend((a,b,b+front_count,a+front_count) for (a,b),count in edges.items() if count==1)
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(vertices,[],faces);mesh.update()
    # Front coordinate y points up after pixel conversion; polygon winding is
    # corrected before duplicating the bottom shell in the callers.
    for polygon in mesh.polygons:polygon.use_smooth=True
    layer=mesh.uv_layers.new(name='Approved source pixel UV')
    for loop in mesh.loops:layer.data[loop.index].uv=uv[loop.vertex_index]
    mesh.materials.append(material)
    obj=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(obj)
    obj['wall_anchor']=spec['wall_anchor'];obj['reference']='01-approved-front.png'
    objects.append(obj);return obj

def distance(x,y,a,b):
    vx,vy=b[0]-a[0],b[1]-a[1]
    t=max(0,min(1,((x-a[0])*vx+(y-a[1])*vy)/max(1e-10,vx*vx+vy*vy)))
    return math.hypot(x-a[0]-t*vx,y-a[1]-t*vy)

def leaf_height(x,y,polygon):
    d=min(distance(x,y,a,b) for a,b in zip(polygon,polygon[1:]+polygon[:1]))
    taper=1-math.exp(-d/2.1)
    base=polygon[0];tip=max(polygon,key=lambda p:math.hypot(p[0]-base[0],p[1]-base[1]))
    midrib=distance(x,y,base,tip)
    vx,vy=tip[0]-base[0],tip[1]-base[1]
    t=max(0,min(1,((x-base[0])*vx+(y-base[1])*vy)/max(1e-8,vx*vx+vy*vy)))
    # Each independently traced leaf has a thin free edge and a shallow folded
    # midrib. Petiole/root thickness is continuous, not a disconnected blob.
    return .008+taper*(.015+.014*math.exp(-(midrib/2.4)**2)*math.sin(math.pi*t))

for part in spec['parts']:
    polygon=part['polygon'];boundary=[Vector((x,y,0)) for x,y in polygon]
    points=[];faces=[];registry={}
    def vertex(p):
        key=(round(p.x,6),round(p.y,6))
        if key not in registry:registry[key]=len(points);points.append((p.x,p.y))
        return registry[key]
    def refine(a,b,c):
        lengths=[(a-b).length_squared,(b-c).length_squared,(c-a).length_squared];edge=lengths.index(max(lengths))
        if lengths[edge]<=2.4**2:
            ids=[vertex(a),vertex(b),vertex(c)]
            if (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)>0:ids.reverse()
            faces.append(tuple(ids));return
        if edge==0:m=(a+b)*.5;refine(a,m,c);refine(m,b,c)
        elif edge==1:m=(b+c)*.5;refine(a,b,m);refine(a,m,c)
        else:m=(c+a)*.5;refine(a,b,m);refine(m,b,c)
    for a,b,c in tessellate_polygon([boundary]):
        if isinstance(a,int):a,b,c=boundary[a],boundary[b],boundary[c]
        refine(a,b,c)
    mesh_object(part['name'],[world(x,y,leaf_height(x,y,polygon)) for x,y in points],[source_uv(x,y) for x,y in points],faces)

cane_points=spec['curves'][0]['points']
def cane_sample(y):
    for a,b in zip(cane_points,cane_points[1:]):
        if a[1]<=y<=b[1]:
            t=(y-a[1])/max(.0001,b[1]-a[1]);return a[0]*(1-t)+b[0]*t
    return cane_points[-1][0]

def cane_section(y,u):
    lip=max(math.exp(-((y-node+.65)/.75)**2) for node in spec['node_y'])
    groove=max(math.exp(-((y-node-1.05)/.55)**2) for node in spec['node_y'])
    profile=spec['node_profile']
    return .007+.026*math.sqrt(max(0,1-u*u))+profile['lip_height']*lip-profile['groove_height']*groove

def curve_center_height(number,x,y):
    curve=spec['curves'][number]
    if curve['stem']:
        nearest=min(curve['points'],key=lambda q:(q[0]-x)**2+(q[1]-y)**2)
        return cane_section(y,max(-1,min(1,(x-nearest[0])/max(.1,nearest[2]))))
    origin=curve['points'][0];end=curve['points'][-1]
    root=curve_center_height(curve['parent_curve'],origin[0],origin[1])
    run=math.hypot(x-origin[0],y-origin[1]);t=min(1,run/5);t=t*t*(3-2*t)
    height=root*(1-t)+.020*t
    if curve.get('leaf_root'):
        remaining=math.hypot(x-end[0],y-end[1]);tail=min(1,remaining/2);tail=tail*tail*(3-2*tail)
        height=.008+(height-.008)*tail
    return height

for number,curve in enumerate(spec['curves']):
    centers=curve['points'];vertices=[];uv=[];faces=[];columns=16
    for i,(x,y,half) in enumerate(centers):
        prev=centers[max(0,i-1)];nxt=centers[min(len(centers)-1,i+1)]
        dx,dy=nxt[0]-prev[0],nxt[1]-prev[1];length=max(1e-8,math.hypot(dx,dy));nx,ny=-dy/length,dx/length
        collar=max((math.exp(-((y-node)/1.2)**2) for node in spec['node_y']),default=0) if curve['stem'] else 0
        for j in range(columns+1):
            u=j/columns*2-1;px,py=x+nx*half*u*(1+spec['node_profile']['lip_width']*collar),y+ny*half*u
            # Side branches start at the actual parent surface, then taper
            # outward. Petiole tips meet the traced leaf root's native height.
            z=cane_section(y,u) if curve['stem'] else curve_center_height(number,x,y)-.004*(1-math.sqrt(max(0,1-u*u)))
            vertices.append(world(px,py,z))
            # Cane UV stays inside its opaque strip; every row has the same
            # width, so lower taper and collars cannot cause transparent tears.
            # Leaf/twig UVs keep the unchanged approved front projection.
            if curve['stem']:
                atlas=spec['cane_atlas']
                uv.append(((atlas['left']+.5+(u*.5+.5)*(atlas['width']-1))/atlas['texture_width'],1-(py-top)/(bottom-top)))
            else:uv.append(source_uv(px,py))
    for i in range(len(centers)-1):
        for j in range(columns):a=i*(columns+1)+j;b=a+columns+1;faces.append((a,b,b+1,a+1))
    mesh_object(curve['name'],vertices,uv,faces)

data={'position':[],'normal':[],'uv':[],'anchor':[],'index':[]}
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles();offset=len(data['anchor'])
    uv={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
    for v in mesh.vertices:
        data['position'].extend(round(float(n),7) for n in v.co)
        data['normal'].extend(round(float(n),7) for n in v.normal)
        data['uv'].extend(round(float(n),7) for n in uv[v.index]);data['anchor'].append(spec['wall_anchor'])
    for tri in mesh.loop_triangles:data['index'].extend(offset+v for v in tri.vertices)
(ASSETS/(slug+'.traced.json')).write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')

# Save editable native parts at undeformed thickness; Blender preview uses the
# same fully revealed root transition as the webpage, and actual cast shadows.
for obj in objects:
    preview=obj.copy();preview.data=obj.data.copy();preview.name='Web height preview / '+obj.name
    bpy.context.collection.objects.link(preview);obj.hide_render=True
    for v in preview.data.vertices:
        t=max(0,min(1,(v.co.y+4.08)/.53));weight=t*t*(3-2*t)
        v.co.z=spec['wall_anchor']+(v.co.z*.82+.12)*weight
bpy.ops.mesh.primitive_plane_add(size=30,location=(-3.5,0,-.012));wall=bpy.context.object
wm=bpy.data.materials.new('Matching warm neutral wall');wm.use_nodes=True
wm.node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value=(.54,.53,.48,1)
wm.node_tree.nodes.get('Principled BSDF').inputs['Roughness'].default_value=.97
wall.data.materials.append(wm)
bpy.ops.object.light_add(type='SUN',location=(-5,7,5.3));sun=bpy.context.object;sun.data.energy=3.55;sun.data.angle=.08
sun.rotation_euler=Vector((5,-7,-5.3)).to_track_quat('-Z','Y').to_euler()
cx=((left+right)*.5/W-.5)*14.222222;cy=(.5-(top+bottom)*.5/H)*8
view_height=(bottom-top)/H*8+.28
bpy.ops.object.camera_add(location=(cx,cy,12));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=view_height
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=16
scene.world.use_nodes=True
scene.world.node_tree.nodes.get('Background').inputs['Color'].default_value=(.65,.64,.60,1)
scene.world.node_tree.nodes.get('Background').inputs['Strength'].default_value=.4
scene.view_settings.view_transform='Standard'
scene.render.resolution_x=600;scene.render.resolution_y=1080;scene.render.resolution_percentage=100
scene.render.filepath=str(OUT/(slug+'-blender-front.png'))
scene['web_base_depth']=.06;scene['web_lift_depth']=.76;scene['web_shared_lift']=.12
scene['model_stage']='Remaining plants / completed together before unified user review'
scene['depth_note']='Separate preview meshes show shared web height; native original parts and JSON retain undeformed height'
bpy.ops.wm.save_as_mainfile(filepath=str(DESIGN/(slug+'.blend')))
bpy.ops.render.render(write_still=True)
camera.location=Vector((cx,cy,.1))+Vector((6,0,10.4));camera.rotation_euler=(0,math.radians(30),0)
scene.render.filepath=str(OUT/(slug+'-blender-oblique.png'));bpy.ops.render.render(write_still=True)

camera.location=Vector((cx,cy,.1))+Vector((-6,0,10.4));camera.rotation_euler=(0,math.radians(-30),0)
scene.render.filepath=str(OUT/(slug+'-blender-left-oblique.png'));bpy.ops.render.render(write_still=True)
print('REMAINING_BAMBOO',slug,len(objects),'editable parts',len(data['anchor']),'vertices',len(data['index'])//3,'triangles')
