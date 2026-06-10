# PNG `readChunks` 方法内存效率分析报告

> 分析对象：`org.apache.commons.imaging.formats.png.PngImageParser#readChunks` 及其相关调用链
> 源码版本：基于 `commons-imaging` 仓库当前版本

---

## 一、方法概览

`readChunks` 有两个重载：

```java
// PngImageParser.java:687-693
private List<PngChunk> readChunks(final ByteSource byteSource,
                                  final ChunkType[] chunkTypes,
                                  final boolean returnAfterFirst)
        throws ImagingException, IOException;

// PngImageParser.java:695-740
private List<PngChunk> readChunks(final InputStream is,
                                  final ChunkType[] chunkTypes,
                                  final boolean returnAfterFirst)
        throws ImagingException, IOException;
```

该方法把 PNG 文件中需要保留的 chunk 全部解析并实例化为 `PngChunk` 对象，塞进一个 `ArrayList` 返回给上层业务。上层调用之后会对返回的 List 做多次 `filterChunks(...)` 再做具体处理。

---

## 二、至少三处内存浪费设计

### 问题 1：全量保留大体积 IDAT 数据，生命周期与整个 chunk list 等长

- **位置**：`PngImageParser.java:713`、`PngImageParser.java:725`
- **代码**：

```java
// 713 行：保留 chunk 时，一次性读入 length 字节
bytes = BinaryFunctions.readBytes("Chunk Data", is, length,
        "Not a Valid PNG File: Couldn't read Chunk Data.");
...
// 725 行：构造对象并加入结果列表
result.add(ChunkType.makeChunk(length, chunkType, crc, bytes));
```

- **原因**：
  - IDAT 是 PNG 真正承载图像数据的块，单个文件往往包含数十到上百个 IDAT，总体积可达数 MB～数 GB。
  - `readChunks` 把所有符合过滤条件的 chunk（包含全部 IDAT）作为实例放入 `result` 中，直到调用者处理完成后才释放。
  - 调用者在 `getBufferedImage`（`PngImageParser.java:198-206`）里对所有 IDAT 执行 `baos.write(bytes)` 再合并成一份 `compressed`，期间 **原始 IDAT chunk 对象与合并后的 `compressed` 同时驻留内存**，对大图像是典型的 "2× 数据量" 浪费。

---

### 问题 2：`PngChunk` 构造器与 `getBytes()` 防御性拷贝造成双倍分配

- **位置**：
  - `PngChunk.java:52` — 构造期 `bytes.clone()`
  - `PngChunk.java:77-78` — 读取期 `bytes.clone()`
- **代码**：

```java
// PngChunk.java:52
public PngChunk(final int length, final int chunkType,
                final int crc, final byte[] bytes) {
    this.bytes = Objects.requireNonNull(bytes, "bytes").clone(); // ← 第 1 次拷贝
    ...
}

// PngChunk.java:77-78
public byte[] getBytes() {
    return bytes.clone(); // ← 第 2 次拷贝（每次调用都会产生）
}
```

- **调用示例**：在 `getBufferedImage` 中循环每个 IDAT 时调用了 `pngChunkIDAT.getBytes()`（`PngImageParser.java:201`），意味着：

  1. `readBytes` 分配了 `byte[length]`；
  2. `new PngChunk(...)` 又 clone 了一次；
  3. `getBytes()` 再 clone 一次。

  对一个 1MB 的 IDAT，峰值瞬时占用 ≈ 3MB，实际只需一份。

---

### 问题 3：`ArrayList` + `filterChunks` 导致的重复结构与列表对象冗余

- **位置**：`PngImageParser.java:696`（`new ArrayList<>()`）、`PngImageParser.java:141-150`（`filterChunks`）
- **代码**：

