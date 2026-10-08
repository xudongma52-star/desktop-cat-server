"""只重建两朵已确认花的连续弯茎，保留花瓣/花心/花托顶点与原图 UV。可重复执行。"""
import bpy, json, math, hashlib
from pathlib import Path
from mathutils import Vector
ROOT=Path('D:/repository');OUT=ROOT/'tmp/flower-stems-20261007';OUT.mkdir(parents=True,exist_ok=True)
asset=ROOT/'apps/web/src/assets/main-flower.traced.json';data=json.loads(asset.read_text())
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'apps/web/design/main-flower-traced.blend'))
for obj in list(bpy.data.objects):
    if obj.name=='Traced curved plaster stem' or obj.get('reference_regenerated_stem'):
        bpy.data.objects.remove(obj,do_unlink=True)
material=bpy.data.materials.get('Original reference projection — fixed-view verification')
image=next(n.image for n in material.node_tree.nodes if n.type=='TEX_IMAGE');W,H=image.size[:]
S=.43/273;objects=[]

def point(points,t):
    points=[Vector(v) for v in points];q=t*(len(points)-1);i=min(len(points)-2,int(q));w=q-i
    p1=points[i];p2=points[i+1];p0=points[i-1] if i else 2*p1-p2;p3=points[i+2] if i+2<len(points) else 2*p2-p1
    knots=[0]
    for a,b in [(p0,p1),(p1,p2),(p2,p3)]:knots.append(knots[-1]+max((b-a).length**.5,1e-4))
    tau=knots[1]+w*(knots[2]-knots[1])
    def mix(a,b,left,right):return ((right-tau)*a+(tau-left)*b)/(right-left)
    a1=mix(p0,p1,knots[0],knots[1]);a2=mix(p1,p2,knots[1],knots[2]);a3=mix(p2,p3,knots[2],knots[3])
    b1=mix(a1,a2,knots[0],knots[2]);b2=mix(a2,a3,knots[1],knots[3])
    return mix(b1,b2,knots[1],knots[2])

main_end=(.14+(321-371)*S,.56+(400-720)*S,.006)
# 保留落地根点；多段缓弓与渐细截面连接到既有花托的实际末端，而不是穿过花盘。
main_points=[(.68,-4.55,.006),(.48,-3.40,.006),(.28,-2.45,.006),(-.035,-1.48,.006),(-.17,-.79,.006),(-.115,-.43,.006),(-.02,-.17,.006),main_end]
def main_at_height(y):
    low,high=0,1
    for _ in range(28):
        mid=(low+high)/2
        if point(main_points,mid).y<y:low=mid
        else:high=mid
    return tuple(point(main_points,(low+high)/2))
dx=(911-986)*S*.78;dy=(820-944)*S*.78;a=-.22
side_end=(1.28+dx*math.cos(a)-dy*math.sin(a),-.24+dx*math.sin(a)+dy*math.cos(a),.006)
side_points=[main_at_height(-1.48),(.33,-1.35,.006),(.68,-1.11,.006),(.96,-.76,.006),side_end]
main_source=[(196,1095),(216,1042),(253,939),(282,848),(304,771),(321,720)]
side_source=[(931,910),(922,932),(914,946),(911,944)]

