"""确认底部三处低层植物：原图轮廓、独立纹理投影、连续茎柄与可编辑 Blender 场景。"""
import bpy, math, json
from pathlib import Path
from mathutils import Vector

ROOT=Path('D:/repository');OUT=ROOT/'tmp/bottom-layer-20261007';OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
image=bpy.data.images.load(str(ROOT/'apps/web/src/assets/bottom-layer-approved-reference.png'));image.pack()
W,H=image.size[:];S=8/H
# 整株采用同一映射；叶柄及纹理不逐片缩放，替代底部增补植物；现有主体不参与生成。
def world(q,z):return ((q.x/W-.5)*(8*16/9),(.5-q.y/H)*8,z)
material=bpy.data.materials.new('Approved quiet bottom layer projection');material.use_nodes=True
nodes=material.node_tree.nodes;nodes.clear();tex=nodes.new('ShaderNodeTexImage');tex.image=image
emission=nodes.new('ShaderNodeEmission');output=nodes.new('ShaderNodeOutputMaterial')
material.node_tree.links.new(tex.outputs['Color'],emission.inputs['Color']);material.node_tree.links.new(emission.outputs[0],output.inputs['Surface'])
objects=[]

def smooth(points,closed=False):
    points=[Vector(p) for p in points];out=[]
    for i in range(len(points) if closed else len(points)-1):
        p1=points[i];p2=points[(i+1)%len(points)]
        p0=points[i-1] if i or closed else 2*p1-p2
        p3=points[(i+2)%len(points)] if closed or i+2<len(points) else 2*p2-p1
        for j in range(10):
            t=j/10
            out.append(.5*(2*p1+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t))
    if not closed:out.append(points[-1])
    return out

def mesh_object(name,vertices,uv,faces):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(vertices,[],faces);mesh.update()
    for face in mesh.polygons:
        if face.normal.z<0:face.flip()
        face.use_smooth=True
    mesh.update();layer=mesh.uv_layers.new(name='Confirmed source coordinates')
    for loop in mesh.loops:layer.data[loop.index].uv=uv[loop.vertex_index]
    obj=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(obj);mesh.materials.append(material)
    obj['wall_anchor']=-.006;objects.append(obj)

def leaf(name,points,center,height=.026):
    boundary=smooth(points,True);center=Vector(center);count=len(boundary);rings=10
    vertices=[];uv=[];faces=[]
    for ring in range(rings+1):
        r=ring/rings
        for edge in boundary:
            q=center.lerp(edge,r)
            # 自由边薄，中腹一次宽弧；微卷沿局部生长方向，叶脉纤维来自原图而非规则刻纹。
            z=.006+height*(1-r*r)**2+.003*math.sin((q.x-center.x)*.014)*(1-r*r)
            vertices.append(world(q,z));uv.append((q.x/W,1-q.y/H))
    for ring in range(rings):
        for j in range(count):
            k=(j+1)%count;a=ring*count;b=a+count;faces.append((a+j,a+k,b+k,b+j))
    mesh_object(name,vertices,uv,faces)


