"""从确认的新效果图描边花茎、细叶和花苞，保留既有主副花头；独立原图 UV 和可编辑 Blender 源。"""
import bpy, json, math, hashlib
from pathlib import Path
from mathutils import Vector
ROOT=Path('D:/repository');OUT=ROOT/'tmp/confirmed-flower-stems-20261007';OUT.mkdir(parents=True,exist_ok=True)
asset=ROOT/'apps/web/src/assets/main-flower.traced.json';heads=json.loads(asset.read_text())
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'apps/web/design/main-flower-traced.blend'))
for obj in list(bpy.data.objects):
    if obj.name=='Traced curved plaster stem' or obj.get('reference_regenerated_stem') or obj.get('approved_stem_unit'):
        bpy.data.objects.remove(obj,do_unlink=True)
image=bpy.data.images.load(str(ROOT/'apps/web/src/assets/flower-stems-approved-reference.png'));image.pack();W,H=image.size[:]
# 坐标依据确认图 992×1586 的独立部位描边；不把明暗直接解释成几何深度。
material=bpy.data.materials.new('Approved matching stems fixed-view projection');material.use_nodes=True
nodes=material.node_tree.nodes;nodes.clear();tex=nodes.new('ShaderNodeTexImage');tex.image=image
emission=nodes.new('ShaderNodeEmission');out=nodes.new('ShaderNodeOutputMaterial')
material.node_tree.links.new(tex.outputs['Color'],emission.inputs['Color']);material.node_tree.links.new(emission.outputs[0],out.inputs['Surface'])
objects=[];S=.43/273
main_source=[(415,1540),(410,1480),(413,1408),(434,1330),(445,1242),(452,1162),(450,1108),(437,1050),(416,994),(380,937),(349,886),(326,828),(317,772),(316,703),(325,628),(330,568)]
side_source=[(452,1118),(469,1077),(501,1020),(537,968),(578,912),(620,855),(657,811),(683,787)]
main_end=Vector((.14+(321-371)*S,.56+(400-720)*S))
dx=(911-986)*S*.78;dy=(820-944)*S*.78;a=-.22
side_end=Vector((1.28+dx*math.cos(a)-dy*math.sin(a),-.24+dx*math.sin(a)+dy*math.cos(a)))

def smooth(points,closed=False):
    points=[Vector(p) for p in points];out=[]
    for i in range(len(points) if closed else len(points)-1):
        p1=points[i];p2=points[(i+1)%len(points)];p0=points[i-1] if i or closed else 2*p1-p2
        p3=points[(i+2)%len(points)] if closed or i+2<len(points) else 2*p2-p1
        for j in range(12):
            t=j/12;out.append(.5*(2*p1+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t))
    if not closed:out.append(points[-1])
    return out

def main_map(q):
    t=(1540-q.y)/(1540-568);baseline=415+(330-415)*t
    return Vector((.68+(main_end.x-.68)*t+(q.x-baseline)*.0022,-4.55+(main_end.y+4.55)*t))
side_root=main_map(Vector(side_source[0]))
def side_map(q):
    t=(1118-q.y)/(1118-787);baseline=452+(683-452)*t
    return side_root.lerp(side_end,t)+Vector(((q.x-baseline)*.0022,0))

def mesh_object(name,vertices,uv,faces):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(vertices,[],faces);mesh.update()
    for poly in mesh.polygons:
        if poly.normal.z<0:poly.flip()
        poly.use_smooth=True
    mesh.update();layer=mesh.uv_layers.new(name='Approved stem reference UV')
    for loop in mesh.loops:layer.data[loop.index].uv=uv[loop.vertex_index]
    obj=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(obj);mesh.materials.append(material)
    obj['approved_stem_unit']=True;objects.append(obj)

