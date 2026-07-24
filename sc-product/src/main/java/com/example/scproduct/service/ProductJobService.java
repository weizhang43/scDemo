package com.example.scproduct.service;

public interface ProductJobService {

    /**
     * 分页扫表，并发调用 chat 服务为 proDesc 为空的商品生成描述，批量更新 MySQL 并同步 ES。
     * @return 执行摘要
     */
    String fillProDesc();

    /**
     * 全量重建 ES 索引：分页扫表 bulk upsert。
     * @return 执行摘要
     */
    String rebuildProDescIndex();

    /**
     * 为 imageUrl 为空的商品随机绑定本地目录中的图片（上传后回写 imageUrl）。
     * @return 执行摘要
     */
    String dealProductImage();
}
