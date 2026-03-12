package com.sensors.smartshoeserver.dto;

import java.util.List;

/**
 * 传感器数据上传请求DTO
 */
public class SensorDataUploadRequest {

    private String recordId;
    private Long startTime;
    private Long endTime;
    private Integer interval;
    private List<List<Integer>> data; // [[sensor1, sensor2, sensor3], ...]
    private Boolean compressed;

    public SensorDataUploadRequest() {
    }

    // Getters and Setters
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

    public Integer getInterval() {
        return interval;
    }

    public void setInterval(Integer interval) {
        this.interval = interval;
    }

    public List<List<Integer>> getData() {
        return data;
    }

    public void setData(List<List<Integer>> data) {
        this.data = data;
    }

    public Boolean getCompressed() {
        return compressed;
    }

    public void setCompressed(Boolean compressed) {
        this.compressed = compressed;
    }
}