# 主花与副花配套茎秆重建

当前版本以用户确认的新效果图 `../src/assets/flower-stems-approved-reference.png` 为不可变参考。使用 Blender 5.2.2 LTS 脚本 `build_reference_flower_stems.py`，逐段追踪主副茎、两枚闭合花苞、五片细叶和枝节，保留原图纤维纹理，并生成真实浅浮雕深度。

主茎保持原根点 (.68,-4.55)，沿连续曲线接到原主花花托；副枝从同一曲线的实际点分出，接到已确认副花的花托。为了保留网页原有花头位置，纵向长度和分叉比例做构图适配，细叶及花苞则保留局部比例。这里是依照参考的适配建模，并非逐像素复制整张效果图。

新茎秆导出至 `../src/assets/flower-stems.traced.json`，使用独立原图纹理。`main-flower.traced.json` 仅保留已确认花头、花心和花托，避免旧茎叠加。两类网格使用同一 -.006 墙面锚点、.12 动态整体抬升、法线梯度与阴影深度公式；网页卸载时释放独立材质和几何，共享阴影材质只释放一次。移动、驻留及移开衰减继续使用原影响场。

可编辑总场景保存为 `main-flower-traced.blend`，两张纹理打包在 Blender 中。脚本保护18594个花头顶点的位置、法线、UV、锚点和拓扑；实际前后哈希均为 d5f4f17b6a47385b7de503a1d80e63566983913e0c1dc70ffb6d4b47e4b9f0de。新茎秆有20284个顶点。报告和修改前快照在 `D:/repository/tmp/confirmed-flower-stems-20261007/`。

类型检查、Vite 构建通过；993×790实际页面记录移动、静止2.6秒、移开4秒，浏览器无错误。截图 page-moving.png、page-stationary.png、page-away.png，Blender诊断渲染 approved-stems-model.png。真实页面是阴影与交互验收依据，诊断渲染只验证轮廓与纹理。当前仅本地预览。

固定正面投影仍包含原图光照，不能宣称恢复真实反照率，也不保证任意观看角度一致。

后续只修改这一版配套茎秆时使用 `build_reference_flower_stems.py`。旧 `rebuild_flower_stems.py` 和 `build_traced_main_flower.py` 保留作历史制作过程；它们会写回旧茎秆，不应直接用于当前版本。
