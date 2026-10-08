"""确认疏花复伞形植株：原图轮廓、独立纹理投影、连续茎柄与可编辑 Blender 场景。"""
import bpy, math, json
from pathlib import Path
from mathutils import Vector

ROOT=Path('D:/repository');OUT=ROOT/'tmp/umbel-reference-20261007';OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
image=bpy.data.images.load(str(ROOT/'apps/web/src/assets/umbel-approved-reference.png'));image.pack()
W,H=image.size[:];S=.0042
# 整株采用同一映射；叶柄及纹理不逐片缩放，替代右上旧伞形花及其周围斜穿的旧细枝。
def world(q,z):return (2.70+(q.x-496)*S,1.55+(447-q.y)*S,z)
material=bpy.data.materials.new('Approved compound umbel fixed-front projection');material.use_nodes=True
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

# 三片独立浅齿叶按确认图追踪，保留不等尺寸、反卷边及侧倾。
leaf('Upper right narrow toothed leaf',[(620,702),(625,682),(625,661),(631,677),(635,652),(638,628),(644,639),(648,612),(657,605),(677,580),(669,609),(668,628),(672,642),(658,649),(663,663),(649,669),(648,685),(636,690)],(648,649),.023)
leaf('Middle left toothed leaf',[(496,931),(477,919),(457,916),(435,909),(447,904),(429,894),(437,889),(416,876),(429,874),(410,861),(423,857),(402,841),(414,837),(397,826),(400,821),(386,807),(367,789),(390,797),(408,803),(414,815),(421,806),(433,827),(437,817),(447,839),(453,834),(461,858),(468,854),(477,878),(483,884),(487,906)],(450,872),.028)
leaf('Lower right large turned toothed leaf',[(591,1113),(608,1110),(620,1098),(627,1103),(640,1091),(646,1098),(658,1096),(676,1102),(664,1112),(676,1114),(662,1123),(683,1128),(701,1140),(718,1150),(703,1156),(719,1170),(735,1189),(724,1187),(739,1204),(752,1223),(741,1225),(753,1245),(761,1272),(749,1255),(734,1244),(721,1233),(704,1222),(696,1206),(690,1224),(678,1201),(667,1184),(664,1201),(651,1179),(642,1164),(639,1178),(627,1158),(620,1142),(615,1151),(608,1136)],(675,1164),.030)
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

stem=[(535,1498),(518,1422),(506,1340),(502,1260),(507,1190),(516,1157),(527,1102),(535,1030),(551,956),(565,884),(574,817),(572,767),(561,698),(549,630),(530,555),(514,491),(496,447)]
branch('Continuous bowed woody main stem',stem,[12,12,11,10,9,9,8,8,8,7,7,7,6,6,5,5,4])
branch('Upper curved leaf petiole',[(572,767),(585,739),(600,717),(620,702)],[6,5,4,2.5])
branch('Middle curved leaf petiole',[(535,1030),(526,994),(513,960),(496,931)],[7,6,4,3])
branch('Lower arched leaf petiole',[(516,1157),(535,1137),(560,1117),(577,1112),(591,1113)],[7,6,5,3,2.5])
# 复伞冠各主梗以不同真实接点和宽缓曲率展开，不做等角度直杆。
groups=[
 ((334,389),[(496,447),(451,431),(400,418),(361,403),(334,389)],[(292,354),(286,373),(278,404),(310,425),(354,352),(332,372)]),
 ((364,335),[(496,447),(464,418),(422,379),(394,350),(364,335)],[(352,307),(370,321),(388,338),(350,343),(331,334),(321,326)]),
 ((309,274),[(496,447),(444,383),(387,334),(349,302),(309,274)],[(309,225),(278,236),(257,269),(291,289),(325,267),(335,247),(343,237)]),
 ((407,276),[(496,447),(463,393),(437,337),(416,295),(407,276)],[(398,255),(418,273),(435,281),(375,281),(408,299)]),
 ((374,179),[(496,447),(443,352),(409,268),(388,212),(374,179)],[(365,138),(341,142),(335,174),(354,190),(398,158),(377,165)]),
 ((440,113),[(496,447),(465,338),(444,235),(440,163),(440,113)],[(406,85),(446,66),(472,90),(411,119),(467,117)]),
 ((464,202),[(496,447),(476,357),(465,281),(460,233),(464,202)],[(464,170),(459,185),(442,190),(470,210),(480,183)]),
 ((524,119),[(496,447),(480,345),(493,252),(511,176),(524,119)],[(529,76),(560,91),(567,117),(550,147),(501,124),(488,139)]),
 ((511,221),[(496,447),(493,361),(501,279),(511,221)],[(534,179),(556,195),(529,206),(498,204),(554,230)]),
 ((619,201),[(496,447),(531,359),(576,281),(607,227),(619,201)],[(587,169),(611,157),(633,178),(656,189),(657,211),(619,211)]),
 ((584,296),[(496,447),(535,378),(562,328),(584,296)],[(569,267),(590,279),(604,296),(587,308)]),
 ((640,287),[(496,447),(552,373),(600,314),(640,287)],[(629,246),(651,259),(684,279),(686,306),(658,297),(606,307)]),
]
for gi,(center,axis,blooms) in enumerate(groups):
    widths=[3.6+(gi%3)*.3]+[2.8]*(len(axis)-2)+[1.5]
    branch('Independent curved primary flower ray '+str(gi),axis,widths,.009)
    cx,cy=center
    for bi,(x,y) in enumerate(blooms):
        branch('Fine secondary pedicel '+str(gi)+' '+str(bi),[center,((cx+x)*.5+(bi%2)*2,(cy+y)*.5),(x,y)],[1.4,1,.65],.006)
        # 小花轮廓按每朵实际中心做薄五瓣展开；瓣内细纹以原图 UV 保留，不使用球粒堆成毛团。
        vertices=[];uv=[];faces=[];segments=30;rings=4;radius=8+(bi%3)*1.1;turn=gi*.61+bi*.73
        for ri in range(rings+1):
            r=ri/rings
            for j in range(segments):
                a=j/segments*2*math.pi+turn;edge=radius*(.78+.22*math.cos((a-turn)*5))
                q=Vector((x+math.cos(a)*edge*r,y+math.sin(a)*edge*r*.86))
                vertices.append(world(q,.009+.014*(1-r*r)+.005*r*math.cos((a-turn)*5)));uv.append((q.x/W,1-q.y/H))
        for ri in range(rings):
            for j in range(segments):
                k=(j+1)%segments;a=ri*segments;b=a+segments;faces.append((a+j,a+k,b+k,b+j))
        mesh_object('Individually located small five-petal flower '+str(gi)+' '+str(bi),vertices,uv,faces)
