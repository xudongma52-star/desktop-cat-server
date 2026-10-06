"""根据已确认图片逐瓣描边，生成带原图投影 UV 的主花浅浮雕。

图像仅用作外观投影，不把亮度直接当深度。几何起伏按瓣根、瓣中、卷边
分别定义；投影验收渲染不宣称完成去光照或取得真实物体深度。
"""
import bpy
import math
import json
from pathlib import Path
from mathutils import Vector

ROOT=Path('D:/repository')
OUT=ROOT/'tmp/reference-main-flower-20261006'
OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
image=bpy.data.images.load(str(ROOT/'apps/web/src/assets/flower-approved-reference.png'))
image.pack()
W,H=image.size[:]
CX,CY=371,400
S=.43/273
objects=[]

def world(x,y,z):
    return (.14+(x-CX)*S,.56+(CY-y)*S,z)

# 从参考图像素坐标描出八片花瓣的可见轮廓；顺序为后层到前层。
petals=[
    ('rear lower',[(365,425),(404,437),(434,477),(465,531),(492,585),(493,626),(475,650),(447,638),(410,594),(386,538),(362,477)],(377,432),(474,637),.010,.006),
    ('upper',[(349,364),(308,328),(268,254),(250,199),(247,164),(265,143),(291,128),(315,126),(337,143),(356,190),(370,250),(375,302),(369,349)],(357,365),(303,137),.012,.012),
    ('upper right',[(386,374),(389,327),(403,268),(431,220),(461,190),(491,181),(505,201),(506,238),(491,292),(456,340),(414,372)],(393,378),(487,195),.014,.010),
    ('upper left',[(341,383),(295,382),(238,370),(185,341),(156,307),(138,270),(139,251),(152,240),(180,243),(218,262),(260,291),(300,331),(343,367)],(349,389),(148,253),.011,.013),
    ('right',[(407,392),(451,360),(504,328),(554,309),(598,305),(622,315),(632,333),(625,360),(601,389),(563,411),(511,423),(457,424),(406,416)],(404,401),(625,332),.013,.009),
    ('lower right',[(402,427),(449,447),(498,468),(540,493),(579,526),(606,565),(610,586),(594,597),(563,590),(515,566),(468,531),(429,487),(394,446)],(392,427),(603,582),.017,.010),
    ('left',[(337,404),(284,399),(232,394),(183,405),(145,430),(127,463),(130,494),(151,523),(184,538),(225,527),(277,486),(317,439),(340,419)],(341,408),(139,480),.016,.011),
    ('lower left',[(359,432),(335,456),(302,499),(272,546),(276,582),(298,614),(323,626),(356,612),(386,580),(399,533),(394,487),(376,444)],(367,432),(317,615),.020,.010),
]

projected=bpy.data.materials.new('Original reference projection — fixed-view verification')
projected.use_nodes=True
nodes=projected.node_tree.nodes
nodes.clear()
tex=nodes.new('ShaderNodeTexImage');tex.image=image
emission=nodes.new('ShaderNodeEmission');emission.inputs['Strength'].default_value=1
output=nodes.new('ShaderNodeOutputMaterial')
projected.node_tree.links.new(tex.outputs['Color'],emission.inputs['Color'])
projected.node_tree.links.new(emission.outputs[0],output.inputs['Surface'])

def outline_samples(points):
    samples=[]
    for i,p1 in enumerate(points):
        p0=Vector(points[i-1]);p1=Vector(p1);p2=Vector(points[(i+1)%len(points)]);p3=Vector(points[(i+2)%len(points)])
        for j in range(6):
            t=j/6
            # 平滑描边保留原图不规则圆边，而非以圆形或椭圆替代。
            samples.append(.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t))
    return samples

