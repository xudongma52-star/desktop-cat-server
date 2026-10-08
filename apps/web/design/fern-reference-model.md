# 左下蕨叶局部重建

2026-10-07，用户指定全页参考图仅用于左下蕨叶。本轮未改其他植物材质、形态或位置。参考原件在 `../src/assets/fern-approved-reference.png`（1487 × 1058）。

`build_traced_fern.py` 经 Blender 5.2.2 LTS 实际执行，保存带内嵌参考的 `fern-traced.blend` 和 `../src/assets/fern.traced.json`。连续羽轴按参考弯曲与渐细，左右十五组羽片逐节定位，带小裂片叶缘、独立中脉与浅厚度。原图 UV 保留浅灰绿、细纹及浅褐羽轴。本轮轮廓为手动近似追踪，尚非逐像素一致。

旧蕨叶仅从底部原资产的绘制索引移除，新资产 `bottom-layer-without-fern.traced.json` 保持 position、normal、uv、anchor 全部原样，仅筛除左下蕨羽三角形。原 `bottom-layer.traced.json` 保留。原十组网格 SHA256 全部未变，底中圆卵叶和右侧细草不重建。

独立参考材质使用未染色投影回调，不重复叠加前轮灰绿着色。可见、法线及共享阴影深度使用主体 `.12` 离墙抬升与原影响场，接入纹理完成计数、尺寸同步、夜色及资源释放。迷雾仍隐藏，动态三状态待恢复后验收。固定正面参考投影包含原光照，并非完整环视材质。

类型检查、Vite 构建与 diff 空白检查通过；实际本地页面 1111 × 790 显示 ready，浏览器无 error/warn。截图 `D:/repository/tmp/fern-20261007/page-final.png`；保护报告、本轮场景差异、Blender 隔离投影在同目录。仅本地预览更新，未推送或部署。
