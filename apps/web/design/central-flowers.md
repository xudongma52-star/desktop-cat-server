# 中央双花浮雕

造型参考是用户于 2026-10-06 确认的生成图，非第三方网站模型。

- `central-flowers.blend`：Blender 5.2.2 的可编辑花头模型，独立花瓣、花盘、花粒和萼片；包含打包的参考图。
- `build_reference_flowers.py`：重建模型、烘焙修改器、输出网页几何及 Cycles 验收图。
- `../src/assets/central-flowers.blender.json`：网页使用的已求值顶点、法线与索引。两朵花共 20,234 顶点、39,776 三角形。

这是第一版三维花头源文件。当前桌面网页已改用 `main-flower.traced.json` 的逐瓣描边双花及主茎；本文件对应资产继续供手机分支沿用原花头。叶片与花苞继续使用 `botanicalCentralFlowerSprig.ts` 的真实曲线和表面，未使用离线渲染图替代交互背景。

执行：`D:/compile/blender/blender-5.2.2-windows-x64/blender.exe --background --factory-startup --threads 4 --python apps/web/design/build_reference_flowers.py`。

离线验收图和 GLB 在 `D:/repository/tmp/reference-flowers-blender-20261006`。离线柔光用于判断造型，网页实际光影需单独验收。当前模型仍是按参考制作的三维重建，并未宣称与生成图逐像素一致。