# 根部沿同一主轴向原植物带底缘延伸，投影采样只用图内下段木质茎。
end=Vector(world(Vector(stem[0]),0)[:2]);curve=smooth([(2.95,-4.55),(2.91,-3.94),(2.92,-3.36),tuple(end)])
source=smooth(stem[:3]);vertices=[];uv=[];faces=[]
for i,q in enumerate(curve):
    t=i/(len(curve)-1);si=(1-t)*(len(source)-1);k=min(int(si),len(source)-2);sample=source[k].lerp(source[k+1],si-k)
    source_t=(source[k+1]-source[k]).normalized();sn=Vector((source_t.y,-source_t.x));tangent=(curve[min(i+1,len(curve)-1)]-curve[max(i-1,0)]).normalized();normal=Vector((-tangent.y,tangent.x));half=.046
    for j in range(13):
        u=j/12*2-1;p=q+normal*(half*u);pixel=sample+sn*(half/S*u)
        vertices.append((p.x,p.y,.006+.012*math.sqrt(max(0,1-u*u))));uv.append((pixel.x/W,1-pixel.y/H))
for i in range(len(curve)-1):
    for j in range(12):a=i*13+j;b=a+13;faces.append((a,a+1,b+1,b))
mesh_object('Continuous rooted main stem extension',vertices,uv,faces)
data={'position':[],'normal':[],'uv':[],'index':[],'anchor':[]}
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles();offset=len(data['anchor']);uv={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
    for vertex in mesh.vertices:
        data['position'].extend(round(v,7) for v in vertex.co);data['normal'].extend(round(v,7) for v in vertex.normal)
        data['uv'].extend(round(v,7) for v in uv[vertex.index]);data['anchor'].append(-.006)
    for tri in mesh.loop_triangles:data['index'].extend(offset+v for v in tri.vertices)
(ROOT/'apps/web/src/assets/umbel.traced.json').write_text(json.dumps(data,separators=(',',':')))
bpy.ops.object.camera_add(location=world(Vector((512,768)),8));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=H*S
scene=bpy.context.scene;scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=8
scene.render.resolution_x=W;scene.render.resolution_y=H;scene.render.resolution_percentage=100
scene.render.film_transparent=True;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.render.filepath=str(OUT/'umbel-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/umbel-traced.blend'));bpy.ops.render.render(write_still=True)
print('TRACED_UMBEL',W,H,len(data['anchor']),len(data['index'])//3)
