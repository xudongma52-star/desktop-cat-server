# 桌面猫素材说明

## 二次元像素小黑猫

当前正式资源均为带透明通道的横向 8 帧精灵表：

- `src/renderer/src/assets/cat/cat-idle-pixel-v4.png`
- `src/renderer/src/assets/cat/cat-sleep-pixel-v4.png`
- `src/renderer/src/assets/cat/cat-groom-pixel-v4.png`
- `src/renderer/src/assets/cat/cat-play-pixel-v4.png`
- `src/renderer/src/assets/cat/cat-eat-pixel-v4.png`
- `src/renderer/src/assets/cat/cat-walk-pixel-v4.png`
- `src/renderer/src/assets/cat/cat-run-pixel-v4.png`

资源通过内置 ImageGen 的 `stylized-concept` 模式生成。用户照片没有复制到仓库；旧版、由照片衍生的猫图只作为身份参考。角色固定特征为纯黑短毛、圆脸、短嘴、小黑鼻、厚爪、粗尾巴和琥珀色圆眼。

统一提示词要求：原创建模；96×96 逻辑像素网格；10–14 色有限色板；明显的 2×2 / 3×3 像素簇、阶梯轮廓、少量抖色和单像素眼睛高光；硬边；禁止抗锯齿、柔和渐变、写实毛发、3D 光泽、矢量曲线和 AI 插画式润色；8 个等宽单元横向排列；透明背景；相同机位、比例、光向和地面线。

各动作提示词：

- `idle`：坐姿呼吸、眨眼、单耳轻弹、尾尖延迟卷动。
- `sleeping`：蜷成月牙，胸腹真实起伏，耳朵与尾尖轻动。
- `grooming`：抬前爪、伸舌舔爪两次、湿爪擦脸和耳后、放下前爪。
- `playing`：压低前身、扭动后腰、抬爪拍击、小幅扑跃并落地。
- `eating`：嗅冻干、低头咬取、咀嚼、舔嘴；冻干在入口后消失。
- `walking`：四足四拍步态，肩胯轮换，头部反向补偿，尾巴延迟跟随。
- `running`：接触、压缩、通过、伸展、腾空、落地；四肢、脊柱、头和尾巴逐帧联动。

渲染端以 `steps(8)` 播放精灵表，并使用 `image-rendering: pixelated`。动作来自每一帧的身体姿态变化，不再对同一张图片做晃动来假装运动。

## 猫叫

猫叫由 `src/renderer/src/cat-sounds.ts` 使用 Web Audio API 在本地生成，不读取网络资源。打招呼、触摸、提醒、开心和撒娇抗议使用不同音型，每类声音会轮换变体，并通过短渐入渐出控制爆音。
