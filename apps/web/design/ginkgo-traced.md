# 银杏三叶与连续弯茎

参考为用户于 2026-10-06 确认的生成图，保存在 `../src/assets/ginkgo-approved-reference.png`。

`build_traced_ginkgo.py` 在 Blender 5.2.2 中逐轮廓制作三片大小不同的双瓣银杏叶、曲线主茎、两条叶柄和木质枝节；下段弯茎延伸至原页面落点。保存可编辑 `ginkgo-traced.blend` 和网页 `../src/assets/ginkgo.traced.json`。

叶脉与木质纹理由原图 UV 投影提供，属于固定正面且含参考光照的浅浮雕外观，不是独立去光照材质。网页单独加载纹理，并与两朵花共用显现、法线变形和阴影深度公式；其他植物和手机分支保持原实现。

重建：`D:/compile/blender/blender-5.2.2-windows-x64/blender.exe --background --factory-startup --threads 4 --python apps/web/design/build_traced_ginkgo.py`。

生成日志与投影验证图在 `D:/repository/tmp/reference-ginkgo-20261006`；最终验收使用实际网页鼠标移动及移开状态。
