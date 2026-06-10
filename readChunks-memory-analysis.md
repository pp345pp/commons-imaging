# PngImageParser.readChunks 方法内存效率分析报告

## 一、概述

`PngImageParser.readChunks` 是 PNG 解析的核心方法，负责从输入流中逐块读取 PNG chunk 数据。该方法在多个上层 API（`getBufferedImage`、`getImageInfo`、`getImageSize`、`getXmpXml` 等）中被调用。经过源码分析，该方法及其关联类型在内存使用上存在以下三处可优化的设计。

---

## 二、内存浪费问题分析

### 问题一：PngChunk 构造导致 bytes 数组双重复制

**涉及文件与行号：**

| 位置 | 文件 | 行号 |
|------|------|------|
| 读取原始 bytes | [PngImageParser.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L715) | 715 |
| 传入 ChunkType.makeChunk | [PngImageParser.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L725) | 725 |
| PngChunk 构造函数 clone | [PngChunk.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/chunks/PngChunk.java#L52) | 52 |
| getBytes() 再次 clone | [PngChunk.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/chunks/PngChunk.java#L79-L81) | 79–81 |

**原因分析：**

数据流如下：

```
BinaryFunctions.readBytes()         → 分配 byte[] A （来自 InputStream）
ChunkType.makeChunk(length,...,A)   → 传入 A
  └─ PngChunk(..., bytes=A)         → this.bytes = A.clone()  分配 byte[] B
```

- **A** 由 [BinaryFunctions.readBytes()](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/common/BinaryFunctions.java#L395-L401) 通过 `IOUtils.toByteArray()` 在堆上新分配。
- 在 [PngChunk 构造函数第 52 行](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/chunks/PngChunk.java#L52)，`this.bytes = Objects.requireNonNull(bytes, "bytes").clone()` 又创建了一份完整的克隆 **B**。
- `makeChunk` 返回后，原始数组 **A** 在 `readChunks` 的 while 循环体中被丢弃，成为垃圾对象。

**内存影响：** 每个被保留的 chunk，其字节数据在堆上存在**两份完全相同的副本**，实际只需要一份。`getBytes()` 方法（第 79–81 行）还会对外再返回一次 clone（第三次复制），这导致 `getBufferedImage` 的第 200 行 `pngChunkIDAT.getBytes()` 为每个 IDAT chunk 再次分配一个新数组。

对于普通元数据 chunk（IHDR 约 13 字节，PLTE 最多 768 字节），此问题影响有限。但对于 **IDAT chunk**（单 chunk 可达数 MB），双倍（乃至三倍）内存开销极为显著。

---

### 问题二：已解析子类仍然保留原始 bytes 字段

**涉及文件与行号：**

| 位置 | 文件 | 行号 |
|------|------|------|
| PngChunkIhdr 构造调用 super | [PngChunkIhdr.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/chunks/PngChunkIhdr.java#L57) | 57 |
| PngChunkIhdr 解析完成后不再使用 bytes | [PngChunkIhdr.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/chunks/PngChunkIhdr.java#L57-L74) | 57–74 |
| PngChunkPlte 构造调用 super | [PngChunkPlte.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/chunks/PngChunkPlte.java#L48) | 48 |
| PngChunkPlte 解析完成后不再使用 bytes | [PngChunkPlte.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/chunks/PngChunkPlte.java#L48-L65) | 48–65 |
| PngChunk 基类保留 bytes 字段 | [PngChunk.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/chunks/PngChunk.java#L37) | 37 |

**原因分析：**

`PngChunkIhdr` 的构造函数（第 57–74 行）在 `super(length, chunkType, crc, bytes)` 中将原始字节克隆并保存在父类的 `bytes` 字段中，然后立即使用 `ByteArrayInputStream` 将 bytes 解析为 `width`、`height`、`bitDepth`、`pngColorType`、`compressionMethod`、`filterMethod`、`interlaceMethod` 等结构化字段。

解析完成后，`PngChunkIhdr` **永远不会再访问** `bytes` 字段——所有对外暴露的信息都通过 `getWidth()`、`getHeight()` 等方法返回。然而，父类 `PngChunk` 的 `bytes` 字段（第 37 行）始终持有这份数据的克隆。

同样的问题存在于所有"在构造函数中完成数据解析"的子类：

| 子类 | 解析后字段 | bytes 大小（典型） | 浪费性质 |
|------|-----------|-------------------|---------|
| `PngChunkIhdr` | width, height, bitDepth, colorType, compressionMethod, filterMethod, interlaceMethod | 13 字节 | bytes 不再被访问 |
| `PngChunkPlte` | rgb[] | ≤ 768 字节 | bytes 不再被访问 |
| `PngChunkPhys` | xPPU, yPPU, unit | 9 字节 | bytes 不再被访问 |
| `PngChunkGama` | gamma | 4 字节 | bytes 不再被访问 |

这些 chunk 的 `bytes` 在构造后即成为**死数据**（dead storage），在 chunk 对象的整个生命周期中持续占用堆内存。

---

### 问题三：ArrayList 全量预载 + IDAT 数据多次复制

**涉及文件与行号：**

| 位置 | 文件 | 行号 |
|------|------|------|
| ArrayList 默认容量初始化 | [PngImageParser.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L696) | 696 |
| getBufferedImage 请求 IDAT chunk | [PngImageParser.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L169-L171) | 169–171 |
| IDAT 数据二次复制到 ByteArrayOutputStream | [PngImageParser.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L198-L207) | 198–207 |
| filterChunks 创建额外 List | [PngImageParser.java](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L141-L151) | 141–151 |

**原因分析：**

**(a) ArrayList 默认容量与扩容：** 第 696 行的 `new ArrayList<>()` 使用默认初始容量 10。对于包含数十甚至上百个 chunk 的 PNG 文件，ArrayList 会在读取过程中多次触发扩容（每次扩容伴随底层数组重新分配和元素复制）。虽然单次扩容开销不大，但这是一种可避免的内存抖动。

**(b) 所有 chunk 全量驻留：** `readChunks` 方法将所有匹配的 chunk 全部读入 `result` 列表后才返回。对于 `getBufferedImage`（第 169–171 行），请求的 chunk 类型包括 `IHDR`、`PLTE`、`IDAT`、`tRNS`、`iCCP`、`gAMA`、`sRGB`。其中 **IDAT 是最大的一类 chunk**——对于一张 4000×3000 的 PNG 照片，全部 IDAT chunk 的压缩数据合计可达 10–30 MB。

这些 IDAT 数据在 `readChunks` 返回后被全部保留在 `chunks` 列表中（每个 `PngChunkIdat` 的 `bytes` 字段）。

**(c) IDAT 数据再次复制：** 在 [getBufferedImage 的第 198–207 行](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L198-L207)，代码遍历所有 IDAT chunk，调用 `getBytes()`（又产生一次 clone），写入 `ByteArrayOutputStream`，最后 `baos.toByteArray()` 再分配一个合并后的数组：

```
chunk.bytes (PngChunk 内部)         →  第一份 (clone from readBytes)
getBytes() 返回                     →  第二份 (clone in getBytes)
ByteArrayOutputStream 内部缓冲区     →  第三份 (逐 chunk 累加)
baos.toByteArray() 结果            →  第四份 (最终合并)
```

在峰值时刻，**同一份 IDAT 数据在堆上存在 2–3 个完整副本**。

**(d) filterChunks 额外分配：** [filterChunks 方法](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L141-L151) 每次调用都创建一个新的 `ArrayList`。在 `getBufferedImage` 中，该方法被调用了 **8 次**（分别过滤 IHDR、PLTE、IDAT、tRNS、sRGB、gAMA、iCCP 等），每次均创建新的 List 对象并持有对原始 chunk 的引用。这不仅产生多个短生命周期对象，也使得原本可以通过索引结构（如 Map<ChunkType, List<PngChunk>>）高效实现的查找退化为多次 O(n) 线性扫描。

---

## 三、优化方案：基于 SoftReference 的最近访问 chunk 数据缓存

### 3.1 设计思路

核心思想是**不在 `readChunks` 中将所有 chunk 数据一次性全量加载到 List 中**，而是：

1. **第一遍扫描**（轻量级）：仅记录每个 chunk 的**元信息**（chunkType、length、crc、以及数据在 ByteSource 中的偏移量），存入列表。
2. **按需加载**：当上层代码需要访问某个具体 chunk 的数据时，通过 `SoftReference` 缓存按需从底层流中读取并解析。
3. **GC 友好**：`SoftReference` 允许 JVM 在内存紧张时自动回收缓存数据，避免 OOM。

### 3.2 伪代码实现

```java
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量级 Chunk 元信息，仅记录偏移量而不持有数据。
 */
class ChunkMetadata {
    final int chunkType;
    final int length;
    final int crc;
    final long dataOffset;   // 数据在原始流中的起始位置

    ChunkMetadata(int chunkType, int length, int crc, long dataOffset) {
        this.chunkType = chunkType;
        this.length = length;
        this.crc = crc;
        this.dataOffset = dataOffset;
    }
}

/**
 * 基于 SoftReference 的 Chunk 数据缓存。
 * 线程安全，不会抛出 ConcurrentModificationException。
 */
class ChunkCache {
    // key = chunk 在列表中的逻辑索引（dataOffset 也可作为唯一键）
    // value = SoftReference<PngChunk>，允许 GC 在内存紧张时回收
    private final ConcurrentHashMap<Integer, SoftReference<PngChunk>> cache
        = new ConcurrentHashMap<>();

    /**
     * 获取或加载指定索引的 chunk。
     * 如果缓存命中且 SoftReference 未被 GC 回收，直接返回；
     * 否则从底层流中重新读取并放入缓存。
     */
    PngChunk getOrLoad(int index, ChunkMetadata meta, SeekableByteSource source)
            throws IOException {
        // [1] 读取缓存条目
        SoftReference<PngChunk> ref = cache.get(index);
        PngChunk chunk = (ref != null) ? ref.get() : null;

        if (chunk != null) {
            return chunk;   // 缓存命中
        }

        // [2] 缓存未命中：从流中按需读取
        synchronized (source) {             // 对底层流加锁
            // 双重检查：可能在等待锁期间被其他线程填充
            ref = cache.get(index);
            chunk = (ref != null) ? ref.get() : null;
            if (chunk != null) {
                return chunk;
            }

            byte[] data = source.readRange(meta.dataOffset, meta.length);
            chunk = ChunkType.makeChunk(meta.length, meta.chunkType, meta.crc, data);

            // [3] 以 SoftReference 放入缓存
            cache.put(index, new SoftReference<>(chunk));
            return chunk;
        }
    }

    /**
     * 显式释放指定 chunk 的缓存条目（可选优化）。
     */
    void evict(int index) {
        cache.remove(index);
    }

    /**
     * 建议 JVM 清理（由 GC 自动触发，无需手动调用）。
     */
    void clear() {
        cache.clear();
    }
}

/**
 * 优化后的 readChunks 方法。
 * 第一遍仅收集元信息（偏移量），不加载数据。
 */
List<ChunkMetadata> readChunksLightweight(InputStream is, ChunkType[] chunkTypes)
        throws IOException {
    List<ChunkMetadata> result = new ArrayList<>();
    while (true) {
        int length = BinaryFunctions.read4Bytes("Length", is, "...", getByteOrder());
        int chunkType = BinaryFunctions.read4Bytes("ChunkType", is, "...", getByteOrder());
        boolean keep = keepChunk(chunkType, chunkTypes);

        if (keep) {
            // 记录当前流位置（数据即将被读取的起点）
            // 注意：需要底层流支持 mark/reset，或使用支持随机访问的 ByteSource
            long dataOffset = getStreamPosition(is);   // 需要记录位置
            int crc;    // CRC 需要在读取数据后获取，此处略去细节
            result.add(new ChunkMetadata(chunkType, length, crc, dataOffset));
        }

        // 跳过数据部分（不加载到内存）
        BinaryFunctions.skipBytes(is, keep ? length + 4 : length + 4, "...");

        if (chunkType == ChunkType.IEND.value) break;
    }
    return result;
}

// ----------- 使用示例 -----------

ChunkCache cache = new ChunkCache();
List<ChunkMetadata> metas = readChunksLightweight(inputStream, requestedTypes);

// 上层按需访问，仅在实际需要时才加载数据
PngChunkIhdr ihdr = (PngChunkIhdr) cache.getOrLoad(ihdrIndex, metas.get(ihdrIndex), source);
int width = ihdr.getWidth();

// 访问完成后可主动清理，也可依赖 GC 自动回收
cache.clear();
```

---

## 四、为什么该方案在多线程环境下不会出现 ConcurrentModificationException

### 4.1 ConcurrentModificationException 的触发条件

`ConcurrentModificationException` 是 **Java 集合框架的 fail-fast 迭代器机制**抛出的异常。其触发条件为：

> 在一个线程通过 **Iterator**（或增强 for 循环、`forEach`、`stream`）遍历集合的过程中，另一个线程（或同一线程）**结构性修改**了该集合（add / remove）。

典型的触发场景：

```java
List<String> list = new ArrayList<>();
list.add("a");
for (String s : list) {           // 隐式创建 Iterator
    list.add("b");                // 结构性修改 → ConcurrentModificationException
}
```

关键要素：**Iterator 遍历** + **结构性修改**。

### 4.2 本方案为何安全

本缓存方案使用 `ConcurrentHashMap`，且**仅使用点操作（point operations）**，从以下三个层面保证安全：

**(a) ConcurrentHashMap 的 `get()` / `put()` / `remove()` 不创建 Iterator**

`ConcurrentHashMap` 的单个键值操作（`get`、`put`、`remove`、`computeIfAbsent` 等）在内部实现中不依赖 fail-fast 迭代器。这些方法使用分段锁或 CAS 操作直接访问内部哈希桶，保证原子性而无需遍历整个集合。

**[ConcurrentHashMap Javadoc](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html) 明确说明：**

> "Retrieval operations (including `get`) generally do not block, and may overlap with update operations (including `put` and `remove`). … They do **not** throw `ConcurrentModificationException`."

**(b) 设计上不存在遍历操作**

本缓存方案的所有外部暴露方法都是基于**键的精确查找**：

| 方法 | 操作类型 | 是否使用 Iterator |
|------|---------|------------------|
| `getOrLoad(index, ...)` | `cache.get(index)` → `cache.put(index, ref)` | 否 |
| `evict(index)` | `cache.remove(index)` | 否 |
| `clear()` | `cache.clear()` | 否（ConcurrentHashMap.clear 是原子操作） |

没有任何方法通过 `Iterator`、增强 for 循环、`forEach`、`stream` 或 `keySet()`/`values()`/`entrySet()` 遍历整个缓存。因此，即便多个线程同时调用 `getOrLoad` 和 `evict`，也不会触发 fail-fast 检测。

**(c) SoftReference.get() 的清除操作由 GC 在安全点执行**

`SoftReference` 引用的清除发生在 JVM GC 的安全点（Safepoint），所有应用线程已暂停。当 `ref.get()` 返回 `null` 时，不影响 `ConcurrentHashMap` 的键值对结构——缓存中仍然存在该条目（key → 已清空的 SoftReference），只是需要通过 `getOrLoad` 重新加载。GC 不会结构性修改 `ConcurrentHashMap`。

**(d) 对底层流的同步保护**

伪代码中对 `source`（底层 SeekableByteSource / InputStream）使用了 `synchronized` 块，内嵌双重检查（DCL）。这保证了同一 chunk 不会在并发场景下被重复加载，同时避免了对流的并发读取冲突。这里的 `synchronized` 与 `ConcurrentHashMap` 的操作正交，互不干扰。

### 4.3 总结

| 风险 | 是否发生 | 原因 |
|------|---------|------|
| `ConcurrentModificationException` | **不会** | 无 Iterator 遍历，无结构性修改冲突 |
| 缓存击穿（多个线程重复加载同一 chunk） | **不会** | 双重检查锁定 + synchronized |
| 内存泄漏（缓存无限增长） | **不会** | SoftReference 允许 GC 自动回收 |
| 脏读（读到一个正在被修改的缓存条目） | **不会** | `cache.put` 是安全的发布（safe publication）；`SoftReference` 内部引用是 volatile 语义 |

---

## 五、总结

| # | 问题 | 核心行号 | 内存影响 |
|---|------|---------|---------|
| 1 | PngChunk 构造函数 `bytes.clone()` 导致双重复制 | [PngImageParser.java:L715](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L715) → [PngChunk.java:L52](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/chunks/PngChunk.java#L52) | 每 chunk 多 1× 内存 |
| 2 | 子类解析完成后仍保留父类 bytes 字段 | [PngChunkIhdr.java:L57](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/chunks/PngChunkIhdr.java#L57)、[PngChunkPlte.java:L48](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/chunks/PngChunkPlte.java#L48) | 元数据 chunk 的 bytes 成为死数据 |
| 3 | ArrayList 全量预载 + IDAT 多次复制 + filterChunks 低效 | [PngImageParser.java:L696](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L696)、[L169–171](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L169-L171)、[L198–207](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L198-L207)、[L141–151](file:///app/commons-imaging/src/main/java/org/apache/commons/imaging/formats/png/PngImageParser.java#L141-L151) | 大图场景下 IDAT 数据 2–4 倍内存占用 |

提出的 **SoftReference 缓存 + 按需加载** 方案可在保证线程安全的前提下，大幅降低 PNG 解析的峰值内存占用，且不会引入 `ConcurrentModificationException` 风险。