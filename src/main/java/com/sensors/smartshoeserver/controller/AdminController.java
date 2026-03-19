package com.sensors.smartshoeserver.controller;

import com.sensors.smartshoeserver.dto.SensorDataRecordDto;
import com.sensors.smartshoeserver.dto.UserDto;
import com.sensors.smartshoeserver.entity.SensorDataRecord;
import com.sensors.smartshoeserver.entity.User;
import com.sensors.smartshoeserver.repository.SensorDataRepository;
import com.sensors.smartshoeserver.repository.UserRepository;
import com.sensors.smartshoeserver.service.ColdDataCompressionService;
import com.sensors.smartshoeserver.service.SensorDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 管理员控制器
 * 提供用户管理、数据记录查看等功能
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final SensorDataRepository sensorDataRepository;
    private final SensorDataService sensorDataService;
    private final ColdDataCompressionService coldDataCompressionService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    public AdminController(UserRepository userRepository, SensorDataRepository sensorDataRepository,
                           SensorDataService sensorDataService, ColdDataCompressionService coldDataCompressionService) {
        this.userRepository = userRepository;
        this.sensorDataRepository = sensorDataRepository;
        this.sensorDataService = sensorDataService;
        this.coldDataCompressionService = coldDataCompressionService;
    }

    /**
     * 管理员首页
     */
    @GetMapping
    public String adminHome(Model model) {
        List<User> allUsers = userRepository.findAll();

        // 统计
        long totalUsers = allUsers.size();
        long activeUsers = allUsers.stream()
                .filter(u -> u.getStatus() == User.UserStatus.ACTIVE)
                .count();
        long totalRecords = sensorDataRepository.count();

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("inactiveUsers", totalUsers - activeUsers);
        model.addAttribute("totalRecords", totalRecords);

        // 最近5个用户
        List<UserDto> recentUsers = allUsers.stream()
                .filter(u -> u.getCreatedAt() != null)
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .limit(5)
                .map(this::convertToDto)
                .collect(Collectors.toList());

        model.addAttribute("recentUsers", recentUsers);
        return "admin/dashboard";
    }

    /**
     * 用户列表
     */
    @GetMapping("/users")
    public String userList(Model model) {
        List<User> allUsers = userRepository.findAll();
        List<UserDto> users = allUsers.stream()
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .map(this::convertToDto)
                .collect(Collectors.toList());

        long totalUsers = allUsers.size();
        long activeUsers = allUsers.stream()
                .filter(u -> u.getStatus() == User.UserStatus.ACTIVE)
                .count();
        long inactiveUsers = totalUsers - activeUsers;

        model.addAttribute("users", users);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("inactiveUsers", inactiveUsers);
        return "admin/users";
    }

    /**
     * 用户详情页面
     */
    @GetMapping("/users/{userId}")
    public String userDetail(@PathVariable String userId, Model model) {
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            return "redirect:/admin/users";
        }

        User user = userOpt.get();
        model.addAttribute("user", convertToDto(user));

        // 获取用户的数据记录
        List<SensorDataRecord> records = sensorDataRepository.findByUserIdOrderByCreatedAtDesc(userId);
        model.addAttribute("records", records.stream()
                .map(this::convertToRecordDto)
                .collect(Collectors.toList()));
        model.addAttribute("recordCount", records.size());

        return "admin/user-detail";
    }

    /**
     * 数据记录列表页面
     */
    @GetMapping("/records")
    public String recordList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SensorDataRecord> recordPage = sensorDataRepository.findAllByOrderByCreatedAtDesc(pageable);

        model.addAttribute("records", recordPage.getContent().stream()
                .map(this::convertToRecordDto)
                .collect(Collectors.toList()));
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", recordPage.getTotalPages());
        model.addAttribute("totalRecords", recordPage.getTotalElements());

        // 添加冷数据压缩统计
        ColdDataCompressionService.CompressionStats compressionStats = coldDataCompressionService.getStats();
        model.addAttribute("compressedCount", compressionStats.getTotalCompressed());
        model.addAttribute("spaceSaved", compressionStats.getTotalSpaceSaved());
        model.addAttribute("isCompressionRunning", compressionStats.isRunning());

        return "admin/records";
    }

    // ==================== API接口 ====================

    /**
     * API: 获取所有用户
     */
    @GetMapping("/api/users")
    @ResponseBody
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<UserDto> users = userRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * API: 获取用户详情
     */
    @GetMapping("/api/users/{userId}")
    @ResponseBody
    public ResponseEntity<?> getUserDetail(@PathVariable String userId) {
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用户不存在"));
        }
        return ResponseEntity.ok(convertToDto(userOpt.get()));
    }

    /**
     * API: 更新用户信息
     */
    @PostMapping("/api/users/{userId}/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String userId,
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam(required = false) String password) {
        Map<String, Object> result = new HashMap<>();
        Optional<User> userOpt = userRepository.findByUserId(userId);

        if (userOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(result);
        }

        User user = userOpt.get();

        // 检查邮箱是否被其他用户使用
        if (!email.equals(user.getEmail())) {
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent() && !existingUser.get().getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "该邮箱已被其他用户使用");
                return ResponseEntity.badRequest().body(result);
            }
        }

        user.setUsername(username);
        user.setEmail(email);
        if (password != null && !password.isEmpty()) {
            user.setPassword(password);
        }
        userRepository.save(user);

        result.put("success", true);
        result.put("message", "用户信息已更新");
        return ResponseEntity.ok(result);
    }

    /**
     * API: 删除用户
     */
    @PostMapping("/api/users/{userId}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String userId) {
        Map<String, Object> result = new HashMap<>();
        Optional<User> userOpt = userRepository.findByUserId(userId);

        if (userOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(result);
        }

        // 删除用户及其所有数据记录
        User user = userOpt.get();
        List<SensorDataRecord> records = sensorDataRepository.findByUserId(userId);
        sensorDataRepository.deleteAll(records);
        userRepository.delete(user);

        result.put("success", true);
        result.put("message", "用户及其数据已删除");
        return ResponseEntity.ok(result);
    }

    /**
     * API: 更新状态
     */
    @PostMapping("/api/users/{userId}/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String userId,
            @RequestParam String status) {
        Map<String, Object> result = new HashMap<>();
        Optional<User> userOpt = userRepository.findByUserId(userId);

        if (userOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            User user = userOpt.get();
            user.setStatus(User.UserStatus.valueOf(status.toUpperCase()));
            userRepository.save(user);
            result.put("success", true);
            result.put("message", "状态已更新");
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", "无效状态");
        }

        return ResponseEntity.ok(result);
    }

    /**
     * API: 重置密码
     */
    @PostMapping("/api/users/{userId}/reset-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetPassword(
            @PathVariable String userId,
            @RequestParam String newPassword) {
        Map<String, Object> result = new HashMap<>();
        Optional<User> userOpt = userRepository.findByUserId(userId);

        if (userOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(result);
        }

        User user = userOpt.get();
        user.setPassword(newPassword);
        userRepository.save(user);
        result.put("success", true);
        result.put("message", "密码已重置");
        return ResponseEntity.ok(result);
    }

    /**
     * API: 获取统计
     */
    @GetMapping("/api/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Long>> getStats() {
        List<User> allUsers = userRepository.findAll();
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalUsers", (long) allUsers.size());
        stats.put("activeUsers", allUsers.stream().filter(u -> u.getStatus() == User.UserStatus.ACTIVE).count());
        stats.put("inactiveUsers", (long) allUsers.size() - stats.get("activeUsers"));
        stats.put("totalRecords", sensorDataRepository.count());
        return ResponseEntity.ok(stats);
    }

    /**
     * API: 获取用户的数据记录
     */
    @GetMapping("/api/users/{userId}/records")
    @ResponseBody
    public ResponseEntity<?> getUserRecords(@PathVariable String userId) {
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用户不存在"));
        }

        List<SensorDataRecordDto> records = sensorDataRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::convertToRecordDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(records);
    }

    /**
     * 记录详情页面
     */
    @GetMapping("/records/{recordId}")
    public String recordDetail(@PathVariable String recordId, Model model) {
        Optional<SensorDataRecord> recordOpt = sensorDataRepository.findByRecordId(recordId);
        if (recordOpt.isEmpty()) {
            return "redirect:/admin/records";
        }

        SensorDataRecord record = recordOpt.get();
        model.addAttribute("record", convertToRecordDto(record));

        // 获取用户信息
        Optional<User> userOpt = userRepository.findByUserId(record.getUserId());
        model.addAttribute("user", userOpt.map(this::convertToDto).orElse(null));

        return "admin/record-detail";
    }

    /**
     * API: 删除数据记录
     */
    @PostMapping("/api/records/{recordId}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteRecord(@PathVariable String recordId) {
        Map<String, Object> result = new HashMap<>();
        Optional<SensorDataRecord> recordOpt = sensorDataRepository.findByRecordId(recordId);

        if (recordOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "记录不存在");
            return ResponseEntity.badRequest().body(result);
        }

        sensorDataRepository.delete(recordOpt.get());
        result.put("success", true);
        result.put("message", "记录已删除");
        return ResponseEntity.ok(result);
    }

    /**
     * 数据分析图表页面
     */
    @GetMapping("/charts")
    public String chartsPage() {
        return "admin/charts";
    }

    // ==================== 图表数据API（优化版） ====================

    /**
     * API: 获取活动趋势数据（优化版 - 使用数据库聚合查询）
     */
    @GetMapping("/api/stats/activity")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getActivityStats(@RequestParam(defaultValue = "7") int days) {
        Map<String, Object> result = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();

        LocalDate today = LocalDate.now();
        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MM-dd");

        LocalDateTime start = today.minusDays(days - 1).atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        // 使用优化的数据库查询
        List<Object[]> stats = sensorDataRepository.countByDateBetween(start, end);
        Map<LocalDate, Long> countMap = new HashMap<>();
        for (Object[] row : stats) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                countMap.put(((java.sql.Date) row[0]).toLocalDate(), ((Number) row[1]).longValue());
            }
        }

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            labels.add(date.format(labelFormatter));
            values.add(countMap.getOrDefault(date, 0L));
        }

        result.put("labels", labels);
        result.put("values", values);
        return ResponseEntity.ok(result);
    }

    /**
     * API: 获取用户活跃度数据（优化版）
     */
    @GetMapping("/api/stats/user-activity")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserActivityStats(@RequestParam(defaultValue = "7") int days) {
        Map<String, Object> result = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();

        LocalDate today = LocalDate.now();
        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MM-dd");

        LocalDateTime start = today.minusDays(days - 1).atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        // 使用优化的数据库查询
        List<Object[]> stats = sensorDataRepository.countDistinctUserByDateBetween(start, end);
        Map<LocalDate, Long> countMap = new HashMap<>();
        for (Object[] row : stats) {
            countMap.put(((java.sql.Date) row[0]).toLocalDate(), ((Number) row[1]).longValue());
        }

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            labels.add(date.format(labelFormatter));
            values.add(countMap.getOrDefault(date, 0L));
        }

        result.put("labels", labels);
        result.put("values", values);
        return ResponseEntity.ok(result);
    }

    /**
     * API: 获取存储统计（优化版）
     * 统计整体存储分布：未压缩数据 + 已压缩数据（按压缩后大小计算）
     */
    @GetMapping("/api/stats/storage")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStorageStats() {
        Map<String, Object> result = new HashMap<>();

        // 未压缩数据的大小（使用 originalSize）
        long uncompressedSize = sensorDataRepository.sumUncompressedSize();
        // 已压缩数据的大小（使用 compressedSize，即压缩后实际占用空间）
        long compressedSize = sensorDataRepository.sumCompressedSize();
        // 已压缩数据的原始大小（用于计算节省空间）
        long originalSizeOfCompressed = sensorDataRepository.sumOriginalSizeOfCompressedRecords();

        result.put("uncompressedSize", uncompressedSize);
        result.put("compressedSize", compressedSize);
        result.put("originalSizeOfCompressed", originalSizeOfCompressed);
        result.put("savedSize", originalSizeOfCompressed - compressedSize);
        return ResponseEntity.ok(result);
    }

    /**
     * API: 获取压缩效率统计（优化版）
     */
    @GetMapping("/api/stats/compression")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCompressionStats() {
        Map<String, Object> result = new HashMap<>();
        int lessThan50 = 0, range50to60 = 0, range60to70 = 0, range70to80 = 0, greaterThan80 = 0;

        // 只查询必要字段，不加载全部数据
        for (SensorDataRecord record : sensorDataRepository.findAll()) {
            if (record.getOriginalSize() == null || record.getOriginalSize() == 0) continue;
            if (record.getCompressedSize() == null) continue;  // 添加空值检查

            double ratio = 1 - (double) record.getCompressedSize() / record.getOriginalSize();
            double percentage = ratio * 100;

            if (percentage < 50) lessThan50++;
            else if (percentage < 60) range50to60++;
            else if (percentage < 70) range60to70++;
            else if (percentage < 80) range70to80++;
            else greaterThan80++;
        }

        result.put("lessThan50", lessThan50);
        result.put("range50to60", range50to60);
        result.put("range60to70", range60to70);
        result.put("range70to80", range70to80);
        result.put("greaterThan80", greaterThan80);
        return ResponseEntity.ok(result);
    }

    /**
     * API: 获取活跃用户排行（优化版）
     */
    @GetMapping("/api/stats/top-users")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getTopUsers(@RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> topUsers = new ArrayList<>();

        // 使用原生SQL查询
        List<Object[]> stats = sensorDataRepository.findTopUsersByRecordCount(limit);

        for (Object[] row : stats) {
            String userId = (String) row[0];
            long recordCount = ((Number) row[1]).longValue();
            long dataPoints = ((Number) row[2]).longValue();
            long totalSize = ((Number) row[3]).longValue();

            Optional<User> userOpt = userRepository.findByUserId(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, Object> userData = new HashMap<>();
                userData.put("userId", userId);
                userData.put("username", user.getUsername());
                userData.put("recordCount", recordCount);
                userData.put("dataPoints", dataPoints);
                userData.put("totalSize", totalSize);
                topUsers.add(userData);
            }
        }

        return ResponseEntity.ok(topUsers);
    }

    /**
     * API: 获取小时分布数据
     */
    @GetMapping("/api/stats/hourly")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getHourlyDistribution() {
        Map<String, Object> result = new HashMap<>();
        int[] hourlyCounts = new int[24];

        sensorDataRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null)
                .forEach(r -> {
                    int hour = r.getCreatedAt().getHour();
                    hourlyCounts[hour]++;
                });

        result.put("values", Arrays.stream(hourlyCounts).boxed().collect(Collectors.toList()));
        return ResponseEntity.ok(result);
    }

    /**
     * API: 获取用户上传统计
     */
    @GetMapping("/api/users/{userId}/upload-stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserUploadStats(@PathVariable String userId) {
        Map<String, Object> result = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();

        LocalDate today = LocalDate.now();
        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MM-dd");

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            labels.add(date.format(labelFormatter));

            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

            long count = sensorDataRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .filter(r -> r.getCreatedAt() != null)
                    .filter(r -> r.getCreatedAt().isAfter(startOfDay) && r.getCreatedAt().isBefore(endOfDay))
                    .count();
            values.add(count);
        }

        result.put("labels", labels);
        result.put("values", values);
        return ResponseEntity.ok(result);
    }

    /**
     * API: 获取记录详细数据（用于图表）
     * 复用 SensorDataService 的方法，自动处理冷数据解压并重置为热数据
     */
    @GetMapping("/api/records/{recordId}/data")
    @ResponseBody
    public ResponseEntity<?> getRecordData(@PathVariable String recordId) {
        // 复用 SensorDataService 的方法，自动处理冷数据解压
        // 注意：Admin 后台不需要 userId 验证，传入 null 表示跳过用户验证
        SensorDataService.SensorDataDetailResult result = sensorDataService.getRecordDetailForAdmin(recordId);

        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", result.getMessage()));
        }

        try {
            List<List<Integer>> rawData = result.getData();
            SensorDataRecordDto record = result.getRecord();

            // 转换为前端需要的格式
            List<Map<String, Object>> data = new ArrayList<>();
            long baseTime = record.getStartTime() != null ? record.getStartTime() : System.currentTimeMillis();
            long interval = record.getInterval() != null ? record.getInterval() : 100L;

            for (int i = 0; i < rawData.size(); i++) {
                List<Integer> point = rawData.get(i);
                if (point != null && point.size() >= 3) {
                    Map<String, Object> dataPoint = new HashMap<>();
                    dataPoint.put("timestamp", baseTime + (i * interval));
                    dataPoint.put("sensor1", point.get(0));
                    dataPoint.put("sensor2", point.get(1));
                    dataPoint.put("sensor3", point.get(2));
                    data.add(dataPoint);
                }
            }

            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "数据解析失败: " + e.getMessage()));
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 转换User为DTO
     */
    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUserId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setStatus(user.getStatus() != null ? user.getStatus().name() : "UNKNOWN");
        dto.setCreatedAt(formatDate(user.getCreatedAt()));
        dto.setUpdatedAt(formatDate(user.getUpdatedAt()));
        dto.setLastLoginAt(user.getLastLoginAt() != null ? formatDate(user.getLastLoginAt()) : "从未登录");
        return dto;
    }

    /**
     * 转换SensorDataRecord为DTO
     */
    private SensorDataRecordDto convertToRecordDto(SensorDataRecord record) {
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
        // 只有当 compressedSize 不为 null 且大于 0 时才表示数据已压缩
        if (record.getCompressedSize() != null && record.getCompressedSize() > 0
                && record.getOriginalSize() != null && record.getOriginalSize() > 0) {
            // 使用数据库中存储的compressionRatio计算节省空间率，如果没有则实时计算
            double ratio = 0.0;
            boolean ratioCalculated = false;
            if (record.getCompressionRatio() != null && !record.getCompressionRatio().isEmpty()) {
                try {
                    // compressionRatio 存储的是 compressedSize/originalSize
                    double storedRatio = Double.parseDouble(record.getCompressionRatio());
                    // 转换为节省空间率: 1 - storedRatio
                    ratio = (1 - storedRatio) * 100;
                    ratioCalculated = true;
                } catch (NumberFormatException e) {
                    // 如果解析失败，直接使用存储的值
                    dto.setCompressionRatio(record.getCompressionRatio());
                }
            } else {
                // 实时计算节省空间率
                ratio = (1 - (double) record.getCompressedSize() / record.getOriginalSize()) * 100;
                ratioCalculated = true;
            }
            if (ratioCalculated) {
                dto.setCompressionRatio(String.format("%.0f%%", ratio));
            }
        } else {
            // 未压缩的数据
            dto.setCompressionRatio("未压缩");
        }
        dto.setCreatedAt(formatDate(record.getCreatedAt()));

        // 获取用户名
        Optional<User> userOpt = userRepository.findByUserId(record.getUserId());
        dto.setUsername(userOpt.map(User::getUsername).orElse(record.getUserId()));

        return dto;
    }

    private String formatDate(java.time.LocalDateTime date) {
        return date != null ? date.format(FORMATTER) : "";
    }

    // ==================== 冷数据压缩管理接口 ====================

    /**
     * 获取冷数据压缩服务统计信息
     */
    @GetMapping("/api/compression/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getColdCompressionStats() {
        ColdDataCompressionService.CompressionStats stats = coldDataCompressionService.getStats();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("totalCompressed", stats.getTotalCompressed());
        result.put("totalSpaceSaved", stats.getTotalSpaceSaved());
        result.put("isRunning", stats.isRunning());

        return ResponseEntity.ok(result);
    }

    /**
     * 手动触发冷数据压缩
     */
    @PostMapping("/api/compression/trigger")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> triggerCompression(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "50") int maxRecords) {

        ColdDataCompressionService.CompressionReport report = coldDataCompressionService.compressManually(days, maxRecords);

        Map<String, Object> result = new HashMap<>();
        result.put("success", report.isSuccess());
        result.put("message", report.getMessage());
        result.put("processedCount", report.getProcessedCount());
        result.put("compressedCount", report.getCompressedCount());
        result.put("spaceSaved", report.getSpaceSaved());

        return ResponseEntity.ok(result);
    }
}
