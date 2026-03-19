package com.sensors.smartshoeserver.repository;

import com.sensors.smartshoeserver.entity.SensorDataRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorDataRecord, Long> {

    Page<SensorDataRecord> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<SensorDataRecord> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<SensorDataRecord> findByRecordId(String recordId);

    Optional<SensorDataRecord> findByRecordIdAndUserId(String recordId, String userId);

    long countByUserId(String userId);

    void deleteByRecordId(String recordId);

    List<SensorDataRecord> findByUserId(String userId);

    Page<SensorDataRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<SensorDataRecord> findByUserIdAndStartTimeBetweenOrderByStartTimeDesc(
            String userId, Long startTime, Long endTime);

    // 支持分页的时间范围查询
    Page<SensorDataRecord> findByUserIdAndStartTimeBetweenOrderByStartTimeDesc(
            String userId, Long startTime, Long endTime, Pageable pageable);

    // 性能优化：按创建时间范围查询
    List<SensorDataRecord> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 性能优化：按创建时间范围统计
    @Query("SELECT COUNT(r) FROM SensorDataRecord r WHERE r.createdAt BETWEEN :start AND :end")
    long countByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 性能优化：按创建时间范围统计不同用户数
    @Query("SELECT COUNT(DISTINCT r.userId) FROM SensorDataRecord r WHERE r.createdAt BETWEEN :start AND :end")
    long countDistinctUserIdByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 性能优化：统计总原始大小
    @Query("SELECT COALESCE(SUM(r.originalSize), 0) FROM SensorDataRecord r")
    long sumOriginalSize();

    // 性能优化：统计总压缩大小（只统计已压缩的数据，即 compressedSize > 0 的记录）
    @Query("SELECT COALESCE(SUM(r.compressedSize), 0) FROM SensorDataRecord r WHERE r.compressedSize IS NOT NULL AND r.compressedSize > 0")
    long sumCompressedSize();

    // 性能优化：统计已压缩记录的原始大小（用于存储分布图表）
    @Query("SELECT COALESCE(SUM(r.originalSize), 0) FROM SensorDataRecord r WHERE r.compressedSize IS NOT NULL AND r.compressedSize > 0")
    long sumOriginalSizeOfCompressedRecords();

    // 性能优化：统计未压缩数据的大小（compressedSize 为 null 的记录）
    @Query("SELECT COALESCE(SUM(r.originalSize), 0) FROM SensorDataRecord r WHERE r.compressedSize IS NULL OR r.compressedSize = 0")
    long sumUncompressedSize();

    // 性能优化：按小时统计上传数量
    @Query("SELECT HOUR(r.createdAt) as hour, COUNT(r) as count FROM SensorDataRecord r WHERE r.createdAt IS NOT NULL GROUP BY HOUR(r.createdAt)")
    List<Object[]> countByHour();

    // 性能优化：获取活跃用户排行
    @Query(value = "SELECT r.user_id, COUNT(r.id) as cnt, COALESCE(SUM(r.data_count), 0) as data_pts, COALESCE(SUM(r.compressed_size), 0) as total_size FROM sensor_data_records r GROUP BY r.user_id ORDER BY cnt DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopUsersByRecordCount(@Param("limit") int limit);

    // 性能优化：按日期分组统计
    @Query("SELECT DATE(r.createdAt) as date, COUNT(r) as count FROM SensorDataRecord r WHERE r.createdAt BETWEEN :start AND :end GROUP BY DATE(r.createdAt) ORDER BY DATE(r.createdAt)")
    List<Object[]> countByDateBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 性能优化：按日期分组统计活跃用户
    @Query("SELECT DATE(r.createdAt) as date, COUNT(DISTINCT r.userId) as count FROM SensorDataRecord r WHERE r.createdAt BETWEEN :start AND :end GROUP BY DATE(r.createdAt) ORDER BY DATE(r.createdAt)")
    List<Object[]> countDistinctUserByDateBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 冷数据压缩：查询指定时间之前未压缩的记录
    // 通过检查compressionRatio是否为null或为空来判断是否已压缩
    @Query("SELECT r FROM SensorDataRecord r WHERE r.createdAt < :threshold AND (r.compressionRatio IS NULL OR r.compressionRatio = '') ORDER BY r.createdAt ASC")
    Page<SensorDataRecord> findUncompressedRecordsBefore(@Param("threshold") LocalDateTime threshold, Pageable pageable);

    // 统计已压缩记录数（compressedSize > 0 的记录）
    @Query("SELECT COUNT(r) FROM SensorDataRecord r WHERE r.compressedSize IS NOT NULL AND r.compressedSize > 0")
    long countCompressedRecords();
}