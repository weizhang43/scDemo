package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Knowledge;
import com.curry.model.KnowledgeNote;
import response.ResponseDto;

import java.util.List;

public interface KnowledgeService extends IService<Knowledge> {

    ResponseDto<Knowledge> add(Knowledge knowledge);

    /** 取下一题：优先取 id 大于 currentId 的未删除记录，没有则从头循环；tag 非空时按标签过滤 */
    ResponseDto<Knowledge> next(Long currentId, Integer tag, String  type);

    /** 按 id 取题（已忽略的不返回），用于恢复上次浏览位置 */
    ResponseDto<Knowledge> getById(Long id, String type);

    /** 按关键字模糊搜索题干，返回前 20 条；tag 非空时按标签过滤 */
    ResponseDto<Knowledge> search(String keyword, Integer tag, String type);

    /** 取上一题：优先取 id 小于 currentId 的未删除记录，没有则回到最后一题；tag 非空时按标签过滤 */
    ResponseDto<Knowledge> prev(Long currentId, Integer tag, String type);

    /** 随机取一题；tag/type 非空时按当前范围过滤 */
    ResponseDto<Knowledge> random(Integer tag, String type);

    /** 查看答案时调用，累加查看次数并记录最后查看时间 */
    ResponseDto<Knowledge> view(Long id);

    /** 切换收藏状态 */
    ResponseDto<Knowledge> toggleFavorite(Long id);

    /** 忽略试题，逻辑删除 */
    ResponseDto<Knowledge> ignore(Long id);

    ResponseDto<KnowledgeNote> addNote(Long knowledgeId, String content);

    ResponseDto<KnowledgeNote> updateNote(Long noteId, KnowledgeNote note);

    ResponseDto<KnowledgeNote> deleteNote(Long noteId);

    ResponseDto<KnowledgeNote> listNotes(Long knowledgeId);
}
