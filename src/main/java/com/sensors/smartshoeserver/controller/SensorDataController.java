package com.sensors.smartshoeserver.controller;

import com.sensors.smartshoeserver.dto.SensorDataRecordDto;
import com.sensors.smartshoeserver.dto.SensorDataUploadRequest;
import com.sensors.smartshoeserver.service.AuthService;
import com.sensors.smartshoeserver.service.SensorDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 传感器数据控制器
 * 处理传感器数据的上传、查询、删除等操作
 * 所有接口需要登录验证（通过Token）
 */
@RestController
@RequestMapping("/api/sensor")
@CrossOrigin(origins = "*")
public class SensorDataController {

    private final SensorDataService sensorDataService;
    private final AuthService authService;

    @Autowired
    public SensorDataController(SensorDataService sensorDataService, AuthService authService) {
        this.sensorDataService = sensorDataService;
        this.authService = authService;
    }

    /**
     * 上传传感器数据
     * @param token 用户Token（从请求头获取）
     * @param request 上传请求
     * @return 上传结果
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadData(
            @RequestHeader("Authorization") String token,
            @RequestBody SensorDataUploadRequest request) {
        Map<String, Object> response = new HashMap<>();

        // 验证Token并获取用户ID
        String userId = extractUserIdFromToken(token);
        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录或Token无效");
            return ResponseEntity.status(401).body(response);
        }

        // 参数校验
        if (request.getData() == null || request.getData().isEmpty()) {
            response.put("success", false);
            response.put("message", "数据不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        if (request.getStartTime() == null) {
            response.put("success", false);
            response.put("message", "开始时间不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        if (request.getInterval() == null || request.getInterval() <= 0) {
            response.put("success", false);
            response.put("message", "采样间隔必须大于0");
            return ResponseEntity.badRequest().body(response);
        }

        // 上传数据
        SensorDataService.SensorDataUploadResult result = sensorDataService.uploadData(userId, request);

        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        if (result.getInfo() != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("recordId", result.getInfo().getRecordId());
            data.put("dataCount", result.getInfo().getDataCount());
            data.put("originalSize", result.getInfo().getOriginalSize());
            data.put("compressedSize", result.getInfo().getCompressedSize());
            data.put("compressionRatio", result.getInfo().getCompressionRatio());
            response.put("data", data);
        }

        if (result.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取用户的数据记录列表
     * @param token 用户Token
     * @param page 页码（默认0）
     * @param size 每页大小（默认20）
     * @return 记录列表
     */
    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> getUserRecords(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> response = new HashMap<>();

        // 验证Token
        String userId = extractUserIdFromToken(token);
        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录或Token无效");
            return ResponseEntity.status(401).body(response);
        }

        // 分页查询
        Pageable pageable = PageRequest.of(page, size);
        Page<SensorDataRecordDto> records = sensorDataService.getUserRecords(userId, pageable);

        response.put("success", true);
        response.put("data", records.getContent());
        response.put("total", records.getTotalElements());
        response.put("page", records.getNumber());
        response.put("size", records.getSize());
        response.put("totalPages", records.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * 按时间范围查询记录
     * @param token 用户Token
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 记录列表
     */
    @GetMapping("/records/range")
    public ResponseEntity<Map<String, Object>> getRecordsByTimeRange(
            @RequestHeader("Authorization") String token,
            @RequestParam Long startTime,
            @RequestParam Long endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> response = new HashMap<>();

        // 验证Token
        String userId = extractUserIdFromToken(token);

        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录或Token无效");
            return ResponseEntity.status(401).body(response);
        }

        // 参数校验
        if (startTime == null || endTime == null) {
            response.put("success", false);
            response.put("message", "开始时间和结束时间不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        if (startTime >= endTime) {
            response.put("success", false);
            response.put("message", "开始时间必须小于结束时间");
            return ResponseEntity.badRequest().body(response);
        }

        // 查询数据（支持分页）
        Pageable pageable = PageRequest.of(page, size);
        Page<SensorDataRecordDto> recordPage = sensorDataService.getRecordsByTimeRange(userId, startTime, endTime, pageable);

        response.put("success", true);
        response.put("data", recordPage.getContent());
        response.put("total", recordPage.getTotalElements());  // 使用total字段，与App端期望一致
        response.put("page", recordPage.getNumber());
        response.put("size", recordPage.getSize());
        response.put("totalPages", recordPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * 获取单条记录详情
     * @param token 用户Token
     * @param recordId 记录ID
     * @return 记录详情
     */
    @GetMapping("/records/{recordId}/detail")
    public ResponseEntity<Map<String, Object>> getRecordDetail(
            @RequestHeader("Authorization") String token,
            @PathVariable String recordId) {
        Map<String, Object> response = new HashMap<>();

        // 验证Token
        String userId = extractUserIdFromToken(token);
        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录或Token无效");
            return ResponseEntity.status(401).body(response);
        }

        // 获取详情
        SensorDataService.SensorDataDetailResult result = sensorDataService.getRecordDetail(userId, recordId);

        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        if (result.isSuccess()) {
            response.put("record", result.getRecord());
            response.put("data", result.getData());
        }

        if (result.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 删除记录
     * @param token 用户Token
     * @param recordId 记录ID
     * @return 删除结果
     */
    @DeleteMapping("/record/{recordId}")
    public ResponseEntity<Map<String, Object>> deleteRecord(
            @RequestHeader("Authorization") String token,
            @PathVariable String recordId) {
        Map<String, Object> response = new HashMap<>();

        // 验证Token
        String userId = extractUserIdFromToken(token);
        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录或Token无效");
            return ResponseEntity.status(401).body(response);
        }

        // 删除记录
        boolean deleted = sensorDataService.deleteRecord(userId, recordId);

        if (deleted) {
            response.put("success", true);
            response.put("message", "删除成功");
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "记录不存在或无权删除");
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 从Token中提取用户ID
     * @param authHeader Authorization请求头
     * @return 用户ID，无效返回null
     */
    private String extractUserIdFromToken(String authHeader) {
        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }

        // 支持 "Bearer token" 格式
        String token = authHeader;
        if (authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 验证Token
        var result = authService.verifyToken(token);

        if (result.isSuccess()) {
            return result.getData().getUserId();
        }
        return null;
    }
}