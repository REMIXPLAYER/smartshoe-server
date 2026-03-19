package com.sensors.smartshoeserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sensors.smartshoeserver.dto.SensorDataRecordDto;
import com.sensors.smartshoeserver.dto.SensorDataUploadRequest;
import com.sensors.smartshoeserver.entity.SensorDataRecord;
import com.sensors.smartshoeserver.entity.User;
import com.sensors.smartshoeserver.repository.SensorDataRepository;
import com.sensors.smartshoeserver.repository.UserRepository;
import com.sensors.smartshoeserver.util.CompressionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * 传感器数据服务层
 */
@Service
public class SensorDataService {

    private static final Logger logger = LoggerFactory.getLogger(SensorDataService.class);

    private final SensorDataRepository sensorDataRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    public SensorDataService(SensorDataRepository sensorDataRepository, UserRepository userRepository) {
        this.sensorDataRepository = sensorDataRepository;
        this.userRepository = userRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 上传传感器数据
     * @param userId 用户ID
     * @param request 上传请求
     * @return 上传结果
     */
    @Transactional
    public SensorDataUploadResult uploadData(String userId, SensorDataUploadRequest request) {
        try {
            // 验证用户
            Optional<User> userOpt = userRepository.findByUserId(userId);
            if (userOpt.isEmpty()) {
                return new SensorDataUploadResult(false, "用户不存在", null);
            }

            User user = userOpt.get();
            if (user.getStatus() == null || user.getStatus() != User.UserStatus.ACTIVE) {
                return new SensorDataUploadResult(false, "用户状态异常", null);
            }

            // 生成记录ID
            String recordId = request.getRecordId();
            if (recordId == null || recordId.isEmpty()) {
                recordId = generateRecordId();
            }

            // 检查记录ID是否已存在
            if (sensorDataRepository.findByRecordId(recordId).isPresent()) {
                return new SensorDataUploadResult(false, "记录ID已存在", null);
            }

            // 将数据转为JSON（不再压缩，直接存储JSON）
            String jsonData = objectMapper.writeValueAsString(request.getData());
            byte[] originalBytes = jsonData.getBytes(StandardCharsets.UTF_8);

            // 直接使用JSON字符串存储（去除压缩环节）
            String dataString = jsonData;

            // 创建记录
            SensorDataRecord record = new SensorDataRecord();
            record.setUserId(userId);
            record.setRecordId(recordId);
            record.setStartTime(request.getStartTime());
            record.setEndTime(request.getEndTime());
            record.setDataCount(request.getData().size());
            record.setInterval(request.getInterval());
            record.setCompressedData(dataString); // 存储原始JSON
            record.setOriginalSize(originalBytes.length);
            // 未压缩的数据，compressedSize 和 compressionRatio 保持为 null

            // 保存
            sensorDataRepository.save(record);

            // 构建结果
            UploadInfo info = new UploadInfo();
            info.setRecordId(recordId);
            info.setDataCount(request.getData().size());
            info.setOriginalSize(originalBytes.length);
            // 未压缩的数据，不设置 compressedSize 和 compressionRatio

            return new SensorDataUploadResult(true, "上传成功", info);

        } catch (JsonProcessingException e) {
            return new SensorDataUploadResult(false, "数据序列化失败: " + e.getMessage(), null);
        } catch (Exception e) {
            return new SensorDataUploadResult(false, "上传失败: " + e.getMessage(), null);
        }
    }

    /**
     * 获取用户的数据记录列表
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 记录列表
     */
    public Page<SensorDataRecordDto> getUserRecords(String userId, Pageable pageable) {
        Page<SensorDataRecord> records = sensorDataRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return records.map(this::convertToDto);
    }

    /**
     * 获取用户指定时间范围内的记录（不分页）
     * @param userId 用户ID
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 记录列表
     */
    public List<SensorDataRecordDto> getRecordsByTimeRange(String userId, Long startTime, Long endTime) {
        List<SensorDataRecord> records = sensorDataRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeDesc(
                userId, startTime, endTime);
        return records.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户指定时间范围内的记录（支持分页）
     * @param userId 用户ID
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @param pageable 分页参数
     * @return 分页记录
     */
    public Page<SensorDataRecordDto> getRecordsByTimeRange(String userId, Long startTime, Long endTime, Pageable pageable) {
        Page<SensorDataRecord> recordPage = sensorDataRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeDesc(
                userId, startTime, endTime, pageable);
        return recordPage.map(this::convertToDto);
    }

    /**
     * 获取单条记录详情
     * @param userId 用户ID
     * @param recordId 记录ID
     * @return 记录详情（包含解压后的数据）
     */
    public SensorDataDetailResult getRecordDetail(String userId, String recordId) {
        Optional<SensorDataRecord> recordOpt = sensorDataRepository.findByRecordIdAndUserId(recordId, userId);
        if (recordOpt.isEmpty()) {
            return new SensorDataDetailResult(false, "记录不存在", null, null);
        }

        // 复用核心处理逻辑（内部管理事务）
        return processRecordDetail(recordOpt.get(), recordId);
    }

    /**
     * 将冷数据重置为热数据（解压后保存）
     * @param record 记录实体
     * @param decompressedData 解压后的JSON数据
     */
    private void resetToHotData(SensorDataRecord record, String decompressedData) {
        try {
            // 检查是否已经是热数据（避免重复重置）
            String currentRatio = record.getCompressionRatio();
            if (currentRatio == null || currentRatio.isEmpty()) {
                logger.debug("记录 {} 已经是热数据，跳过重置", record.getRecordId());
                return;
            }
            
            record.setCompressedData(decompressedData);
            // 清除所有压缩相关字段，确保显示"未压缩"
            record.setCompressedSize(null);
            record.setCompressionRatio(null);
            // 将 originalSize 设置为解压后数据的大小
            record.setOriginalSize(decompressedData.getBytes(StandardCharsets.UTF_8).length);
            // 注意：由于此方法被 @Transactional 的 processRecordDetail 调用，
            // save 操作会在同一事务中执行，事务提交时自动刷新到数据库
            sensorDataRepository.save(record);
            logger.info("记录 {} 已从冷数据重置为热数据", record.getRecordId());
        } catch (Exception e) {
            logger.error("重置记录 {} 为热数据失败: {}", record.getRecordId(), e.getMessage(), e);
            // 抛出异常以确保事务回滚
            throw new RuntimeException("重置热数据失败", e);
        }
    }

    /**
     * 获取记录详情（Admin 后台使用，跳过用户验证）
     * 复用相同的解压逻辑，查看后自动重置为热数据
     * @param recordId 记录ID
     * @return 记录详情结果
     */
    public SensorDataDetailResult getRecordDetailForAdmin(String recordId) {
        Optional<SensorDataRecord> recordOpt = sensorDataRepository.findByRecordId(recordId);
        if (recordOpt.isEmpty()) {
            return new SensorDataDetailResult(false, "记录不存在", null, null);
        }
        // 复用核心处理逻辑（内部管理事务）
        return processRecordDetail(recordOpt.get(), recordId);
    }

    /**
     * 处理记录详情的核心逻辑（解压数据、解析、重置为热数据）
     * @param record 记录实体
     * @param recordId 记录ID（用于日志）
     * @return 记录详情结果
     */
    @Transactional
    public SensorDataDetailResult processRecordDetail(SensorDataRecord record, String recordId) {
        try {
            String jsonData = record.getCompressedData();

            // 添加空值检查
            if (jsonData == null || jsonData.isEmpty()) {
                return new SensorDataDetailResult(false, "数据为空", null, null);
            }

            // 判断数据是否已压缩（通过检查 compressionRatio 字段）
            // 注意：需要在任何修改之前读取该字段
            String compressionRatio = record.getCompressionRatio();
            boolean isCompressed = compressionRatio != null && !compressionRatio.isEmpty();
            
            if (isCompressed) {
                // 数据已压缩，需要解压
                try {
                    jsonData = CompressionUtils.decompress(jsonData);
                    logger.debug("记录 {} 已解压", recordId);
                } catch (Exception e) {
                    // 解压失败，可能是未压缩数据，记录警告并尝试直接解析
                    logger.warn("解压记录 {} 失败，尝试直接解析: {}", recordId, e.getMessage());
                }
            }

            // 解析数据
            List<List<Integer>> data = objectMapper.readValue(jsonData,
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Integer.class)));

            // 如果数据是压缩的，解压后重置为热数据（未压缩状态）
            // 使用之前读取的 isCompressed 变量，而不是重新读取 record.getCompressionRatio()
            if (isCompressed) {
                resetToHotData(record, jsonData);
            }

            return new SensorDataDetailResult(true, "获取成功", convertToDto(record), data);
        } catch (Exception e) {
            logger.error("获取记录 {} 详情失败: {}", recordId, e.getMessage(), e);
            return new SensorDataDetailResult(false, "数据解析失败: " + e.getMessage(), null, null);
        }
    }