def make_petal(name,points,root,tip,layer,curl):
    boundary=outline_samples(points)
    center=sum(boundary,Vector((0,0)))/len(boundary)
    axis=Vector(tip)-Vector(root)
    length=axis.length
    axis.normalize()
    cross=Vector((-axis.y,axis.x))
    half_width=max(abs((q-center).dot(cross)) for q in boundary)
    vertices,uv,faces=[],[],[]
    def add(q):
        t=max(0,min(1,(q-Vector(root)).dot(axis)/length))
        u=max(-1,min(1,(q-center).dot(cross)/half_width))
        envelope=math.sin(math.pi*t)
        # 几何只承载可解释的大尺度起伏，细纹留在原图投影及后续贴图中。
        z=.004+layer*.25+.006*envelope+curl*.35*envelope*u*u+.001*envelope*u
        vertices.append(world(q.x,q.y,z));uv.append((q.x/W,1-q.y/H))
    add(center)
    count=len(boundary)
    for ring in range(1,13):
        for q in boundary:add(center.lerp(q,ring/12))
    for j in range(count):faces.append((0,1+(j+1)%count,1+j))
    for ring in range(1,12):
        a=1+(ring-1)*count;b=a+count
        for j in range(count):
            k=(j+1)%count
            faces.append((a+j,a+k,b+k,b+j))
    mesh=bpy.data.meshes.new(name)
    mesh.from_pydata(vertices,[],faces);mesh.update()
    # 屏幕像素 y 与世界 y 反向。显式按 z 分量校正表面朝向，避免双面材质掩盖反法线。
    for polygon in mesh.polygons:
        if polygon.normal.z<0:polygon.flip()
    mesh.update()
    uv_layer=mesh.uv_layers.new(name='Reference projection UV')
    for loop in mesh.loops:uv_layer.data[loop.index].uv=uv[loop.vertex_index]
    obj=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(obj)
    obj.data.materials.append(projected)
    for polygon in mesh.polygons:polygon.use_smooth=True
    obj['traced_from']='Approved reference, pixel coordinate outline'
    objects.append(obj)

for descriptor in petals:make_petal(*descriptor)

# 花心投影落在独立浅丘上；颗粒外观来自参考图，避免重复叠加人工等大球阵列。
center_points=[(371+47*math.cos(i*math.tau/36),400+48*math.sin(i*math.tau/36)) for i in range(36)]
make_petal('Central disk',center_points,(371,448),(371,352),.038,.003)

# 原图中的花托及短花茎也用同一 UV 投影，保证固定视角验收接点连续。
make_petal('Reference calyx and short stem',[(336,563),(365,575),(374,601),(360,622),(344,648),(334,692),(326,720),(316,720),(321,675),(330,632),(324,603)],(321,720),(360,583),.003,.002)

# 下段保留当前植株落点，上段依原图弧线描边；同一组控制点导出给网页叶柄和花苞求接点。
reference_stem=[(186,1122),(216,1042),(253,939),(282,848),(304,771),(322,706),(338,652)]
stem_points=[Vector((.68,-4.55,.018)),Vector((.50,-2.55,.020)),Vector((-.16,-1.02,.014))]
for i,(x,y) in enumerate(reference_stem):
    stem_points.append(Vector(world(x,y,.011-.008*i/(len(reference_stem)-1))))
stem_points.append(Vector((.14,.56,-.010)))

def curve_point(t):
    q=t*(len(stem_points)-1);i=min(len(stem_points)-2,int(q));weight=q-i
    p1=stem_points[i];p2=stem_points[i+1]
    p0=stem_points[i-1] if i else 2*p1-p2
    p3=stem_points[i+2] if i+2<len(stem_points) else 2*p2-p1
    knots=[0]
    for a,b in [(p0,p1),(p1,p2),(p2,p3)]:knots.append(knots[-1]+max((b-a).length**.5,1e-4))
    tau=knots[1]+weight*(knots[2]-knots[1])
    def mix(a,b,left,right):return ((right-tau)*a+(tau-left)*b)/(right-left)
    a1=mix(p0,p1,knots[0],knots[1]);a2=mix(p1,p2,knots[1],knots[2]);a3=mix(p2,p3,knots[2],knots[3])
    b1=mix(a1,a2,knots[0],knots[2]);b2=mix(a2,a3,knots[1],knots[3])
    return mix(b1,b2,knots[1],knots[2])

