from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageOps
import imageio_ffmpeg


WIDTH = 960
HEIGHT = 540
FPS = 60
DURATION_SECONDS = 4.1
ROOT = Path(__file__).resolve().parents[3]
FRAME_DIR = ROOT / "apps" / "web" / "src" / "assets" / "dusk-flight-frames"
OUTPUT_DIR = ROOT / "apps" / "web" / "src" / "assets"


def cover(image: Image.Image) -> Image.Image:
    return ImageOps.fit(image.convert("RGB"), (WIDTH, HEIGHT), Image.Resampling.LANCZOS)


def smoothstep(value: float) -> float:
    value = min(1.0, max(0.0, value))
    return value * value * (3.0 - 2.0 * value)


def zoom_toward(image: Image.Image, amount: float, x_bias: float, y_bias: float) -> Image.Image:
    crop_width = max(1, round(WIDTH / amount))
    crop_height = max(1, round(HEIGHT / amount))
    left = round((WIDTH - crop_width) * x_bias)
    top = round((HEIGHT - crop_height) * y_bias)
    return image.crop((left, top, left + crop_width, top + crop_height)).resize(
        (WIDTH, HEIGHT), Image.Resampling.LANCZOS
    )


def add_zoom_smear(image: Image.Image, strength: float, x_bias: float, y_bias: float) -> Image.Image:
    if strength <= .002:
        return image
    result = image
    # 多次轻微推进形成沿飞行方向展开的速度拖影，比统一高斯模糊更有纵深。
    for step in range(1, 5):
        sample = zoom_toward(image, 1 + strength * step / 4, x_bias, y_bias)
        result = Image.blend(result, sample, .16)
    return result


def add_subtle_vignette(image: Image.Image, strength: float) -> Image.Image:
    ellipse = Image.new("L", (WIDTH, HEIGHT), 0)
    draw = ImageDraw.Draw(ellipse)
    draw.ellipse(
        (round(WIDTH * .03), round(HEIGHT * .01), round(WIDTH * .97), round(HEIGHT * .99)),
        fill=255,
    )
    edge = ImageChops.invert(ellipse.filter(ImageFilter.GaussianBlur(round(WIDTH * .12))))
    edge = ImageEnhance.Brightness(edge).enhance(strength)
    return Image.composite(Image.new("RGB", (WIDTH, HEIGHT), (30, 22, 18)), image, edge)


shots = [cover(Image.open(path)) for path in sorted(FRAME_DIR.glob("frame-*.webp"))]
if len(shots) != 10:
    raise RuntimeError(f"Expected 10 flight frames, found {len(shots)}")

frame_count = round(FPS * DURATION_SECONDS)


def range_progress(value: float, start: float, end: float) -> float:
    return smoothstep((value - start) / (end - start))


def cut_energy(time: float, center: float, radius: float = .22) -> float:
    distance = abs(time - center) / radius
    return smoothstep(1.0 - distance) if distance < 1.0 else 0.0


def render_frame(index: int) -> Image.Image:
    time = index / (frame_count - 1) * DURATION_SECONDS
    # 三个锚点各自保持持续推进；透视差异只在贴近前景遮挡物的瞬间切换，
    # 不再把十张不同构图交叉溶解成逐张叠放的幻灯片。
    if time < 1.5:
        progress = range_progress(time, 0.0, 1.5)
        frame = zoom_toward(shots[0], 1.0 + .48 * progress, .52 + .30 * progress, .53 - .10 * progress)
        travel_speed = .008 + .026 * progress
    elif time < 2.82:
        progress = range_progress(time, 1.5, 2.82)
        frame = zoom_toward(shots[8], 1.0 + .40 * progress, .22 - .15 * progress, .50 - .08 * progress)
        travel_speed = .012 + .032 * progress
    else:
        progress = range_progress(time, 2.82, DURATION_SECONDS)
        frame = zoom_toward(shots[9], 1.0 + .43 * progress, .22 - .08 * progress, .48 - .07 * progress)
        travel_speed = .014 + .036 * progress

    # 切点被高速贴近的枝叶/石墙完全吞没；最模糊的一帧前后直接换景，画面中不存在可见溶解。
    occlusion = max(cut_energy(time, 1.5), cut_energy(time, 2.82))
    frame = add_zoom_smear(frame, travel_speed + .105 * occlusion, .48, .46)
    if occlusion > .01:
        frame = frame.filter(ImageFilter.GaussianBlur(.4 + 13.0 * occlusion))
        frame = ImageEnhance.Color(frame).enhance(1.0 - .34 * occlusion)
        frame = ImageEnhance.Brightness(frame).enhance(1.0 - .52 * occlusion)
        # 最近景的枝叶/石墙在切点铺满镜头，彻底隐藏透视跳变，而不是让两张图彼此溶解。
        frame = Image.blend(frame, Image.new("RGB", frame.size, (30, 22, 18)), .84 * occlusion)

    total_progress = min(1.0, time / DURATION_SECONDS)
    vignette = .18 + .09 * smoothstep(total_progress) + .18 * occlusion
    return add_subtle_vignette(frame, vignette)


def encode(path: Path, codec: str, output_params: list[str]) -> None:
    writer = imageio_ffmpeg.write_frames(
        str(path),
        (WIDTH, HEIGHT),
        fps=FPS,
        codec=codec,
        macro_block_size=2,
        pix_fmt_in="rgb24",
        pix_fmt_out="yuv420p",
        output_params=output_params,
    )
    writer.send(None)
    try:
        for frame_index in range(frame_count):
            writer.send(render_frame(frame_index).tobytes())
    finally:
        writer.close()


encode(
    OUTPUT_DIR / "dusk-arrival.mp4",
    "libx264",
    ["-crf", "24", "-preset", "slow", "-movflags", "+faststart"],
)
encode(
    OUTPUT_DIR / "dusk-arrival.webm",
    "libvpx-vp9",
    ["-crf", "34", "-b:v", "0", "-deadline", "good", "-cpu-used", "2"],
)