def stem(name,control,source,width_bottom,width_top,source_scale):
    positions=[];uv=[];faces=[];rows=180;columns=12
    for i in range(rows+1):
        t=i/rows;c=point(control,t)
        tangent=point(control,min(1,t+.0001))-point(control,max(0,t-.0001));tangent.z=0;tangent.normalize()
        cross=Vector((-tangent.y,tangent.x,0))
        sample=point(source,t);source_tangent=point(source,min(1,t+.0001))-point(source,max(0,t-.0001));source_tangent.normalize()
        source_cross=Vector((source_tangent.y,-source_tangent.x))
        half=width_bottom*(1-t)+width_top*t
        for j in range(columns+1):
            u=j/columns*2-1;v=c+cross*(half*u)
            # 薄边和一次柔和截面，不添加黑边。所有接点锚定 -.006，与花托共用动态抬升。
            v.z=.006+.007*math.sqrt(max(0,1-u*u))+.0007*math.exp(-((u+.2)/.2)**2)
            positions.append(tuple(v));pixel=sample+source_cross*(half/source_scale*u);uv.append((pixel.x/W,1-pixel.y/H))
    for i in range(rows):
        for j in range(columns):
            k=i*(columns+1)+j;n=k+columns+1;faces.append((k,k+1,n+1,n))
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(positions,[],faces);mesh.update()
    for poly in mesh.polygons:
        if poly.normal.z<0:poly.flip()
        poly.use_smooth=True
    mesh.update();layer=mesh.uv_layers.new(name='Original stem projection UV')
    for loop in mesh.loops:layer.data[loop.index].uv=uv[loop.vertex_index]
    obj=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(obj);mesh.materials.append(material)
    obj['reference_regenerated_stem']=True;objects.append(obj)
stem('Rebuilt main continuous curved stem',main_points,main_source,.012,.007,S)
stem('Rebuilt secondary continuous curved branch',side_points,side_source,.009,.005,S*.78)

# 初轮只移除旧茎的 .016 锚点顶点；重跑时移除上次追加尾段。保留花头数组原值，不经 Blender 浮点重导出。
keep_count=data.get('stemVertexStart')
keep=[i for i,v in enumerate(data['anchor']) if i<keep_count] if keep_count is not None else [i for i,v in enumerate(data['anchor']) if v!=.016]
mapping={old:new for new,old in enumerate(keep)}
result={key:[data[key][i*size+j] for i in keep for j in range(size)] for key,size in [('position',3),('normal',3),('uv',2),('anchor',1)]}
result['index']=[]
for i in range(0,len(data['index']),3):
    tri=data['index'][i:i+3]
    if all(v in mapping for v in tri):result['index'].extend(mapping[v] for v in tri)
head_hash=hashlib.sha256(json.dumps(result,separators=(',',':')).encode()).hexdigest()
result['stemVertexStart']=len(keep);result['stemIndexStart']=len(result['index'])
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles();offset=len(result['anchor'])
    uv={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
    for vertex in mesh.vertices:
        result['position'].extend(round(v,7) for v in vertex.co);result['normal'].extend(round(v,7) for v in vertex.normal)
        result['uv'].extend(round(v,7) for v in uv[vertex.index]);result['anchor'].append(-.006)
    for tri in mesh.loop_triangles:result['index'].extend(offset+v for v in tri.vertices)
protected={key:result[key][:len(keep)*size] for key,size in [('position',3),('normal',3),('uv',2),('anchor',1)]}
protected['index']=result['index'][:result['stemIndexStart']]
assert hashlib.sha256(json.dumps(protected,separators=(',',':')).encode()).hexdigest()==head_hash
result['stemControlPoints']=[list(p) for p in main_points]+[[.14,.56,-.010]]
result['secondaryStemControlPoints']=[list(p) for p in side_points]
asset.write_text(json.dumps(result,separators=(',',':')))
(OUT/'protected-head-verification.json').write_text(json.dumps({'unchanged':True,'sha256':head_hash,'vertices':len(keep),'indices':result['stemIndexStart']},indent=2))
scene=bpy.context.scene;camera=scene.camera;camera.location=(.6,-1.6,8);camera.data.type='ORTHO';camera.data.ortho_scale=6.4
scene.render.resolution_x=900;scene.render.resolution_y=1400;scene.cycles.samples=8
scene.render.filepath=str(OUT/'flowers-rebuilt-stems.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/main-flower-traced.blend'));bpy.ops.render.render(write_still=True)
print('REBUILT_STEMS',len(result['anchor']),head_hash)
