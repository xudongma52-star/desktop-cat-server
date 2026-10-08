"""确认四叶浅裂弯枝：原图轮廓、独立纹理投影、连续茎柄与可编辑 Blender 场景。"""
import bpy, math, json
from pathlib import Path
from mathutils import Vector

ROOT=Path('D:/repository');OUT=ROOT/'tmp/ivy-region-20261007';OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
image=bpy.data.images.load(str(ROOT/'apps/web/src/assets/ivy-approved-reference.png'));image.pack()
W,H=image.size[:];S=.0019
# 整株采用同一映射；叶柄及纹理不逐片缩放，替代底中偏右的旧重叠植物群。
def world(q,z):return (1.45+(q.x-650)*S,-2.0+(750-q.y)*S,z)
material=bpy.data.materials.new('Approved ivy fixed-front projection');material.use_nodes=True
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
    boundary=smooth(points,True);center=Vector(center);count=len(boundary);rings=22
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

# 四片独立轮廓保留裂尖、反卷边和不等尺寸，不能用同一个叶模板旋转复制。
leaf('Upper right three-lobed leaf',[(703,244),(682,231),(685,205),(695,170),(683,145),(683,120),(692,90),(695,61),(705,39),(706,64),(717,89),(738,108),(755,133),(766,160),(788,153),(818,135),(847,125),(875,123),(904,137),(935,150),(954,156),(980,153),(962,171),(946,195),(926,217),(909,239),(906,260),(921,279),(923,300),(922,326),(935,352),(928,367),(913,353),(892,347),(873,337),(852,320),(826,317),(802,321),(779,311),(757,297),(743,278),(729,258)],(801,223),.031)
leaf('Middle left curled leaf',[(552,407),(542,398),(542,384),(556,370),(545,372),(532,374),(520,365),(507,358),(495,339),(471,327),(446,316),(427,309),(437,323),(436,344),(426,366),(413,389),(396,417),(368,448),(346,476),(363,469),(383,464),(402,453),(418,447),(425,464),(423,485),(414,510),(404,535),(407,545),(418,535),(429,523),(450,516),(470,497),(484,474),(490,452),(512,437),(531,421)],(468,423),.023)
leaf('Large lower left lobed leaf',[(440,865),(410,859),(385,859),(360,872),(343,895),(327,920),(314,950),(293,977),(266,998),(253,1023),(245,1055),(232,1099),(228,1080),(234,1052),(225,1028),(223,1007),(210,984),(195,959),(195,932),(206,915),(209,900),(189,880),(166,872),(143,854),(116,849),(89,831),(69,814),(53,808),(84,799),(112,783),(143,757),(176,741),(205,734),(239,738),(268,733),(298,723),(315,706),(327,686),(341,663),(355,644),(365,609),(377,631),(394,651),(408,674),(419,698),(426,724),(438,750),(438,779),(432,806),(434,832)],(295,839),.030)
leaf('Small right turned lobed leaf',[(733,690),(751,689),(765,678),(777,660),(787,639),(790,613),(802,638),(814,659),(820,674),(837,686),(856,693),(884,708),(874,721),(859,733),(848,751),(843,768),(839,785),(838,805),(836,819),(831,829),(831,853),(837,884),(828,906),(815,917),(816,897),(808,879),(795,863),(780,847),(765,833),(759,816),(750,803),(734,790),(723,782),(718,767),(721,750),(731,728)],(789,760),.024)

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

stem=[(340,1510),(374,1400),(415,1280),(447,1167),(485,1053),(523,955),(554,870),(591,766),(621,674),(642,581),(637,510),(620,439),(629,378),(654,332),(677,281),(703,244)]
branch('Continuous tapered woody S main stem',stem,[13,12,12,11,11,10,9,9,8,8,7,7,6,6,5,4])
branch('Lower left curved petiole',[(523,955),(508,923),(487,897),(465,878),(440,865)],[8,7,6,5,4])
branch('Middle left curved petiole',[(620,439),(611,464),(602,484),(586,463),(568,430),(552,407)],[7,7,6,5,4,3])
branch('Small right arched petiole',[(591,766),(626,725),(658,700),(689,686),(714,684),(733,690)],[7,6,5,5,4,3])
leaf('Lower natural branch joint',[(514,963),(509,941),(519,938),(526,946),(537,959),(535,977),(524,981)],(525,959),.012)
leaf('Middle natural branch joint',[(611,489),(595,486),(598,474),(607,463),(619,453),(625,469),(624,484)],(612,477),.008)
leaf('Side natural branch joint',[(583,752),(593,739),(606,742),(601,751),(596,766),(586,773),(579,765)],(590,756),.009)

# 底端沿参考主茎切线延伸到原植物带根点，纹理只取确认图内的木质茎段。
end=Vector(world(Vector(stem[0]),0)[:2]);curve=smooth([(.66,-4.55),(.76,-4.12),(.82,-3.8),tuple(end)])
source=smooth(stem[:3]);vertices=[];uv=[];faces=[]
for i,q in enumerate(curve):
    t=i/(len(curve)-1);si=(1-t)*(len(source)-1);k=min(int(si),len(source)-2);sample=source[k].lerp(source[k+1],si-k)
    source_t=(source[k+1]-source[k]).normalized();sn=Vector((source_t.y,-source_t.x))
    tangent=(curve[min(i+1,len(curve)-1)]-curve[max(i-1,0)]).normalized();normal=Vector((-tangent.y,tangent.x));half=.024
    for j in range(13):
        u=j/12*2-1;p=q+normal*(half*u);pixel=sample+sn*(half/S*u)
        vertices.append((p.x,p.y,.006+.012*math.sqrt(max(0,1-u*u))));uv.append((pixel.x/W,1-pixel.y/H))
for i in range(len(curve)-1):
    for j in range(12):a=i*13+j;b=a+13;faces.append((a,a+1,b+1,b))
mesh_object('Continuous rooted lower extension',vertices,uv,faces)

data={'position':[],'normal':[],'uv':[],'index':[],'anchor':[]}
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles();offset=len(data['anchor']);uv={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
    for vertex in mesh.vertices:
        data['position'].extend(round(v,7) for v in vertex.co);data['normal'].extend(round(v,7) for v in vertex.normal)
        data['uv'].extend(round(v,7) for v in uv[vertex.index]);data['anchor'].append(-.006)
    for tri in mesh.loop_triangles:data['index'].extend(offset+v for v in tri.vertices)
(ROOT/'apps/web/src/assets/ivy.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=world(Vector((515,763)),8));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=H*S
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=8
scene.render.resolution_x=W;scene.render.resolution_y=H;scene.render.resolution_percentage=100
scene.render.film_transparent=True;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.render.filepath=str(OUT/'ivy-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/ivy-traced.blend'));bpy.ops.render.render(write_still=True)
print('TRACED_IVY',W,H,len(data['anchor']),len(data['index'])//3)
