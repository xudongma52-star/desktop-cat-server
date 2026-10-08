# 主花原图投影验证版

参考为用户确认的生成图，原文件完整保存在 `../src/assets/flower-approved-reference.png`。

`build_traced_main_flower.py` 在 Blender 5.2.2 中按像素坐标描出八片花瓣，分别制作浅起伏表面和投影 UV。保存的可编辑文件是 `main-flower-traced.blend`；已求值网页几何是 `../src/assets/main-flower.traced.json`。没有把图像明暗直接当作几何深度。

网页使用两朵逐瓣描边花头、主花弯茎及花托。副花缩小并改变侧倾和花瓣展开角度；细叶及花苞按新确认图重建，分枝沿真实主茎重新连接。独立投影材质共用鼠标双通道影响场，显现时抬升并产生与其他植物相同权重的动态侧向阴影，移开后回落，菜单夜色保持原有过渡。

当前主副茎已用 `build_reference_flower_stems.py` 按新确认图局部重建，花头数据保持；茎秆、花托、附着细叶及花苞统一锚点和 .12 动态抬升。具体源文件、保护比对与实测记录见 [flower-stems-rebuilt.md](flower-stems-rebuilt.md)。只修改茎秆时使用该脚本，避免重新生成已确认花头。

当前属于**固定正面、带原图光照的投影外观**：局部均值归一化仅是近似，材质最终仍混合了原图外观，不能称为恢复了真实反照率。花心颗粒及细微褶皱目前主要来自投影纹理，未完成独立法线/粗糙度贴图，也未宣称任意视角与照明都与原图一致。

近距离验证页：`http://127.0.0.1:53085/review/main-flower.html`，左侧原图、右侧真实网格与投影材质。只验证花头形状和纹理，不代表当前完整花茎及网页鼠标动态。

Blender 输出和操作日志在 `D:/repository/tmp/reference-main-flower-20261006`。可执行 `D:/compile/blender/blender-5.2.2-windows-x64/blender.exe --background --factory-startup --threads 4 --python apps/web/design/build_traced_main_flower.py` 重建。

上述命令重建的是基础花朵版本与旧茎秆；需要当前新弯茎时，再运行 `build_reference_flower_stems.py`，该脚本会移除旧茎，保留已确认花头，并独立导出配套茎秆与纹理。