```java
// readChunks 中
final List<PngChunk> result = new ArrayList<>();   // 696 行

// 上层多个方法中（169-170, 420, 558, 576, ...）
final List<PngChunk> chunks = readChunks(byteSource,
        new ChunkType[] { ChunkType.IHDR, ChunkType.PLTE, ... }, false);
final List<PngChunk> IHDRs = filterChunks(chunks, ChunkType.IHDR);
final List<PngChunk> PLTEs = filterChunks(chunks, ChunkType.PLTE);
...

private List<PngChunk> filterChunks(List<PngChunk> chunks, ChunkType type) {
    final List<PngChunk> result = new ArrayList<>();
    for (PngChunk chunk : chunks) {
        if (chunk.getChunkType() == type.value) {
            result.add(chunk);   // 146 行
        }
    }
    return result;
}
```

- **原因**：
  - 调用方往往只关心少量特定类型的 chunk，但 `readChunks` 依然把所有通过过滤的 chunk 放在一个扁平 List 中。
  - 随后每个需要的类型都再做一次 `filterChunks`，**每类生成一个新 ArrayList + 对应引用数组**。对一张含有很多 ancillary chunk（如 tEXt、iTXt、sRGB、gAMA、iCCP、pHYs 等）的 PNG，方法内部会制造出许多小列表对象，增加 GC 压力。
  - `readChunks` 本身对每种类型的 chunk 没有索引，也使得上层无法避免 O(n) 线性扫描。

---

### （补充）问题 4：一次性组装 `compressed` 导致峰值内存 ≥ 2× IDAT

- **位置**：`PngImageParser.java:198-206`
- **代码**：

```java
ByteArrayOutputStream baos = new ByteArrayOutputStream();
for (final PngChunk IDAT : IDATs) {
    final PngChunkIdat pngChunkIDAT = (PngChunkIdat) IDAT;
    final byte[] bytes = pngChunkIDAT.getBytes();  // clone
    baos.write(bytes);
}
final byte[] compressed = baos.toByteArray();     // 再拷贝一次
baos = null;
```

调用 `getBytes()` → `baos.write` → `toByteArray()` 使得在某一瞬间 **IDAT chunk 列表 + 内部 byte[] + baos 内部缓冲 + compressed 副本**同时存在，峰值占用是原始压缩数据量的数倍。这虽然不在 `readChunks` 内部，但与 `readChunks` 选择把 IDAT 以独立 `byte[]` 形式暴露的设计直接相关。

---

## 三、优化方案：软引用缓存最近访问的 chunk 数据

### 3.1 设计思路

1. **按需加载**：`readChunks` 不再立即把 chunk 的 payload 字节全部读入并持有，而是记录每个 chunk 在流中的偏移与长度，只在第一次被访问时才真正 I/O。
2. **`SoftReference` 缓存**：对已解析过的 chunk payload 以 `SoftReference<byte[]>` 形式缓存；当 JVM 内存紧张时 GC 会自动回收它们，从而避免 OOM。
3. **LRU 上限**：为了防止过多软引用 Reference 对象自身造成开销，使用一个有界 `LinkedHashMap` 维护最近使用过的若干个 chunk。
4. **防御性拷贝改为共享不可变引用**：`PngChunk` 内部直接持有缓存返回的 `byte[]`（或通过 getter 返回不可变视图/只读副本），消除 `clone` 的重复分配。

### 3.2 数据结构

```
ChunkKey  = (ByteSource identity, offset)
ChunkMeta = (offset, length, chunkType, crc)
cache     = LinkedHashMap<ChunkKey, SoftReference<byte[]>> (access-ordered, bounded)
```

### 3.3 实现伪代码

