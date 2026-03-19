package com.sensors.smartshoeserver.service;

import com.sensors.smartshoeserver.entity.SensorDataRecord;
import com.sensors.smartshoeserver.repository.SensorDataRepository;
import com.sensors.smartshoeserver.util.CompressionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 冷数据压缩服务
 * 在系统空闲时自动压缩旧数据，节省存储空间
 * 优先级较低，不会挤占其他服务资源
 */
@Service
public class ColdDataCompressionService {

    private static final Logger logger = LoggerFactory.getLogger(ColdDataCompressionService.class);

    @Autowired
    private SensorDataRepository sensorDataRepository;

    // 配置项
    @Value("${cold.data.compression.enabled:true}")
    private boolean compressionEnabled;

    @Value("${cold.data.compression.days-threshold:7}")
    private int daysThreshold; // 多少天前的数据被视为冷数据

    @Value("${cold.data.compression.batch-size:10}")
    private int batchSize; // 每批处理记录数

    @Value("${cold.data.compression.max-records-per-run:100}")
    private int maxRecordsPerRun; // 每次运行最多处理记录数

    @Value("${cold.data.compression.min-compression-ratio:0.8}")
    private double minCompressionRatio; // 最小压缩率，低于此值不压缩

    // 运行状态
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger totalCompressed = new AtomicInteger(0);
    private final AtomicLong totalSpaceSaved = new AtomicLong(0);

    /**
     * 定时任务：每小时检查一次是否需要压缩
     * 使用 fixedDelay 确保任务不会重叠执行
     */
    @Scheduled(fixedDelay = 3600000) // 每小时执行一次
    public void scheduledCompression() {
        if (!compressionEnabled) {
            logger.debug("冷数据压缩已禁用");
            return;
        }

        // 检查系统负载，如果负载高则跳过
        if (isSystemBusy()) {
            logger.debug("系统负载较高，跳过本次压缩任务");
            return;
        }

        compressColdData();
    }

    /**
     * 手动触发压缩（用于管理后台）
     *
     * @param days 压缩多少天前的数据
     * @param maxRecords 最多处理多少条记录
     * @return 压缩结果报告
     */
    public CompressionReport compressManually(int days, int maxRecords) {
        if (isRunning.get()) {
            return new CompressionReport(false, "压缩任务正在运行中，请稍后重试", 0, 0, 0);
        }

        int originalDays = daysThreshold;
        int originalMax = maxRecordsPerRun;

        try {
            daysThreshold = days;
            maxRecordsPerRun = maxRecords;
            return compressColdData();
        } finally {
            daysThreshold = originalDays;
            maxRecordsPerRun = originalMax;
        }
    }

