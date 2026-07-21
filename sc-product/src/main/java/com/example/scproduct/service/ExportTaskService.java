package com.example.scproduct.service;

import com.example.scproduct.vo.ExportTaskVO;

import javax.servlet.http.HttpServletResponse;

/**
 * 异步导出任务编排：基于 Redis 存任务状态 + 本地线程池执行。
 */
public interface ExportTaskService {

    /**
     * 提交异步导出任务，立即返回 taskId。
     * 查询参数通过 ProductExportQuery 传入。
     */
    ExportTaskVO submit(ProductService.ProductExportQuery query);

    /** 查询任务状态 */
    ExportTaskVO status(String taskId);

    /** 取消任务（异步线程下一页循环检测到后退出） */
    ExportTaskVO cancel(String taskId);

    /**
     * 下载已完成任务的 xlsx 文件到 response。
     * @return true 表示文件成功写入 response；false 表示任务不存在或不可下载
     */
    boolean download(String taskId, HttpServletResponse response) throws Exception;
}