```java
// PngImageParser.java（改造版）

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;

public class PngImageParser extends AbstractImageParser<...> {

    // 每个 ChunkKey -> 软引用的 payload 字节；access-order + 固定上限，天然 LRU
    private final Map<ChunkKey, SoftReference<byte[]>> chunkCache =
            new LinkedHashMap<ChunkKey, SoftReference<byte[]>>(
                    16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(
                        Map.Entry<ChunkKey, SoftReference<byte[]>> e) {
                    return size() > 128;   // 最多保留 128 个最近使用的 chunk
                }
            };

    // ---- 元数据类：替代 PngChunk 里整段 byte[] ----
    private static final class ChunkMeta {
        final long  offset;      // chunk data 在文件中的起始偏移
        final int   length;      // chunk data 长度
        final int   chunkType;
        final int   crc;

        ChunkMeta(long offset, int length, int chunkType, int crc) {
            this.offset = offset; this.length = length;
            this.chunkType = chunkType; this.crc = crc;
        }
    }

    // ---- 缓存 Key：使用 (ByteSource, offset) ----
    private static final class ChunkKey {
        final Object sourceId;   // 注意：实际建议使用 ByteSource 的可比较身份
        final long   offset;
        ChunkKey(Object sourceId, long offset) {
            this.sourceId = sourceId;
            this.offset   = offset;
        }
        @Override public int hashCode() {
            return Objects.hash(sourceId, offset);
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof ChunkKey)) return false;
            ChunkKey k = (ChunkKey) o;
            return offset == k.offset && Objects.equals(sourceId, k.sourceId);
        }
    }

    // 【改造点 1】readChunks 只返回 ChunkMeta，不真正读 payload
    private List<ChunkMeta> readChunkMetas(ByteSource byteSource,
                                           ChunkType[] chunkTypes,
                                           boolean returnAfterFirst)
            throws IOException, ImagingException {
        List<ChunkMeta> metas = new ArrayList<>();
        try (InputStream is = byteSource.getInputStream()) {
            readSignature(is);
            // 用 PositionInputStream 包装（见下），记录每个 chunk data 的偏移
            PositionInputStream pis = new PositionInputStream(is);
            while (true) {
                int length    = read4Bytes(pis);
                int chunkType = read4Bytes(pis);
                long dataOff  = pis.position();   // payload 起点

                boolean keep = keepChunk(chunkType, chunkTypes);
                if (keep) {
                    // 不读字节，仅记录位置
                    metas.add(new ChunkMeta(dataOff, length, chunkType,
                                            read4Bytes(pis))); // CRC
                    if (returnAfterFirst) skipBytes(pis, length);
                } else {
                    skipBytes(pis, length);
                }

                if (chunkType == ChunkType.IEND.value) break;
            }
            return metas;
        }
    }

    // 【改造点 2】按需解引用：命中缓存直接拿，未命中或软引用已被回收再重读
    byte[] getChunkBytes(ByteSource byteSource, ChunkMeta meta)
            throws IOException {
        ChunkKey key = new ChunkKey(byteSource, meta.offset);

        synchronized (chunkCache) {          // 保证结构修改线程安全（见第四节）
            SoftReference<byte[]> ref = chunkCache.get(key);
            byte[] payload = ref != null ? ref.get() : null;

            if (payload != null) return payload;   // 命中

            // 未命中：直接 seek 到偏移并读取 length 字节
            payload = readRange(byteSource, meta.offset, meta.length);

            chunkCache.put(key, new SoftReference<>(payload));
            return payload;
        }
    }

    // ---------- 上层用法：IDAT 合并的内存下降 ----------
    // getBufferedImage 中：
    //
    // List<ChunkMeta> idats = filterMetas(metas, ChunkType.IDAT);
    // try (OutputStream out = inflater) {                  // 流式解压缩，不再先拼接
    //     for (ChunkMeta m : idats) {
    //         byte[] payload = getChunkBytes(byteSource, m);
    //         out.write(payload);
    //         // payload 被 SoftReference 持有，方法结束后可被回收
    //     }
    // }
}
```

> 说明：`PositionInputStream` / `readRange` 属于 ByteSource 层面的辅助实现（`commons-imaging` 的 `ByteSource` 已经支持 `getInputStream(start)` 或类似能力），这里不再展开。核心变化是 **把 "把所有 chunk 都搬上堆" 改成 "按需 seek + SoftReference 缓存"**。

---

## 四、多线程环境下为什么不会抛出 `ConcurrentModificationException`？

要点不是"软引用线程安全"，而是 **访问缓存的方式避免了 Iterator 并发修改检测**。

### 4.1 `ConcurrentModificationException` 的触发前提

JDK 的 `ArrayList` / `LinkedHashMap` 等在通过 **迭代器（包括 for-each）遍历**过程中，若检测到 `modCount` 与迭代器记录的 `expectedModCount` 不一致，便会在 `checkForComodification()` 处抛出异常。也就是说：

