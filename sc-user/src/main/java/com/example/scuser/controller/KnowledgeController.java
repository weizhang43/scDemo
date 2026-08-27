package com.example.scuser.controller;

import com.curry.model.Knowledge;
import com.curry.model.KnowledgeNote;
import com.example.scuser.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

@RestController
@RequestMapping("/user/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @PostMapping
    public ResponseDto<Knowledge> add(@RequestBody Knowledge knowledge) {
        return knowledgeService.add(knowledge);
    }

    /** 按关键字搜索题干，用于输入自动补全；tag 为标签筛选 */
    @GetMapping("/search")
    public ResponseDto<Knowledge> search(@RequestParam("keyword") String keyword,
                                         @RequestParam(value = "tag", required = false) Integer tag,
                                         @RequestParam(value = "type", required = false) String type) {
        return knowledgeService.search(keyword, tag, type);
    }

    /** 按 id 取题，用于恢复上次浏览位置 */
    @GetMapping("/{id}")
    public ResponseDto<Knowledge> get(@PathVariable("id") Long id,
                                      @RequestParam(value = "type", required = false) String type) {
        return knowledgeService.getById(id, type);
    }

    /** 浏览下一题，currentId 为当前题 id，首次传空；tag 为标签筛选 */
    @GetMapping("/next")
    public ResponseDto<Knowledge> next(@RequestParam(value = "currentId", required = false) Long currentId,
                                       @RequestParam(value = "tag", required = false) Integer tag,
                                       @RequestParam(value = "type", required = false) String type) {
        return knowledgeService.next(currentId, tag, type);
    }

    /** 浏览上一题，currentId 为当前题 id；tag 为标签筛选 */
    @GetMapping("/prev")
    public ResponseDto<Knowledge> prev(@RequestParam(value = "currentId", required = false) Long currentId,
                                       @RequestParam(value = "tag", required = false) Integer tag,
                                       @RequestParam(value = "type", required = false) String type) {
        return knowledgeService.prev(currentId, tag, type);
    }

    /** 随机浏览一题；tag/type 非空时按当前范围过滤 */
    @GetMapping("/random")
    public ResponseDto<Knowledge> random(@RequestParam(value = "tag", required = false) Integer tag,
                                         @RequestParam(value = "type", required = false) String type) {
        return knowledgeService.random(tag, type);
    }

    /** 查看答案，同时记录查看进度 */
    @PostMapping("/view/{id}")
    public ResponseDto<Knowledge> view(@PathVariable("id") Long id) {
        return knowledgeService.view(id);
    }

    /** 切换收藏 */
    @PostMapping("/favorite/{id}")
    public ResponseDto<Knowledge> favorite(@PathVariable("id") Long id) {
        return knowledgeService.toggleFavorite(id);
    }

    /** 忽略试题（逻辑删除） */
    @PostMapping("/ignore/{id}")
    public ResponseDto<Knowledge> ignore(@PathVariable("id") Long id) {
        return knowledgeService.ignore(id);
    }

    @PostMapping("/{id}/note")
    public ResponseDto<KnowledgeNote> addNote(@PathVariable("id") Long id, @RequestBody KnowledgeNote note) {
        return knowledgeService.addNote(id, note == null ? null : note.getContent());
    }

    @PutMapping("/note/{noteId}")
    public ResponseDto<KnowledgeNote> updateNote(@PathVariable("noteId") Long noteId, @RequestBody KnowledgeNote note) {
        return knowledgeService.updateNote(noteId, note);
    }

    @DeleteMapping("/note/{noteId}")
    public ResponseDto<KnowledgeNote> deleteNote(@PathVariable("noteId") Long noteId) {
        return knowledgeService.deleteNote(noteId);
    }

    @GetMapping("/{id}/notes")
    public ResponseDto<KnowledgeNote> listNotes(@PathVariable("id") Long id) {
        return knowledgeService.listNotes(id);
    }
}