def source_center(y):
    for (ax,ay),(bx,by) in zip(reference_stem,reference_stem[1:]):
        if by<=y<=ay:
            f=(y-ay)/(by-ay)
            return Vector((ax+(bx-ax)*f,y)),Vector((bx-ax,by-ay)).normalized()
    return Vector(reference_stem[-1]),Vector((.25,-1)).normalized()

rows,columns=256,12
vertices,stem_uv,faces=[],[],[]
stop=(len(stem_points)-2)/(len(stem_points)-1)
for row in range(rows+1):
    t=row/rows*stop;c=curve_point(t)
    tangent=(curve_point(min(1,t+.0001))-curve_point(max(0,t-.0001))).normalized()
    normal=Vector((-tangent.y,tangent.x,0)).normalized()
    source_y=CY+(.56-c.y)/S
    # 原图以下的延伸只复用已知茎段纹理，不采样图片外的背景或推测额外植物。
    if source_y>1122:
        lower_fraction=min(1,(-.578-c.y)/(4.55-.578))
        source_y=1120-400*lower_fraction
    source_y=max(652,min(1120,source_y))
    source,source_tangent=source_center(source_y)
    source_normal=Vector((source_tangent.y,-source_tangent.x))
    half_width=(5.0+1.5*max(0,(800-source_y)/148))*S
    for column in range(columns+1):
        u=column/columns*2-1
        # 圆润截面上的浅脊线和缓慢收细给出石膏茎，而不是一根均匀黑色细线。
        ridge=.0036*math.sqrt(max(0,1-u*u))+.0006*math.exp(-((u+.23)/.18)**2)
        point=c+normal*(half_width*u);point.z+=ridge
        vertices.append(tuple(point))
        pixel=source+source_normal*((half_width/S)*u)
        stem_uv.append((pixel.x/W,1-pixel.y/H))
for row in range(rows):
    for column in range(columns):
        a=row*(columns+1)+column;b=a+columns+1
        faces.append((a,a+1,b+1,b))
mesh=bpy.data.meshes.new('Traced curved plaster stem');mesh.from_pydata(vertices,[],faces);mesh.update()
for polygon in mesh.polygons:
    if polygon.normal.z<0:polygon.flip()
    polygon.use_smooth=True
mesh.update();uv_layer=mesh.uv_layers.new(name='Reference stem UV')
for loop in mesh.loops:uv_layer.data[loop.index].uv=stem_uv[loop.vertex_index]
stem=bpy.data.objects.new('Traced curved plaster stem',mesh);bpy.context.collection.objects.link(stem)
stem.data.materials.append(projected);stem['reference_stem']=True;objects.append(stem)

# 次花使用原图右下方的独立轮廓；保持已验收主花的所有几何数据及生成顺序。
main_world=world
def world(x,y,z):
    # 副花保留材质细节，但缩小并改变朝向，避免直接复制参考图构图。
    dx=(x-986)*S*.78;dy=(820-y)*S*.78
    angle=-.22
    return (1.28+dx*math.cos(angle)-dy*math.sin(angle),-.24+dx*math.sin(angle)+dy*math.cos(angle),z)
