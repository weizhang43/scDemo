package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Notice;
import response.ResponseDto;

public interface NoticeService extends IService<Notice> {

    /**
     * 管理端分页查询通知（可按标题、状态筛选）。
     */
    ResponseDto<Notice> pageQuery(Integer pageNum, Integer pageSize, String title, Integer status);

    /**
     * 首页轮播用：查询已发布的通知，按 sortOrder 降序、创建时间降序。
     */
    ResponseDto<Notice> listPublished();

    /**
     * 查询单条通知。
     */
    ResponseDto<Notice> getDetail(Long noticeId);

    /**
     * 新增通知。
     */
    ResponseDto<Notice> addNotice(Notice notice, Integer uId, String uName);

    /**
     * 修改通知。
     */
    ResponseDto<Notice> updateNotice(Notice notice);

    /**
     * 删除通知。
     */
    ResponseDto<Notice> removeNotice(Long noticeId);

    /**
     * 切换发布状态。
     */
    ResponseDto<Notice> changeStatus(Long noticeId, Integer status);
}
