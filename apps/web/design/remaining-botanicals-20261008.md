# 剩余植株整组补齐（2026-10-08，待用户统一验收）

本轮以用户最后提供的完整效果图为基准，补齐中部前侧竹、最右侧长竹及其叶簇，以及四根剩余竹笋。用户明确要求全部完成后统一验收，本轮不再逐株中途等待。已有鸟、花、银杏、叶枝、竹子、竹笋及其纹理保持原状。

## 参考与制作方式

构图、轮廓、原色和 UV 使用 `bamboo-shoots-reference-20261007/01-approved-front.png`，1499 × 1049。原图 SHA256 为 `8fb287d539626ecb0cd6a43cc47efbcf94ef261a8966a3e7c80d9fb779d523f1`，本轮未改变。此前的左右斜视、竹子细节和竹笋细节多角度图继续用于浅浮雕厚度、折边和连接参考，没有重新生成提案图。

使用 Blender 5.2.2 LTS 实际运行 Python 脚本建模并保存可编辑 `.blend`。轮廓在放大的原图裁切中逐片描出；两株共 47 片不等大小的竹叶。主秆按原图弧度连续建模，竹节增加卷唇与细凹槽。叶柄取父曲线接点，侧枝根部高度与父秆表面连续衔接。四根竹笋分别描出嫩芽、笋壳外轮廓与斜向折边，不用一根笋缩放复制。

| 新对象 | 原生源文件 | 网页网格 | 顶点 / 三角形 |
| --- | --- | --- | --- |
| 中部前侧竹，11 片叶 | `center-bamboo-03.blend` | `../src/assets/center-bamboo-03.traced.json` | 36762 / 73432 |
| 最右侧长竹，36 片叶 | `right-bamboo-01.blend` | `../src/assets/right-bamboo-01.traced.json` | 100298 / 200300 |
| 中右部小竹笋 | `shoot-step-05.blend` | `../src/assets/shoot-step-05.traced.json` | 14472 / 28940 |
| 右部高竹笋 | `shoot-step-06.blend` | `../src/assets/shoot-step-06.traced.json` | 26512 / 53020 |
| 右部小竹笋 | `shoot-step-07.blend` | `../src/assets/shoot-step-07.traced.json` | 15236 / 30468 |
| 最右侧幼竹笋 | `shoot-step-08.blend` | `../src/assets/shoot-step-08.traced.json` | 4556 / 9108 |

每个对象都有同名 `-contours.json` 参数和 `-approved.png` 网页纹理。脚本为 `prepare_remaining_botanicals.py`、`build_remaining_bamboo.py`、`build_remaining_shoot.py`，`build_all_remaining.py` 顺序生成全部六个独立模型。每个原生文件打包对应纹理，保留未变形的可编辑部件和单独的网页高度预览副本。

统一坐标为 `x=(px/1499-.5)*14.222222`、`y=(.5-py/1049)*8`；锚点 -.006，薄壳厚 .003。竹秆有独立的不透明纤维 UV 条带。新轮廓纹理增加一源像素的透明度采样余量，避免现有 .90 阈值裁掉细叶柄；几何轮廓、全站透明度阈值不变。1992 个细曲线中心采样点均通过该阈值，分枝根部与父曲线截面无分离的采样结果保存在检查报告中。

## 间距、交叠与动态阴影

位置和比例沿用参考图。与已有裂叶和右侧叶枝交叠处，只读查询 `ivy.traced.json`、`right-pair.traced.json` 的实际三角形高度，约束新竹局部表面，保留原有植株在前的关系。读取过程没有写入旧模型。完整构图、中央交叠和右侧交叠另有 Blender 诊断图，用于发现穿插和构图问题；它们不是网页截图。

网页通过 `../src/components/botanicalReferenceRemaining.ts` 配对六个独立网格及纹理，在现有 `BotanicalScene.vue` 中接入。新对象沿用原竹材质、根部过渡、法线梯度和 `rootedDepthMaterial`，开启投射与接收真实阴影。移动、驻留、历史衰减、离场回落和 .12 离墙抬升保持原公式，新增纹理加载计数、视口缩放、菜单色调同步以及卸载释放。手机原布局不变。

固定正面采用原图投影；原图已有光照，投影混合用于避免重复压灰。该方式不是恢复出的真实反照率，不保证任意角度和任意灯光的照片级一致。Blender 诊断使用同方向的真实几何投影，正面和左右 30° 视图均已渲染。

## 验证与验收状态

- 68 个既有资产 SHA256 全部一致；场景本轮差异只有新增对象与必要生命周期接入，迷雾、灯光及旧对象逻辑没有修改。
- 六个网格的数组维度、索引范围、有限数值检查通过，没有零面积三角形；每个 `.blend` 实际存在并打包纹理。
- `vue-tsc --noEmit` 退出码 0；最终 Vite 构建退出码 0。本轮沿用既有格式，构建仍有场景包过大警告，场景输出约 148.19 MB（38.66 MB gzip）；不能据此声称实际帧率或首次加载性能已经验证。
- 本地主页、场景模块、新加载模块、六个网格导入和六张纹理的 HTTP 检查均返回 200。
- 自动浏览器此前实际返回本地 URL 安全策略拒绝，本轮没有绕过；正常网页的移动、静止、移开状态与实际视口截图尚未验证，等待用户在已打开的预览页统一验收。构建通过和 Blender 渲染不代表网页视觉验收通过。

检查报告、修改前快照、源图分析裁切、六个对象的三角度渲染和整组诊断保存在 `D:/repository/tmp/botanical-remaining-20261008/`。完整诊断为 `all-plants-blender-diagnostic.png`，中央与右侧分别为 `center-overlap-blender-diagnostic.png`、`right-overlap-blender-diagnostic.png`；报告为 `verification.json`、`connection-verification.json` 和 `scene-local.diff`。

预览地址：`http://127.0.0.1:5173/`。本轮未提交、推送或部署。
