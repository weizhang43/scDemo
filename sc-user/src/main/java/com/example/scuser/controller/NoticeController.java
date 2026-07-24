package com.example.scuser.controller;

import com.curry.model.Notice;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.annotation.OpLog;
import com.example.scuser.service.NoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import response.ResponseDto;

@RestController
@RequestMapping("/user/notice")
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    /** 管理端分页查询 */
    @GetMapping("/page")
    public ResponseDto<Notice> page(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                    @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                    @RequestParam(value = "title", required = false) String title,
                                    @RequestParam(value = "status", required = false) Integer status) {
        return noticeService.pageQuery(pageNum, pageSize, title, status);
    }

    /** 首页轮播：已发布的通知 */
    @GetMapping("/list")
    public ResponseDto<Notice> list() {
        return noticeService.listPublished();
    }

    @GetMapping("/{noticeId}")
    public ResponseDto<Notice> get(@PathVariable("noticeId") Long noticeId) {
        return noticeService.getDetail(noticeId);
    }

    @OpLog(module = "通知管理", type = OpLog.OpType.ADD, description = "新增通知")
    @PostMapping
    public ResponseDto<Notice> add(@RequestBody Notice notice,
                                   @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
                                   @RequestHeader(value = AuthConstant.HEADER_X_USER_NAME, required = false) String uName) {
        return noticeService.addNotice(notice, uId, uName);
    }

    @OpLog(module = "通知管理", type = OpLog.OpType.UPDATE, description = "修改通知")
    @PutMapping
    public ResponseDto<Notice> update(@RequestBody Notice notice) {
        return noticeService.updateNotice(notice);
    }

    @OpLog(module = "通知管理", type = OpLog.OpType.DELETE, description = "删除通知")
    @DeleteMapping("/{noticeId}")
    public ResponseDto<Notice> delete(@PathVariable("noticeId") Long noticeId) {
        return noticeService.removeNotice(noticeId);
    }

    @OpLog(module = "通知管理", type = OpLog.OpType.UPDATE, description = "切换通知状态")
    @PostMapping("/status")
    public ResponseDto<Notice> changeStatus(@RequestParam("noticeId") Long noticeId,
                                            @RequestParam("status") Integer status) {
        return noticeService.changeStatus(noticeId, status);
    }
}
