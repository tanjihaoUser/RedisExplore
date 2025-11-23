package com.wait.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wait.entity.domain.Post;
import com.wait.service.impl.PostServiceImpl;
import com.wait.util.ResponseUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 使用list存储用户帖子之间的关系
 */
@Slf4j
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostServiceImpl postService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPost(@RequestBody Post post) {
        log.info("Creating post for user: {}", post.getUserId());
        Long postId = postService.insert(post);
        Map<String, Object> extraFields = new HashMap<>();
        extraFields.put("postId", postId);
        return ResponseUtil.success(extraFields, post);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getPostsByUserId(@PathVariable Long userId, @RequestParam int page, @RequestParam int size) {
        log.info("Getting posts for user: {}, page: {}, size: {}", userId, page, size);
        List<Post> posts = postService.getUserPagedPosts(userId, page, size);
        return ResponseUtil.success(posts);
    }

    @PutMapping("")
    public ResponseEntity<Map<String, Object>> updatePost(
            @RequestBody Post post) {
        log.info("Updating post: {}", post.getId());
        int rowsAffected = postService.update(post);
        Map<String, Object> data = new HashMap<>();
        data.put("rowsAffected", rowsAffected);
        return ResponseUtil.success(data);
    }

    @DeleteMapping()
    public ResponseEntity<Map<String, Object>> deletePost(@RequestParam("userId") Long userId, @RequestParam("postId") Long postId) {
        log.info("Deleting post: {}, user: {}", postId, userId);
        int rowsAffected = postService.delete(userId, postId);
        Map<String, Object> data = new HashMap<>();
        data.put("rowsAffected", rowsAffected);
        return ResponseUtil.success("帖子删除成功", data);
    }
}
