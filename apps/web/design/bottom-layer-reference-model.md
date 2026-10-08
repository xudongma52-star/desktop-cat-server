# 底部低层植物（2026-10-07）

本轮仅添加确认效果图中的左侧稀疏蕨羽、中间五片圆卵叶与右侧三条弧形细草。现有八组模型及纹理保留，桌面旧密集枝网不恢复，迷雾继续临时隐藏。手机原布局保持。

## 资产与生成

- 不变参考：`../src/assets/bottom-layer-approved-reference.png`，1463 × 1075。
- Blender 5.2.2 LTS 脚本：`build_traced_bottom_layer.py`。
- 可编辑源：`bottom-layer-traced.blend`，内嵌参考。
- 网格：`../src/assets/bottom-layer.traced.json`，43846 顶点、79560 三角形。
- 同一全屏坐标映射：x 为 `(px/W-.5)*8*16/9`，y 为 `(.5-py/H)*8`，UV 为 `(px/W,1-py/H)`；网页统一按视口宽高比缩放 x。Blender 校验相机同步设置非方形像素比例以吻合此映射。

叶片轮廓与蕨羽位置依照确认图追踪，叶柄沿连续曲线连接，草叶保持各自弧度。按用户追加要求，底部整体抬升改为与主体一致的 `.12`；独立可见材质、法线梯度和阴影深度同步使用此值。沿用全站光源和影响场，不改变主体材质。原图含固定光照，纹理投影适用于当前正面观察，不是独立测量反照率或完整环视模型。

## 本轮验证

Blender 实际生成、保存场景并输出隔离投影。`npm run typecheck` 及 Vite 构建通过，预览输出到 `D:/repository/tmp/left-stems-20261006/dist`。实际本地页面 `http://127.0.0.1:53085/` 于 1111 × 790 视口显示 ready，无浏览器 error/warn。

截图：`D:/repository/tmp/bottom-layer-20261007/page-final.png`。已有八组几何 SHA256 与修改前逐项相同，报告保存在同目录 `protected-verification.json`，本轮场景差异在 `scene-this-round.diff`。

当前按用户要求全量显现，运动／静止／移开动态阴影未在本轮启用验收；新模型仍接入原有影响场的高度与投影链路，恢复迷雾后应另做动态三状态检查。本轮仅更新本地预览，未推送或部署。
