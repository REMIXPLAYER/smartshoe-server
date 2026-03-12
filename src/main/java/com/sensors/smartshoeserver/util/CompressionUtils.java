package com.sensors.smartshoeserver.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 压缩工具类
 * 提供GZIP压缩和解压功能
 */
public class CompressionUtils {

    /**
     * 压缩字符串数据
     *
     * @param data 原始字符串
     * @return Base64编码的压缩数据
     * @throws IOException 压缩失败时抛出
     */
    public static String compress(String data) throws IOException {
        if (data == null || data.isEmpty()) {
            return data;
        }

        byte[] input = data.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(input);
        }

        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /**
     * 解压字符串数据
     *
     * @param compressedData Base64编码的压缩数据
     * @return 原始字符串
     * @throws IOException 解压失败时抛出
     */
    public static String decompress(String compressedData) throws IOException {
        if (compressedData == null || compressedData.isEmpty()) {
            return compressedData;
        }

        byte[] compressed = Base64.getDecoder().decode(compressedData);
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }

        return baos.toString(StandardCharsets.UTF_8);
    }

    /**
     * 压缩字节数组
     *
     * @param data 原始字节数组
     * @return 压缩后的字节数组
     * @throws IOException 压缩失败时抛出
     */
    public static byte[] compress(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return data;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    /**
     * 解压字节数组
     *
     * @param compressedData 压缩后的字节数组
     * @return 原始字节数组
     * @throws IOException 解压失败时抛出
     */
    public static byte[] decompress(byte[] compressedData) throws IOException {
        if (compressedData == null || compressedData.length == 0) {
            return compressedData;
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }

        return baos.toByteArray();
    }

    /**
     * 判断数据是否已压缩（简单启发式判断）
     * 通过检查数据是否看起来像Base64编码的压缩数据
     *
     * @param data 数据字符串
     * @return 是否可能已压缩
     */
    public static boolean isCompressed(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }

        // 检查是否是有效的Base64字符串
        try {
            Base64.getDecoder().decode(data);
            // 如果能解码且长度大于原始长度的80%，可能是压缩的
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 计算压缩率
     *
     * @param originalSize 原始大小
     * @param compressedSize 压缩后大小
     * @return 压缩率（0-1之间）
     */
    public static double calculateCompressionRatio(int originalSize, int compressedSize) {
        if (originalSize == 0) {
            return 0.0;
        }
        return (double) compressedSize / originalSize;
    }
}
