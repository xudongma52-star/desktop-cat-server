"""确认右侧叶枝与高穗：原图轮廓、独立纹理投影、连续茎柄与可编辑 Blender 场景。"""
import bpy, math, json
from pathlib import Path
from mathutils import Vector

ROOT=Path('D:/repository');OUT=ROOT/'tmp/right-pair-20261007';OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
image=bpy.data.images.load(str(ROOT/'apps/web/src/assets/right-pair-approved-reference.png'));image.pack()
W,H=image.size[:];S=.0042
# 整株采用同一映射；叶柄及纹理不逐片缩放，替代右侧全部剩余旧植株。
def world(q,z):return (4.65+(q.x-360)*S,-.35+(850-q.y)*S,z)
material=bpy.data.materials.new('Approved right pair fixed-front projection');material.use_nodes=True
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
    boundary=smooth(points,True);center=Vector(center);count=len(boundary);rings=14
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

# 每片叶分别追踪；确认图还有一片小侧叶，完整保留，不扩成花型。
leaf('Upper pointed upright leaf',[(321,853),(311,830),(309,804),(314,778),(323,752),(337,730),(357,710),(384,698),(403,690),(417,680),(399,702),(397,721),(389,746),(379,767),(365,789),(347,808),(332,828)],(355,759),.028)
leaf('Small upper left turned leaf',[(293,899),(279,887),(267,884),(252,871),(243,858),(228,843),(240,845),(255,852),(269,863),(281,879)],(260,871),.019)
leaf('Middle right serrated leaf',[(394,1098),(397,1068),(405,1040),(420,1013),(441,989),(470,970),(498,957),(529,951),(555,957),(584,958),(610,954),(590,970),(576,988),(562,1007),(546,1028),(526,1049),(501,1066),(474,1077),(447,1084),(420,1087)],(492,1021),.031)
leaf('Large lower left serrated leaf',[(292,1314),(267,1301),(242,1292),(215,1280),(189,1264),(166,1245),(146,1223),(130,1200),(115,1178),(100,1155),(83,1141),(67,1133),(50,1129),(70,1125),(92,1119),(116,1112),(142,1108),(168,1114),(192,1123),(217,1137),(241,1156),(262,1176),(279,1200),(290,1227),(297,1256),(298,1288)],(213,1224),.034)
leaf('Lower right downward curled leaf',[(388,1375),(405,1368),(423,1372),(447,1381),(468,1394),(486,1415),(499,1440),(505,1465),(510,1492),(509,1518),(501,1538),(499,1520),(496,1500),(484,1487),(464,1478),(445,1464),(430,1445),(417,1425),(404,1400)],(453,1430),.026)
leaf('Upper grass thin arcing blade',[(716,1184),(731,1129),(747,1080),(766,1035),(790,990),(815,954),(835,937),(814,962),(798,988),(783,1020),(769,1057),(750,1101),(733,1148)],(768,1049),.016)
leaf('Lower grass thin arcing blade',[(672,1540),(687,1484),(706,1430),(728,1376),(754,1329),(784,1285),(815,1259),(832,1250),(810,1273),(790,1300),(770,1336),(751,1376),(731,1423),(711,1470),(689,1516)],(748,1382),.017)
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