def strip(name,source,mapper,widths):
    centers=smooth(source);world=[mapper(q) for q in centers];positions=[];uv=[];faces=[];columns=12
    for i,q in enumerate(centers):
        k=min(i//12,len(widths)-2);f=(i-k*12)/12;half=widths[k]*(1-f)+widths[k+1]*f
        st=(centers[min(i+1,len(centers)-1)]-centers[max(0,i-1)]).normalized();sn=Vector((st.y,-st.x))
        wt=(world[min(i+1,len(world)-1)]-world[max(0,i-1)]).normalized();wn=Vector((-wt.y,wt.x))
        for j in range(columns+1):
            u=j/columns*2-1;p=world[i]+wn*(half*.0020*u);pixel=q+sn*(half*u)
            positions.append((p.x,p.y,.006+.010*math.sqrt(max(0,1-u*u))))
            uv.append((pixel.x/W,1-pixel.y/H))
    for i in range(len(centers)-1):
        for j in range(columns):k=i*(columns+1)+j;n=k+columns+1;faces.append((k,k+1,n+1,n))
    mesh_object(name,positions,uv,faces)

strip('Confirmed continuous S curved main stem',main_source,main_map,[13,11,10,10,10,10,12,11,10,9,8,7,6,6,5,5])
strip('Confirmed diagonal secondary flower branch',side_source,side_map,[9,8,7,6,6,5,5,5])

def attached_map(source_anchor,parent_map):
    root=Vector(source_anchor);base=parent_map(root)
    # 附属细叶与花苞保持原图自身比例；只适配接点位置，不随主茎纵向伸长而拉成长片。
    def mapper(q):return base+Vector(((q.x-root.x)*.0022,(root.y-q.y)*.0022))
    return mapper

def patch(name,points,center,mapper,height=.012):
    boundary=smooth(points,True);center=Vector(center);positions=[];uv=[];faces=[];count=len(boundary);rings=16
    for ring in range(rings+1):
        r=ring/rings
        for edge in boundary:
            q=center.lerp(edge,r);p=mapper(q);z=.006+height*(1-r*r)**2
            positions.append((p.x,p.y,z));uv.append((q.x/W,1-q.y/H))
    for ring in range(rings):
        for j in range(count):k=(j+1)%count;a=ring*count;b=a+count;faces.append((a+j,a+k,b+k,b+j))
    mesh_object(name,positions,uv,faces)

left_bud=attached_map((326,873),main_map)
strip('Matching left bud curved petiole',[(326,873),(302,820),(277,760),(250,709),(238,699)],left_bud,[5,4,3,3,3])
patch('Matching unopened left bud',[(238,699),(226,691),(208,665),(198,637),(198,609),(201,587),(216,604),(238,624),(249,650),(249,676)],(222,644),left_bud,.022)
right_bud=attached_map((522,995),side_map)
strip('Matching side bud curved petiole',[(522,995),(565,978),(603,949),(630,928)],right_bud,[4,3,3,3])
patch('Matching side unopened bud',[(630,928),(634,906),(646,886),(664,874),(686,861),(690,877),(678,899),(659,916),(641,931)],(656,901),right_bud,.018)

for name,points,center,root,parent in [
 ('Main outward narrow leaf',[(326,874),(300,847),(278,831),(254,819),(235,811),(252,810),(280,818),(313,844)],(284,836),(326,874),main_map),
 ('Main upright narrow leaf',[(317,813),(320,780),(334,753),(355,734),(378,720),(367,745),(348,772),(332,797)],(340,766),(317,813),main_map),
 ('Side upward narrow leaf',[(526,984),(530,946),(528,909),(518,866),(513,833),(527,852),(538,883),(547,920),(540,958)],(531,914),(526,984),side_map),
 ('Side outward narrow leaf',[(528,992),(556,977),(587,978),(615,997),(586,989),(557,990)],(567,986),(528,992),side_map),
 ('Lower main narrow leaf',[(434,1309),(421,1263),(403,1230),(384,1197),(372,1169),(391,1187),(414,1215),(431,1251),(438,1288)],(415,1247),(434,1309),main_map),
]:patch(name,points,center,attached_map(root,parent),.012)
# 原图枝节独立保留，采用局部比例避免纵向适配把枝节拉长；不以黑球或圆片伪造接头。
for name,points,center,root,parent in [
 ('Main woody ridge node',[(403,1014),(415,1012),(427,1026),(431,1043),(423,1054),(411,1045)],(418,1034),(418,1034),main_map),
 ('Continuous flower branch fork',[(441,1089),(452,1086),(463,1102),(465,1124),(456,1139),(446,1128)],(453,1114),(452,1118),main_map),
 ('Lower leaf woody node',[(421,1298),(434,1299),(443,1315),(441,1330),(427,1331),(420,1317)],(433,1315),(434,1309),main_map),
]:patch(name,points,center,attached_map(root,parent),.008)

stem_data={'position':[],'normal':[],'uv':[],'index':[],'anchor':[]}
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles();offset=len(stem_data['anchor'])
    uv={loop.vertex_index:mesh.uv_layers.active.data[loop.index].uv for loop in mesh.loops}
    for vertex in mesh.vertices:
        stem_data['position'].extend(round(v,7) for v in vertex.co);stem_data['normal'].extend(round(v,7) for v in vertex.normal)
        stem_data['uv'].extend(round(v,7) for v in uv[vertex.index]);stem_data['anchor'].append(-.006)
    for tri in mesh.loop_triangles:stem_data['index'].extend(offset+v for v in tri.vertices)
(ROOT/'apps/web/src/assets/flower-stems.traced.json').write_text(json.dumps(stem_data,separators=(',',':')))
head_count=heads['stemVertexStart'];index_count=heads['stemIndexStart']
protected={key:heads[key][:head_count*size] for key,size in [('position',3),('normal',3),('uv',2),('anchor',1)]}
protected['index']=heads['index'][:index_count]
before_hash=hashlib.sha256(json.dumps(protected,separators=(',',':')).encode()).hexdigest()
protected.update({'stemVertexStart':head_count,'stemIndexStart':index_count,
 'stemControlPoints':[[*main_map(Vector(q)),.006] for q in main_source]+[[.14,.56,-.010]],
 'secondaryStemControlPoints':[[*side_map(Vector(q)),.006] for q in side_source]})
asset.write_text(json.dumps(protected,separators=(',',':')))
after=json.loads(asset.read_text());check={key:after[key] for key in ['position','normal','uv','anchor','index']}
after_hash=hashlib.sha256(json.dumps(check,separators=(',',':')).encode()).hexdigest();assert before_hash==after_hash
(OUT/'protected-head-verification.json').write_text(json.dumps({'unchanged':True,'before':before_hash,'after':after_hash,'headVertices':head_count,'stemVertices':len(stem_data['anchor'])},indent=2))
scene=bpy.context.scene;scene.camera.location=(.6,-1.6,8);scene.camera.data.ortho_scale=6.4
scene.render.resolution_x=900;scene.render.resolution_y=1400;scene.cycles.samples=8
scene.render.filepath=str(OUT/'approved-stems-model.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'apps/web/design/main-flower-traced.blend'));bpy.ops.render.render(write_still=True)
print('APPROVED_FLOWER_STEMS',W,H,len(stem_data['anchor']),before_hash)