    /**
     * 压缩冷数据
     *
     * @return 压缩结果报告
     */
    public synchronized CompressionReport compressColdData() {
        if (!isRunning.compareAndSet(false, true)) {
            logger.warn("压缩任务已在运行中，跳过本次执行");
            return new CompressionReport(false, "任务正在运行中", 0, 0, 0);
        }

        logger.info("开始冷数据压缩任务...");
        long startTime = System.currentTimeMillis();

        int processedCount = 0;
        int compressedCount = 0;
        long spaceSaved = 0;
        int errorCount = 0;

        try {
            LocalDateTime threshold = LocalDateTime.now().minus(daysThreshold, ChronoUnit.DAYS);
            Pageable pageable = PageRequest.of(0, batchSize);

            while (processedCount < maxRecordsPerRun) {
                // 检查系统负载
                if (isSystemBusy()) {
                    logger.info("系统负载升高，暂停压缩任务");
                    break;
                }

                // 查询未压缩的冷数据
                Page<SensorDataRecord> records = sensorDataRepository.findUncompressedRecordsBefore(
                        threshold, pageable);

                if (records.isEmpty()) {
                    logger.info("没有更多需要压缩的冷数据");
                    break;
                }

                for (SensorDataRecord record : records.getContent()) {
                    if (processedCount >= maxRecordsPerRun) {
                        break;
                    }

                    try {
                        CompressionResult result = compressRecord(record);
                        processedCount++;

                        if (result.isSuccess()) {
                            compressedCount++;
                            spaceSaved += result.getSpaceSaved();
                        }

                        // 每处理10条记录休眠一下，让出CPU
                        if (processedCount % 10 == 0) {
                            Thread.sleep(100);
                        }
                    } catch (Exception e) {
                        logger.error("压缩记录 {} 失败: {}", record.getRecordId(), e.getMessage());
                        errorCount++;
                    }
                }

                if (!records.hasNext()) {
                    break;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("冷数据压缩完成: 处理{}条, 成功{}条, 失败{}条, 节省{}字节, 耗时{}ms",
                    processedCount, compressedCount, errorCount, spaceSaved, duration);

            // 更新统计
            totalCompressed.addAndGet(compressedCount);
            totalSpaceSaved.addAndGet(spaceSaved);

            return new CompressionReport(true,
                    String.format("压缩完成: %d条成功, %d条失败", compressedCount, errorCount),
                    processedCount, compressedCount, spaceSaved);

        } catch (Exception e) {
            logger.error("冷数据压缩任务异常", e);
            return new CompressionReport(false, "压缩任务异常: " + e.getMessage(),
                    processedCount, compressedCount, spaceSaved);
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * 压缩单条记录
     *
     * @param record 数据记录
     * @return 压缩结果
     */
    private CompressionResult compressRecord(SensorDataRecord record) throws IOException {
        String originalData = record.getCompressedData();

        // 添加空值检查
        if (originalData == null || originalData.isEmpty()) {
            logger.debug("记录 {} 数据为空，跳过", record.getRecordId());
            return new CompressionResult(false, 0, "数据为空");
        }

        // 检查是否已压缩（简单判断：如果看起来像Base64且长度较短，可能已压缩）
        if (CompressionUtils.isCompressed(originalData)) {
            logger.debug("记录 {} 可能已压缩，跳过", record.getRecordId());
            return new CompressionResult(false, 0, "数据可能已压缩");
        }

        int originalSize = originalData.length();

        // 压缩数据
        String compressedData = CompressionUtils.compress(originalData);
        int compressedSize = compressedData.length();

        // 计算压缩率
        double ratio = CompressionUtils.calculateCompressionRatio(originalSize, compressedSize);

        // 如果压缩效果不佳，不保存
        if (ratio > minCompressionRatio) {
            logger.debug("记录 {} 压缩率不佳({}%), 跳过", record.getRecordId(), String.format("%.1f", ratio * 100));
            return new CompressionResult(false, 0, "压缩率不佳: " + String.format("%.1f%%", ratio * 100));
        }

        // 保存压缩后的数据
        record.setCompressedData(compressedData);
        record.setCompressedSize(compressedSize);
        record.setOriginalSize(originalSize);
        // compressionRatio 是 compressedSize / originalSize
        record.setCompressionRatio(String.format("%.2f", ratio));

        sensorDataRepository.save(record);

        long spaceSaved = originalSize - compressedSize;
        logger.debug("记录 {} 压缩成功: {} → {} 字节, 节省 {} 字节",
                record.getRecordId(), originalSize, compressedSize, spaceSaved);

        return new CompressionResult(true, spaceSaved, "压缩成功");
    }

    /**
     * 检查系统是否繁忙
     * 简单的启发式判断：检查CPU使用率（如果可用）
     *
     * @return 是否繁忙
     */
    private boolean isSystemBusy() {
        // 获取系统CPU负载
        double loadAverage = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();

        // 负载不可用或系统负载检查失败时返回 false
        if (loadAverage < 0) {
            return false;
        }

        int processors = Runtime.getRuntime().availableProcessors();
        if (processors <= 0) {
            return false;
        }

        return loadAverage > processors * 0.7; // 70% 负载阈值
    }

    /**
     * 获取压缩统计信息
     * 从数据库实时查询，确保统计信息准确且持久化
     * @return 统计信息
     */
    public CompressionStats getStats() {
        // 从数据库实时统计已压缩记录数
        long compressedCount = sensorDataRepository.countCompressedRecords();
        
        // 计算节省空间 = 已压缩数据的原始大小 - 已压缩数据的压缩后大小
        long originalSizeOfCompressed = sensorDataRepository.sumOriginalSizeOfCompressedRecords();
        long compressedSizeTotal = sensorDataRepository.sumCompressedSize();
        long spaceSaved = originalSizeOfCompressed - compressedSizeTotal;
        
        return new CompressionStats(
                (int) compressedCount,
                spaceSaved,
                isRunning.get()
        );
    }

    // ========== 内部类 ==========

    /**
     * 压缩结果
     */
    public static class CompressionResult {
        private final boolean success;
        private final long spaceSaved;
        private final String message;

        public CompressionResult(boolean success, long spaceSaved, String message) {
            this.success = success;
            this.spaceSaved = spaceSaved;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public long getSpaceSaved() { return spaceSaved; }
        public String getMessage() { return message; }
    }

    /**
     * 压缩报告
     */
    public static class CompressionReport {
        private final boolean success;
        private final String message;
        private final int processedCount;
        private final int compressedCount;
        private final long spaceSaved;

        public CompressionReport(boolean success, String message, int processedCount,
                                 int compressedCount, long spaceSaved) {
            this.success = success;
            this.message = message;
            this.processedCount = processedCount;
            this.compressedCount = compressedCount;
            this.spaceSaved = spaceSaved;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getProcessedCount() { return processedCount; }
        public int getCompressedCount() { return compressedCount; }
        public long getSpaceSaved() { return spaceSaved; }
    }

    /**
     * 压缩统计
     */
    public static class CompressionStats {
        private final int totalCompressed;
        private final long totalSpaceSaved;
        private final boolean isRunning;

        public CompressionStats(int totalCompressed, long totalSpaceSaved, boolean isRunning) {
            this.totalCompressed = totalCompressed;
            this.totalSpaceSaved = totalSpaceSaved;
            this.isRunning = isRunning;
        }

        // Getters
        public int getTotalCompressed() { return totalCompressed; }
        public long getTotalSpaceSaved() { return totalSpaceSaved; }
        public boolean isRunning() { return isRunning; }
    }
}