stem=[(293,1774),(280,1690),(272,1624),(278,1572),(290,1512),(312,1418),(316,1360),(337,1250),(351,1175),(360,1139),(349,1064),(334,990),(309,928),(316,875),(321,853)]
branch('Continuous woody leaf branch',stem,[12,11,10,9,9,8,8,7,7,7,6,5,4,3,2.5])
branch('Upper small leaf curved petiole',[(309,928),(306,917),(299,906),(293,899)],[4,3.5,2.5,1.5])
branch('Large left leaf curved petiole',[(316,1360),(310,1346),(301,1330),(292,1314)],[6,5,3,2])
branch('Middle right leaf curved petiole',[(360,1139),(369,1123),(382,1107),(394,1098)],[6,5,3,2])
branch('Lower turned leaf arched petiole',[(312,1418),(332,1398),(354,1381),(371,1374),(388,1375)],[6,5,4,3,2])
grass=[(629,1774),(655,1610),(672,1540),(697,1325),(716,1184),(739,1005),(749,822),(749,781),(735,725),(727,657),(712,580),(698,505),(688,430),(674,350),(657,274),(642,207),(624,143),(610,97),(592,70),(578,49)]
branch('Single gently bowed seed grass stalk',grass,[6,6,5,5,4,4,3.5,3.5,3,3,2.8,2.6,2.4,2.2,2,1.8,1.4,1,.8,.4],.009)
# 小穗分别描出细长尖壳，沿同一草茎的实际节点连接，不做规则锯齿片。
seeds=[(592,70,7,27),(610,97,5,25),(599,109,11,14),(625,125,5,29),(620,142,10,15),(658,166,9,39),(615,193,15,17),(641,221,7,43),(660,240,7,37),(680,267,8,39),(699,286,8,38),(628,299,9,43),(670,327,7,44),(692,362,7,47),(715,380,8,45),(646,402,9,36),(646,434,8,48),(686,414,7,45),(704,451,7,44),(694,472,8,48),(723,509,9,50),(746,529,7,47),(671,552,9,47),(706,625,9,51),(732,604,7,48),(751,649,8,48),(689,676,8,42),(700,708,8,45),(735,725,7,50)]
axis=smooth(grass)
for i,(x,y,w,length) in enumerate(seeds):
    # 曲梗先接近小穗上肩；垂尖壳沿参考图方向微倾，保留不等长度与分组空隙。
    root=min(axis,key=lambda p:abs(p.y-(y-length*.55)))
    tip=(x,y-length*.45)
    branch('Seed curved attachment '+str(i),[tuple(root),((root.x+x)*.5,y-length*.6),tip],[.8,.65,.45],.004)
    leaf('Individual tapered seed husk '+str(i),[(x-2,y-length*.5),(x-w*.7,y-length*.25),(x-w,y),(x-w*.5,y+length*.25),(x+3,y+length*.5),(x+w*.5,y+length*.17),(x+w,y-length*.12),(x+w*.4,y-length*.35)],(x,y),.018)
# 两株都沿已有下段曲率伸出屏幕底缘，使用本株图内木质/草茎的纹理。
for name,source_points,root,width in [('Woody rooted extension',stem[:3],(4.31,-4.55),.045),('Grass rooted extension',grass[:3],(5.73,-4.55),.022)]:
    end=Vector(world(Vector(source_points[0]),0)[:2]);curve=smooth([root,((root[0]+end.x)*.5,(root[1]+end.y)*.5),tuple(end)])
    source=smooth(source_points);vertices=[];uv=[];faces=[]
    for i,q in enumerate(curve):
        t=i/(len(curve)-1);si=(1-t)*(len(source)-1);k=min(int(si),len(source)-2);sample=source[k].lerp(source[k+1],si-k)
        source_t=(source[k+1]-source[k]).normalized();sn=Vector((source_t.y,-source_t.x));tangent=(curve[min(i+1,len(curve)-1)]-curve[max(i-1,0)]).normalized();normal=Vector((-tangent.y,tangent.x))
        for j in range(13):
            u=j/12*2-1;p=q+normal*(width*u);pixel=sample+sn*(width/S*u)
            vertices.append((p.x,p.y,.006+.010*math.sqrt(max(0,1-u*u))));uv.append((pixel.x/W,1-pixel.y/H))
    for i in range(len(curve)-1):
        for j in range(12):a=i*13+j;b=a+13;faces.append((a,a+1,b+1,b))
    mesh_object(name,vertices,uv,faces)
data={'position':[],'normal':[],'uv':[],'index':[],'anchor':[]}
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles();offset=len(data['anchor']);uv={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
    for vertex in mesh.vertices:
        data['position'].extend(round(v,7) for v in vertex.co);data['normal'].extend(round(v,7) for v in vertex.normal)
        data['uv'].extend(round(v,7) for v in uv[vertex.index]);data['anchor'].append(-.006)
    for tri in mesh.loop_triangles:data['index'].extend(offset+v for v in tri.vertices)
(ROOT/'apps/web/src/assets/right-pair.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=world(Vector((443,887)),8));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=H*S
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=8
scene.render.resolution_x=W;scene.render.resolution_y=H;scene.render.resolution_percentage=100
scene.render.film_transparent=True;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.render.filepath=str(OUT/'right-pair-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/right-pair-traced.blend'));bpy.ops.render.render(write_still=True)
print('TRACED_RIGHT_PAIR',W,H,len(data['anchor']),len(data['index'])//3)
