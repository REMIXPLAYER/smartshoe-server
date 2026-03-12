package com.sensors.smartshoeserver.repository;

import com.sensors.smartshoeserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层
 * 提供用户数据的CRUD操作
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据邮箱查找用户
     * @param email 邮箱
     * @return 用户对象（可能为空）
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据用户ID查找用户
     * @param userId 用户ID
     * @return 用户对象（可能为空）
     */
    Optional<User> findByUserId(String userId);

    /**
     * 检查邮箱是否已存在
     * @param email 邮箱
     * @return 是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 检查用户ID是否已存在
     * @param userId 用户ID
     * @return 是否存在
     */
    boolean existsByUserId(String userId);
}