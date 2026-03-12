package com.sensors.smartshoeserver.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 传感器数据记录实体
 * 存储用户上传的传感器数据
 */
@Entity
@Table(name = "sensor_data_records", indexes = {
    @Index(name = "idx_sensor_user_id", columnList = "userId"),
    @Index(name = "idx_sensor_record_id", columnList = "recordId", unique = true),
    @Index(name = "idx_sensor_start_time", columnList = "startTime")
})
@EntityListeners(AuditingEntityListener.class)
public class SensorDataRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID（关联User实体）
     */
    @Column(nullable = false, length = 64)
    private String userId;

    /**
     * 记录ID（唯一标识）
     */
    @Column(nullable = false, unique = true, length = 64)
    private String recordId;

    /**
     * 记录开始时间
     */
    @Column(nullable = false)
    private Long startTime;

    /**
     * 记录结束时间
     */
    private Long endTime;

    /**
     * 数据点数量
     */
    @Column(nullable = false)
    private Integer dataCount;

    /**
     * 采样间隔（毫秒）
     */
    @Column(nullable = false, name = "sampling_interval")
    private Integer interval;

    /**
     * 压缩后的数据（Base64编码）
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String compressedData;

    /**
     * 原始数据大小（字节）
     */
    private Integer originalSize;

    /**
     * 压缩后数据大小（字节）
     */
    private Integer compressedSize;

    /**
     * 压缩率（如 0.65 表示压缩到原来的65%）
     */
    @Column(length = 10)
    private String compressionRatio;

    /**
     * 创建时间
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 构造函数
    public SensorDataRecord() {
    }

    public SensorDataRecord(String userId, String recordId, Long startTime, Long endTime,
                           Integer dataCount, Integer interval, String compressedData,
                           Integer originalSize, Integer compressedSize) {
        this.userId = userId;
        this.recordId = recordId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.dataCount = dataCount;
        this.interval = interval;
        this.compressedData = compressedData;
        this.originalSize = originalSize;
        this.compressedSize = compressedSize;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Integer getDataCount() {
        return dataCount;
    }

    public void setDataCount(Integer dataCount) {
        this.dataCount = dataCount;
    }

    public Integer getInterval() {
        return interval;
    }

    public void setInterval(Integer interval) {
        this.interval = interval;
    }

    public String getCompressedData() {
        return compressedData;
    }

    public void setCompressedData(String compressedData) {
        this.compressedData = compressedData;
    }

    public Integer getOriginalSize() {
        return originalSize;
    }

    public void setOriginalSize(Integer originalSize) {
        this.originalSize = originalSize;
    }

    public Integer getCompressedSize() {
        return compressedSize;
    }

    public void setCompressedSize(Integer compressedSize) {
        this.compressedSize = compressedSize;
    }

    public String getCompressionRatio() {
        return compressionRatio;
    }

    public void setCompressionRatio(String compressionRatio) {
        this.compressionRatio = compressionRatio;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}