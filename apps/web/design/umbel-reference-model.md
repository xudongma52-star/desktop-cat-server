# 疏花复伞形植株参考重建

确认图 `../src/assets/umbel-approved-reference.png`（1024×1536）保持不变。Blender 5.2.2 LTS 通过 `build_traced_umbel.py` 制作三片分别描边的浅齿叶、连续木质弯茎、独立曲花梗与按图定位的小五瓣花。叶脉、纤维和花瓣微细纹理由原图UV保留，没有以图像明暗生成几何高度。

复伞冠分组追踪曲梗与各朵花的中心，小花轮廓采用薄五瓣面，局部形态为合理重建而非每朵微小花瓣的像素级追踪。当前是固定正面、带原图光照的投影外观，不保证任意照明及观察角度一致。

像素映射比例 .0042，原图花序汇接点映射到(2.70,1.55)，整体略向右布置以避开用户指定保护的花朵和四叶裂叶；根部连续延伸到原底缘植物带。源文件 `umbel-traced.blend` 打包纹理，导出 `../src/assets/umbel.traced.json`，共51232个顶点。

桌面旧makeMainUmbel实例及斜穿目标植株的branchedSprig第7组移除，右外侧第6组与手机实例保持。没有修改保护植株的几何和纹理；ivy、main-flower、flower-stems及其他已确认参考JSON资产的前后SHA实际一致，报告位于 `D:/repository/tmp/umbel-reference-20261007/protected-verification.json`。

新模型沿用-.006锚点、.12整体抬升、共享法线梯度与一致的customDepthMaterial，加入纹理加载完成条件、同步缩放及菜单颜色、卸载释放独立资源。用户要求的临时无迷雾模式保持，全部植物和真实投影持续显示。

类型检查与Vite构建通过；1075×790正常页面状态ready，浏览器无错误。实际截图page-final.png、Blender诊断渲染umbel-projection.png、代码快照位于上述目录。只更新本地预览，未提交或部署。