> **只有"边迭代边修改"才会触发**；纯 `get` / `put` 不使用迭代器时不会抛该异常。

### 4.2 上面方案的并发路径

```java
SoftReference<byte[]> ref;
byte[] payload;
synchronized (chunkCache) {                       // ①
    ref = chunkCache.get(key);                    // ② 纯 Map.get → 无迭代
    payload = ref == null ? null : ref.get();     // ③ SoftReference.get() 是 native / volatile 读
    if (payload == null) {
        payload = readRange(...);
        chunkCache.put(key, new SoftReference<>(payload));  // ④ LinkedHashMap.put
    }
}
```

1. **同步块①**：`chunkCache` 的所有读写都在同一对象锁保护之下（若希望更高并发可用 `ConcurrentHashMap`）。任何时刻只有一个线程在修改 `LinkedHashMap` 结构，因此内部 `modCount` 的变化本身就是串行化的，**不存在"迭代 A 线程 + 修改 B 线程"的窗口**。
2. **路径 ②/③/④ 都不产生迭代器**：没有 for-each、`keySet().iterator()`、`values()` 的显式/隐式迭代，也就没有 `checkForComodification()` 的调用点。
3. **`SoftReference.get()` 的安全**：软引用本身由 JVM 的 Reference 处理器管理，`get()` 只是原子地读 referent 指针，并不进入任何 Java 级容器遍历；即便在回收瞬间读到 `null`，也只是触发一次重新读取，没有异常抛出。
4. **LRU 的 `LinkedHashMap` 重排序**：access-order 的 `LinkedHashMap` 在 `get` 时会把条目移动到链表尾部。若未加同步，多线程并发 `get` 可能破坏链表结构（这是另一种问题），但 **它仍然不会抛出 `ConcurrentModificationException`**——因为仍然没有 "迭代器侧" 在做 `checkForComodification`。加上同步块后，结构重排也被串行化，链表一致性和无 CME 同时成立。

> 总结：`ConcurrentModificationException` 源于"迭代 + 并发修改"。本方案的缓存路径
> （a）没有任何迭代，
> （b）在同一同步块中完成读/写，
> 因此天然满足不抛 `ConcurrentModificationException` 的条件。

---

## 五、收益评估

| 指标                     | 现状                              | 软引用缓存方案                        |
| ------------------------ | --------------------------------- | ------------------------------------- |
| 初始读入峰值占用         | Σ chunk.length 字节（含 IDAT）    | ≈ 元数据（数个 Integer/Long）         |
| 大文件 IDAT 峰值占用     | ≥ 2 × 压缩数据量（clone + baos）  | 最多最近 N 个 chunk + 流写出时一份    |
| IDAT `clone` 次数        | 每个 IDAT 3 次（构造 + 两次读）   | 0 ~ 1 次（按需读取，可直接使用缓存）  |
| GC 压力                  | 大量 `byte[] + ArrayList` 小对象  | 引用对象为主，可被 GC 主动回收        |
| 对 "只需要 IHDR" 这类场景 | 依然扫描整个文件并保存可能巨大 chunk | 只保留元数据，`returnAfterFirst` 可提前退出 |

实际效果：解析大 PNG 的峰值堆占用有望下降 **50%–80%**，在移动端/受限堆环境尤其明显。

---

## 六、相关源码行号速查

| 文件                                                       | 行号 | 作用 |
| ---------------------------------------------------------- | ---- | ---- |
| `PngImageParser.java`                                      | 695–740 | `readChunks(InputStream, ...)` 主实现 |
| `PngImageParser.java`                                      | 713  | 无条件读取 chunk 数据 |
| `PngImageParser.java`                                      | 725  | 把 chunk 添加到 `result` 列表 |
| `PngImageParser.java`                                      | 141–150 | `filterChunks` 按类型二次线性扫描 |
| `PngImageParser.java`                                      | 198–206 | `getBufferedImage` 中 IDAT 二次组装 |
| `chunks/PngChunk.java`                                     | 52   | 构造器里的 `bytes.clone()` |
| `chunks/PngChunk.java`                                     | 77–78 | `getBytes()` 每次调用的 `bytes.clone()` |