    /**
     * 删除记录
     * @param userId 用户ID
     * @param recordId 记录ID
     * @return 删除结果
     */
    @Transactional
    public boolean deleteRecord(String userId, String recordId) {
        Optional<SensorDataRecord> recordOpt = sensorDataRepository.findByRecordIdAndUserId(recordId, userId);
        if (recordOpt.isPresent()) {
            sensorDataRepository.delete(recordOpt.get());
            return true;
        }
        return false;
    }

    /**
     * 生成记录ID
     */
    private String generateRecordId() {
        return "record_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 转换为DTO
     */
    private SensorDataRecordDto convertToDto(SensorDataRecord record) {
        SensorDataRecordDto dto = new SensorDataRecordDto();
        dto.setId(record.getId());
        dto.setUserId(record.getUserId());
        dto.setRecordId(record.getRecordId());
        dto.setStartTime(record.getStartTime());
        dto.setEndTime(record.getEndTime());
        dto.setDataCount(record.getDataCount());
        dto.setInterval(record.getInterval());
        dto.setOriginalSize(record.getOriginalSize());
        dto.setCompressedSize(record.getCompressedSize());
        // 使用存储的 compressionRatio，如果没有则计算
        if (record.getCompressionRatio() != null && !record.getCompressionRatio().isEmpty()) {
            // 将存储的小数格式转换为百分比显示（如 0.65 -> 65.0%）
            try {
                double ratio = Double.parseDouble(record.getCompressionRatio());
                double savingsPercentage = (1 - ratio) * 100;
                dto.setCompressionRatio(String.format("%.1f%%", savingsPercentage));
            } catch (NumberFormatException e) {
                dto.setCompressionRatio(record.getCompressionRatio());
            }
        } else if (record.getCompressedSize() != null && record.getCompressedSize() > 0
                && record.getOriginalSize() != null && record.getOriginalSize() > 0) {
            // 兼容旧数据：计算压缩率
            double ratio = (1 - (double) record.getCompressedSize() / record.getOriginalSize()) * 100;
            dto.setCompressionRatio(String.format("%.1f%%", ratio));
        } else {
            dto.setCompressionRatio("未压缩");
        }
        dto.setCreatedAt(formatDate(record.getCreatedAt()));
        return dto;
    }

    private String formatDate(LocalDateTime date) {
        return date != null ? date.format(FORMATTER) : "";
    }

    // 内部结果类
    public static class SensorDataUploadResult {
        private final boolean success;
        private final String message;
        private final UploadInfo info;

        public SensorDataUploadResult(boolean success, String message, UploadInfo info) {
            this.success = success;
            this.message = message;
            this.info = info;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public UploadInfo getInfo() { return info; }
    }

    public static class UploadInfo {
        private String recordId;
        private int dataCount;
        private int originalSize;
        private int compressedSize;
        private float compressionRatio;

        // Getters and Setters
        public String getRecordId() { return recordId; }
        public void setRecordId(String recordId) { this.recordId = recordId; }
        public int getDataCount() { return dataCount; }
        public void setDataCount(int dataCount) { this.dataCount = dataCount; }
        public int getOriginalSize() { return originalSize; }
        public void setOriginalSize(int originalSize) { this.originalSize = originalSize; }
        public int getCompressedSize() { return compressedSize; }
        public void setCompressedSize(int compressedSize) { this.compressedSize = compressedSize; }
        public float getCompressionRatio() { return compressionRatio; }
        public void setCompressionRatio(float compressionRatio) { this.compressionRatio = compressionRatio; }
    }

    public static class SensorDataDetailResult {
        private final boolean success;
        private final String message;
        private final SensorDataRecordDto record;
        private final List<List<Integer>> data;

        public SensorDataDetailResult(boolean success, String message, SensorDataRecordDto record, List<List<Integer>> data) {
            this.success = success;
            this.message = message;
            this.record = record;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public SensorDataRecordDto getRecord() { return record; }
        public List<List<Integer>> getData() { return data; }
    }
}