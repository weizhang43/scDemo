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

@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService {

    @Override
    public ResponseDto<Notice> pageQuery(Integer pageNum, Integer pageSize, String title, Integer status) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<Notice>()
                .like(StringUtils.hasText(title), Notice::getTitle, title)
                .eq(status != null, Notice::getStatus, status)
                .orderByDesc(Notice::getSortOrder)
                .orderByDesc(Notice::getCreateTime);
        Page<Notice> page = baseMapper.selectPage(new Page<>(current, size), wrapper);
        return ResponseDto.success(page);
    }

    @Override
    public ResponseDto<Notice> listPublished() {
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, 1)
                .orderByDesc(Notice::getSortOrder)
                .orderByDesc(Notice::getCreateTime);
        List<Notice> list = baseMapper.selectList(wrapper);
        return ResponseDto.success(list);
    }

    @Override
    public ResponseDto<Notice> getDetail(Long noticeId) {
        Notice notice = baseMapper.selectById(noticeId);
        return notice == null ? ResponseDto.error("通知不存在") : ResponseDto.success(notice);
    }

    @Override
    public ResponseDto<Notice> addNotice(Notice notice, Integer uId, String uName) {
        if (!StringUtils.hasText(notice.getTitle())) {
            return ResponseDto.error("标题不能为空");
        }
        notice.setNoticeId(null);
        if (notice.getStatus() == null) {
            notice.setStatus(1);
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

    @Override
    public ResponseDto<Notice> removeNotice(Long noticeId) {
        if (noticeId == null) {
            return ResponseDto.error("通知ID不能为空");
        }
        int rows = baseMapper.deleteById(noticeId);
        return rows > 0 ? ResponseDto.success(null) : ResponseDto.error("通知不存在或已删除");
    }

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
