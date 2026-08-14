package com.example.scorder.util;

import exception.BusinessException;

import java.util.List;

/**
 * 用户上传图片URL的校验与拼接工具，评价与售后凭证共用。
 * 图片先经 sc-product 的 /product/image/upload 上传拿到相对URL，本工具只负责把这批URL安全地拼成一列。
 */
public final class ImageUrlUtil {

    /** 只接受本站图片服务的相对路径，防止把任意外链写进库里 */
    private static final String ALLOWED_PREFIX = "/product/image/";

    /** 单个URL最大长度，3个URL加分隔符需落进 VARCHAR(512) */
    private static final int URL_MAX_LENGTH = 160;

    private ImageUrlUtil() {
    }

    /**
     * 校验图片URL列表并拼接为逗号分隔串。
     *
     * @param images 相对URL列表，可为 null/空（返回 null）
     * @param max    最多允许几张
     * @return 逗号分隔的URL串，无图时返回 null
     */
    public static String validateAndJoin(List<String> images, int max) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        if (images.size() > max) {
            throw new BusinessException("最多上传 " + max + " 张图片");
        }
        StringBuilder joined = new StringBuilder();
        for (String url : images) {
            String trimmed = url == null ? "" : url.trim();
            if (trimmed.isEmpty() || trimmed.contains(",") || trimmed.length() > URL_MAX_LENGTH
                    || !trimmed.startsWith(ALLOWED_PREFIX)) {
                throw new BusinessException("图片地址不合法");
            }
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(trimmed);
        }
        return joined.toString();
    }
}
