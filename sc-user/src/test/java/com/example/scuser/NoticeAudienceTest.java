package com.example.scuser;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.curry.model.Notice;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.controller.NoticeController;
import com.example.scuser.mapper.NoticeMapper;
import com.example.scuser.service.NoticeService;
import com.example.scuser.service.impl.NoticeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import response.ResponseDto;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NoticeAudienceTest {
    private NoticeServiceImpl service;
    private NoticeMapper mapper;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "notice-test"), Notice.class);
        service = new NoticeServiceImpl();
        mapper = mock(NoticeMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void loginListUsesSelectedRoleAndRejectsUnknownRoles() {
        NoticeController controller = new NoticeController();
        NoticeService noticeService = mock(NoticeService.class);
        ReflectionTestUtils.setField(controller, "noticeService", noticeService);
        when(noticeService.listPublished(anyInt())).thenAnswer(invocation -> ResponseDto.success(Collections.emptyList()));

        for (int role : Arrays.asList(AuthConstant.U_TYPE_MERCHANT, AuthConstant.U_TYPE_CUSTOMER, AuthConstant.U_TYPE_ADMIN)) {
            assertEquals(200, controller.loginList(role).getCode());
            verify(noticeService).listPublished(role);
        }
        assertNotEquals(200, controller.loginList(null).getCode());
        assertNotEquals(200, controller.loginList(0).getCode());
        assertNotEquals(200, controller.loginList(4).getCode());
        verifyNoMoreInteractions(noticeService);
    }

    @Test
    void publishedListFiltersAnonymousAndRoles() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        for (int type = 0; type <= 3; type++) {
            service.listPublished(type == 0 ? null : type);
        }
        ArgumentCaptor<LambdaQueryWrapper<Notice>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper, times(4)).selectList(captor.capture());
        for (int type = 0; type <= 3; type++) {
            String sql = captor.getAllValues().get(type).getSqlSegment();
            assertTrue(sql.contains("target_types IS NULL"));
            assertEquals(type != 0, sql.contains("target_types &"));
            assertTrue(sql.contains("status"));
        }
    }

    @Test
    void detailRequiresPublishedAndMatchingAudience() {
        Notice notice = new Notice();
        notice.setStatus(1);
        notice.setTargetTypes(2 | 4);
        when(mapper.selectById(1L)).thenReturn(notice);
        assertEquals(200, service.getDetail(1L, 2).getCode());
        assertEquals(200, service.getDetail(1L, 3).getCode());
        assertNotEquals(200, service.getDetail(1L, 1).getCode());
        assertNotEquals(200, service.getDetail(1L, null).getCode());
        notice.setTargetTypes(null);
        assertEquals(200, service.getDetail(1L, null).getCode());
        notice.setStatus(0);
        assertNotEquals(200, service.getDetail(1L, 2).getCode());
    }

    @Test
    void clearingAudienceSetsColumnToNull() {
        Notice existing = new Notice();
        existing.setNoticeId(1L);
        existing.setTargetTypes(2);
        when(mapper.selectById(1L)).thenReturn(existing);
        Notice updated = new Notice();
        updated.setNoticeId(1L);
        updated.setTargetTypes(null);
        service.updateNotice(updated);
        ArgumentCaptor<LambdaUpdateWrapper<Notice>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSet().contains("target_types"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(null));
    }

    @Test
    void rejectsInvalidMasksBeforeInsert() {
        for (Integer invalid : Arrays.asList(0, 8, -1)) {
            Notice notice = new Notice();
            notice.setTitle("test");
            notice.setTargetTypes(invalid);
            ResponseDto<Notice> response = service.addNotice(notice, 1, "admin");
            assertNotEquals(200, response.getCode());
        }
        verify(mapper, never()).insert(any());
    }
}