def branch(name,points,widths,height=.012):
    centers=smooth(points);vertices=[];uv=[];faces=[];columns=12
    for i,q in enumerate(centers):
        tangent=(centers[min(i+1,len(centers)-1)]-centers[max(0,i-1)]).normalized();normal=Vector((-tangent.y,tangent.x))
        segment=min(i//10,len(widths)-2);fraction=(i-segment*10)/10;half=widths[segment]*(1-fraction)+widths[segment+1]*fraction
        for j in range(columns+1):
            u=j/columns*2-1;pixel=q+normal*(half*u)
            vertices.append(world(pixel,.006+height*math.sqrt(max(0,1-u*u))));uv.append((pixel.x/W,1-pixel.y/H))
    for i in range(len(centers)-1):
        for j in range(columns):a=i*(columns+1)+j;b=a+columns+1;faces.append((a,a+1,b+1,b))
    mesh_object(name,vertices,uv,faces)


leaf('oval-top',[(679,944),(649,941),(632,926),(625,902),(625,881),(647,885),(670,903),(682,923)],(653,915),.018)
leaf('oval-upright',[(704,974),(708,951),(721,933),(742,920),(735,938),(723,958)],(719,950),.014)
leaf('oval-middle',[(694,991),(672,998),(650,997),(625,983),(643,970),(662,969),(680,979)],(661,984),.014)
leaf('oval-lower',[(719,1053),(691,1050),(671,1038),(661,1020),(678,1020),(704,1030)],(693,1036),.016)
leaf('oval-right',[(730,1041),(735,1015),(751,996),(775,984),(762,1013),(748,1030)],(749,1010),.016)
branch('oval-main',[(737,1080),(728,1054),(712,1020),(697,986),(680,947)],[2,2,1.8,1.5,1],.008)
branch('oval-top-petiole',[(697,986),(681,947),(668,931)],[1.4,1.3,.8],.008)
branch('oval-side-petiole',[(712,1020),(706,987),(706,970)],[1.2,1,.5],.008)
branch('oval-mid-petiole',[(709,1014),(695,993),(679,987)],[1.2,1,.5],.008)
branch('oval-low-petiole',[(731,1065),(718,1050),(703,1043)],[1.2,1,.5],.008)
branch('oval-right-petiole',[(727,1052),(732,1035),(741,1021)],[1.2,1,.5],.008)
# 确认图三条蕨羽轴与逐节稀疏羽片；细草保持三个独立弧线。
branch('fern-high-axis',[(51,1079),(62,1010),(81,945),(104,881),(129,817)],[1.5,1.3,1.1,.8,.3],.006)
branch('fern-short-axis',[(51,1079),(89,1030),(123,988),(167,941)],[1.5,1.1,.7,.2],.006)
branch('fern-left-axis',[(51,1079),(56,1029),(44,986),(15,969)],[1.3,1,.7,.2],.006)
def leaflet(name,base,tip,width):
    a=Vector(base);b=Vector(tip);d=b-a;n=Vector((-d.y,d.x)).normalized();c=a+d*.52
    leaf(name,[tuple(a),tuple(a+d*.3+n*width*.8),tuple(a+d*.7+n*width),tuple(b),tuple(a+d*.7-n*width*.65),tuple(a+d*.3-n*width*.55)],tuple(c),.010)
for i,(a,l,r,w) in enumerate([
((126,826),(120,824),(132,820),2),((122,837),(114,829),(131,831),2),
((118,849),(108,839),(131,842),2.2),((113,861),(101,850),(131,854),2.3),
((109,874),(92,860),(129,866),2.5),((104,887),(86,873),(129,879),2.5),
((100,900),(78,884),(128,896),2.8),((95,914),(69,893),(130,909),3),
((90,928),(57,908),(127,923),3),((86,943),(47,921),(129,941),3),
((80,960),(35,940),(127,958),3),((75,977),(27,960),(118,976),3),
((69,995),(37,977),(100,995),3)]):
    leaflet('fern-tall-left-'+str(i),a,l,w);leaflet('fern-tall-right-'+str(i),a,r,w)
for i,(a,l,r) in enumerate([
((158,951),(151,944),(160,961)),((148,962),(142,952),(152,976)),
((139,972),(132,960),(142,988)),((130,982),(122,968),(133,999)),
((120,992),(111,977),(124,1010)),((111,1004),(98,986),(116,1021)),
((101,1017),(89,997),(110,1031)),((91,1030),(85,1010),(111,1042)),
((81,1043),(81,1023),(105,1050))]):
    leaflet('fern-low-left-'+str(i),a,l,2.2);leaflet('fern-low-right-'+str(i),a,r,2.4)
for i,(a,l,r) in enumerate([
((26,977),(13,972),(30,967)),((34,984),(19,981),(35,973)),
((43,994),(29,991),(44,981)),((49,1004),(33,1004),(51,991)),
((53,1015),(39,1016),(56,1002))]):
    leaflet('fern-small-left-'+str(i),a,l,2);leaflet('fern-small-right-'+str(i),a,r,2)
branch('grass-upright',[(1329,1080),(1332,1020),(1334,967),(1325,906),(1303,860)],[2,2,1.6,1,.15],.010)
branch('grass-right',[(1331,1080),(1355,1020),(1390,962),(1439,930)],[1.7,1.5,.9,.1],.010)
branch('grass-left',[(1325,1080),(1306,1036),(1280,1016),(1254,1024)],[1.8,1.4,.8,.1],.008)

data={'position':[],'normal':[],'uv':[],'index':[],'anchor':[]}
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles();offset=len(data['anchor']);uv={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
    for vertex in mesh.vertices:
        data['position'].extend(round(v,7) for v in vertex.co);data['normal'].extend(round(v,7) for v in vertex.normal)
        data['uv'].extend(round(v,7) for v in uv[vertex.index]);data['anchor'].append(-.006)
    for tri in mesh.loop_triangles:data['index'].extend(offset+v for v in tri.vertices)
(ROOT/'apps/web/src/assets/bottom-layer.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=world(Vector((W/2,H/2)),8));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=8*16/9
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=8
scene.render.resolution_x=W;scene.render.resolution_y=H;scene.render.resolution_percentage=100;scene.render.pixel_aspect_x=(16/9)/(W/H)
scene.render.film_transparent=True;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.render.filepath=str(OUT/'bottom-layer-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/bottom-layer-traced.blend'));bpy.ops.render.render(write_still=True)
print('TRACED_BOTTOM_LAYER',W,H,len(data['anchor']),len(data['index'])//3)
