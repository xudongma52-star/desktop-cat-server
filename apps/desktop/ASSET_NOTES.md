# 桌面猫素材说明

## Q 版黑猫

最终资源：

- `src/renderer/src/assets/cat/cat-running-v2.png`
- `src/renderer/src/assets/cat/cat-sleeping-v2.png`

两张图通过内置 ImageGen 生成。用户提供的照片只作为同一只宠物猫的身份参考，没有复制到仓库。角色固定特征为纯黑短毛、圆脸、短嘴、小黑鼻、厚爪和琥珀色圆眼。

奔跑图的最终提示词重点：

> Draw a highly stylized cartoon chibi version of the referenced real black cat: oversized round head, tiny plump body, short legs, thick paws, small triangular ears, thick tail and large amber eyes. Use clean 2D hand-drawn game-sprite styling, simplified shapes, smooth dark outlines, flat black and charcoal blocks, minimal fur detail, a running pose facing right, and a uniform #00FF00 background for extraction.

睡觉图的最终提示词重点：

> Draw the sleeping state for exactly the same cartoon chibi black cat, matching the running asset's proportions, colors and 2D style. Curl the cat on its side with closed eyes, head on both front paws and tail around the body. Use the same simplified black and charcoal blocks and a uniform #00FF00 background for extraction.

生成器没有稳定输出真实透明通道，因此先生成纯绿底图，再用 `scripts/process-cat-asset.ps1` 去除绿幕、裁剪透明边缘并缩放。最终资源均为 32 位透明 PNG，四角 alpha 为 0；原始 1536×1024 图片约 1.5 MB，最终单张约 190–200 KB。

## 猫叫

猫叫的来源、作者和 CC0 许可证记录在 `THIRD_PARTY_NOTICES.md`。运行时读取项目内 MP3，不访问网络。