secondary_start=len(objects)
secondary_petals=[
    ('Secondary upper',[(967,812),(947,775),(940,735),(949,702),(964,681),(980,668),(994,667),(1016,687),(1034,717),(1035,744),(1023,772),(995,805)],(980,813),(990,675),.012,.010),
    ('Secondary upper left',[(963,817),(927,812),(891,791),(861,757),(844,731),(844,716),(863,704),(886,709),(917,734),(945,772),(973,803)],(975,819),(849,718),.011,.009),
    ('Secondary right',[(1000,812),(1026,782),(1058,760),(1090,756),(1122,767),(1145,786),(1157,808),(1151,835),(1133,853),(1107,859),(1063,850),(1029,840),(999,835)],(1000,821),(1150,810),.015,.010),
    ('Secondary lower right',[(994,844),(1027,852),(1062,867),(1093,899),(1125,933),(1137,958),(1129,971),(1108,969),(1075,949),(1044,922),(1016,890),(981,850)],(988,839),(1127,965),.020,.012),
    ('Secondary folded left',[(965,830),(945,842),(924,851),(911,873),(914,893),(929,903),(945,887),(950,866),(968,850)],(968,835),(916,886),.018,.016),
    ('Secondary foreground fold',[(934,824),(953,833),(978,850),(1001,865),(1008,885),(993,884),(971,869),(948,851)],(945,832),(1000,879),.023,.012),
]
for i,descriptor in enumerate(secondary_petals):
    make_petal(*descriptor)
    # 每片花瓣改变少量展开角，纹理跟随几何，花根仍在花心范围内衔接。
    angle=[-.045,.065,-.035,.055,-.06,.025][i]
    for vertex in objects[-1].data.vertices:
        dx=vertex.co.x-1.28;dy=vertex.co.y+.24
        vertex.co.x=1.28+dx*math.cos(angle)-dy*math.sin(angle)
        vertex.co.y=-.24+dx*math.sin(angle)+dy*math.cos(angle)
    objects[-1].data.update()
make_petal('Secondary central disk',[(986+30*math.cos(i*math.tau/36),820+29*math.sin(i*math.tau/36)) for i in range(36)],(986,849),(986,791),.038,.003)
make_petal('Secondary calyx and curved stem',[(967,856),(950,866),(940,888),(931,910),(922,932),(914,946),(908,944),(916,921),(921,899),(925,876),(938,862)],(911,944),(959,860),.004,.003)
for obj in objects[secondary_start:]:obj['reference_secondary']=True
world=main_world

positions,normals,uvs,indices,anchors=[],[],[],[],[]
for obj in objects:
    mesh=obj.data
    mesh.calc_loop_triangles()
    offset=len(positions)//3
    custom_uv={loop.vertex_index:list(mesh.uv_layers.active.data[loop.index].uv) for loop in mesh.loops} if obj.get('reference_stem') or obj.get('reference_secondary') else None
    # 投影 UV 每个顶点唯一，无接缝，所以可使用顶点法线和索引。
    for vertex in mesh.vertices:
        positions.extend(round(v,7) for v in vertex.co)
        normals.extend(round(v,7) for v in vertex.normal)
        x=CX+(vertex.co.x-.14)/S;y=CY-(vertex.co.y-.56)/S
        uvs.extend(round(v,7) for v in custom_uv[vertex.index]) if custom_uv else uvs.extend((round(x/W,7),round(1-y/H,7)))
        anchors.append(.016 if obj.get('reference_stem') else -.006)
    for tri in mesh.loop_triangles:indices.extend(offset+v for v in tri.vertices)
(ROOT/'apps/web/src/assets/main-flower.traced.json').write_text(json.dumps({'position':positions,'normal':normals,'uv':uvs,'index':indices,'anchor':anchors,'stemControlPoints':[[round(v,7) for v in point] for point in stem_points]},separators=(',',':')))

bpy.ops.object.camera_add(location=(.14,.52,8))
camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=1.05
bpy.context.scene.camera=camera
scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.samples=16
scene.render.resolution_x=1000;scene.render.resolution_y=1050;scene.render.resolution_percentage=100
scene.render.film_transparent=True
scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGBA'
scene.view_settings.view_transform='Standard'
scene.render.filepath=str(OUT/'main-flower-projection.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/main-flower-traced.blend'))
bpy.ops.render.render(write_still=True)
print('TRACED_MAIN_FLOWER',len(positions)//3,len(indices)//3)
