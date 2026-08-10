package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Notice;
import com.example.scuser.mapper.NoticeMapper;
import com.example.scuser.service.NoticeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import response.ResponseDto;

import java.util.Date;
import java.util.List;

/**
 * 通知公告服务：分页查询、发布列表、增删改与状态变更。
 */
@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService {

    /** 分页查询默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 通知状态：已发布 */
    private static final int STATUS_PUBLISHED = 1;

    /**
     * 按标题与状态分页查询通知。
     */
    @Override
    public ResponseDto<Notice> pageQuery(Integer pageNum, Integer pageSize, String title, Integer status) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<Notice>()
                .like(StringUtils.hasText(title), Notice::getTitle, title)
                .eq(status != null, Notice::getStatus, status)
                .orderByDesc(Notice::getSortOrder)
                .orderByDesc(Notice::getCreateTime);
        Page<Notice> page = baseMapper.selectPage(new Page<>(current, size), wrapper);
        return ResponseDto.success(page);
    }

    /**
     * 查询全部已发布的通知，按排序值与创建时间倒序。
     */
    @Override
    public ResponseDto<Notice> listPublished() {
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, STATUS_PUBLISHED)
                .orderByDesc(Notice::getSortOrder)
                .orderByDesc(Notice::getCreateTime);
        List<Notice> list = baseMapper.selectList(wrapper);
        return ResponseDto.success(list);
    }

    /**
     * 按 ID 查询通知详情。
     */
    @Override
    public ResponseDto<Notice> getDetail(Long noticeId) {
        Notice notice = baseMapper.selectById(noticeId);
        return notice == null ? ResponseDto.error("通知不存在") : ResponseDto.success(notice);
    }

    /**
     * 新增通知：默认已发布、排序值 0，记录创建人信息。
     */
    @Override
    public ResponseDto<Notice> addNotice(Notice notice, Integer uId, String uName) {
        if (!StringUtils.hasText(notice.getTitle())) {
            return ResponseDto.error("标题不能为空");
        }
        notice.setNoticeId(null);
        if (notice.getStatus() == null) {
            notice.setStatus(STATUS_PUBLISHED);
        }
        if (notice.getSortOrder() == null) {
            notice.setSortOrder(0);
        }
        notice.setCreateBy(uId);
        notice.setCreateName(uName);
        Date now = new Date();
        notice.setCreateTime(now);
        notice.setUpdateTime(now);
        baseMapper.insert(notice);
        return ResponseDto.success(notice);
    }

    /**
     * 修改通知内容，创建人信息不允许被覆盖。
     */
    @Override
    public ResponseDto<Notice> updateNotice(Notice notice) {
        if (notice.getNoticeId() == null) {
            return ResponseDto.error("通知ID不能为空");
        }
        if (baseMapper.selectById(notice.getNoticeId()) == null) {
            return ResponseDto.error("通知不存在");
        }
        notice.setCreateBy(null);
        notice.setCreateName(null);
        notice.setCreateTime(null);
        notice.setUpdateTime(new Date());
        baseMapper.updateById(notice);
        return ResponseDto.success(baseMapper.selectById(notice.getNoticeId()));
    }

    /**
     * 删除通知。
     */
    @Override
    public ResponseDto<Notice> removeNotice(Long noticeId) {
        if (noticeId == null) {
            return ResponseDto.error("通知ID不能为空");
        }
        int rows = baseMapper.deleteById(noticeId);
        return rows > 0 ? ResponseDto.success(null) : ResponseDto.error("通知不存在或已删除");
    }

    /**
     * 变更通知的发布状态。
     */
    @Override
    public ResponseDto<Notice> changeStatus(Long noticeId, Integer status) {
        if (noticeId == null || status == null) {
            return ResponseDto.error("参数不完整");
        }
        Notice update = new Notice();
        update.setNoticeId(noticeId);
        update.setStatus(status);
        update.setUpdateTime(new Date());
        baseMapper.updateById(update);
        return ResponseDto.success(baseMapper.selectById(noticeId));
    }
}
