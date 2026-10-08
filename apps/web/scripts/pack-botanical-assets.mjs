import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'
import { gzipSync } from 'node:zlib'
import { MeshoptEncoder, MeshoptDecoder } from 'meshoptimizer'
import sharp from 'sharp'

const assets = fileURLToPath(new URL('../src/assets/', import.meta.url))
const config = JSON.parse(await readFile(new URL('./botanical-assets.json', import.meta.url), 'utf8'))
await mkdir(assets + 'optimized', { recursive: true })
await Promise.all([MeshoptEncoder.ready, MeshoptDecoder.ready])
const manifest = { models: {}, textures: {}, wall: {} }
const sha = bytes => createHash('sha256').update(bytes).digest('hex')

// Float32 是现有 Three.js 上传到 GPU 的实际精度；不量化、不减面、不重排索引。
for (const name of config.models) {
  const source = JSON.parse(await readFile(assets + name + '.traced.json', 'utf8'))
  const attributes = [], encodedParts = []
  let offset = 0
  for (const key of ['position', 'normal', 'uv', 'anchor', 'index']) {
    const components = key === 'position' || key === 'normal' ? 3 : key === 'uv' ? 2 : 1
    const array = key === 'index' ? Uint32Array.from(source[key]) : Float32Array.from(source[key])
    const bytes = new Uint8Array(array.buffer), count = array.length / components, stride = components * 4
    const packed = key === 'index'
      ? MeshoptEncoder.encodeIndexSequence(bytes, count, stride)
      : MeshoptEncoder.encodeVertexBuffer(bytes, count, stride)
    const decoded = new Uint8Array(bytes.length)
    if (key === 'index') MeshoptDecoder.decodeIndexSequence(decoded, count, stride, packed)
    else MeshoptDecoder.decodeVertexBuffer(decoded, count, stride, packed)
    if (!Buffer.from(bytes).equals(Buffer.from(decoded))) throw Error(`Geometry changed: ${name}/${key}`)
    attributes.push({ key, count, stride, offset, length: packed.length, sha256: sha(bytes) })
    encodedParts.push(packed); offset += packed.length
  }
  const header = Buffer.from(JSON.stringify({ version: 1, attributes }))
  const prefix = Buffer.alloc(8); prefix.write('BTR1'); prefix.writeUInt32LE(header.length, 4)
  const uncompressed = Buffer.concat([prefix, header, ...encodedParts])
  const output = gzipSync(uncompressed, { level: 9 })
  await writeFile(assets + 'optimized/' + name + '.mesh.gz', output)
  manifest.models[name] = { bytes: output.length, decodedBytes: uncompressed.length, sha256: sha(output), attributes }
}

// 无损 WebP 保留完整分辨率和 RGBA；解码后的逐像素检查是生成流程的一部分。
for (const name of config.textures) {
  const source = await readFile(assets + name)
  let output = await sharp(source).webp({ lossless: true, effort: 6 }).toBuffer()
  const originalPixels = await sharp(source).ensureAlpha().raw().toBuffer()
  const decodedPixels = await sharp(output).ensureAlpha().raw().toBuffer()
  // WebP 会丢弃全透明像素的 RGB；此类裁切图保留 PNG，避免纹理采样边缘发生变化。
  const exact = originalPixels.equals(decodedPixels)
  const outputName = exact ? name.replace(/\.png$/, '.webp') : name
  if (!exact) output = source
  await writeFile(assets + 'optimized/' + outputName, output)
  manifest.textures[name] = { file: outputName, bytes: output.length, sourceBytes: source.length, sha256: sha(output), pixelSha256: sha(originalPixels) }
}
for (const name of ['beige_wall_001_nor_gl_1k.jpg', 'beige_wall_001_rough_1k.jpg']) {
  manifest.wall[name] = { bytes: (await readFile(assets + name)).length }
}
await writeFile(assets + 'optimized/manifest.json', JSON.stringify(manifest, null, 2) + '\n')
console.log(JSON.stringify({ models: Object.keys(manifest.models).length, modelBytes: Object.values(manifest.models).reduce((n, m) => n + m.bytes, 0), textures: Object.keys(manifest.textures).length, textureBytes: Object.values(manifest.textures).reduce((n, t) => n + t.bytes, 0), exactGeometryAndPixels: true }))
