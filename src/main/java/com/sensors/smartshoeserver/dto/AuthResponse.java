package com.sensors.smartshoeserver.dto;

/**
 * 认证响应DTO
 * 封装登录、注册等接口的响应数据
 */
public class AuthResponse {

    private boolean success;
    private String message;
    private UserData data;

    public AuthResponse() {
    }

    public AuthResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public AuthResponse(boolean success, String message, UserData data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UserData getData() {
        return data;
    }

    public void setData(UserData data) {
        this.data = data;
    }

    /**
     * 用户数据内部类
     */
    public static class UserData {
        private String userId;
        private String username;
        private String email;
        private String token;

        public UserData() {
        }

        public UserData(String userId, String username, String email, String token) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.token = token;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}