package org.margin.server.social.conversation.controllers;

import org.margin.server.social.conversation.models.dtos.CreateThreadPostRequest;
import org.margin.server.social.conversation.models.dtos.ThreadConversationDTO;
import org.margin.server.social.conversation.models.dtos.ThreadSummaryDTO;
import org.margin.server.social.conversation.services.ThreadService;
import org.margin.server.users.models.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ThreadController {

    private final ThreadService threadService;

    public ThreadController(ThreadService threadService) {
        this.threadService = threadService;
    }

    @PostMapping("/channels/{channelId}/threads")
    public ThreadConversationDTO createPost(@AuthenticationPrincipal User user,
                                            @PathVariable Long channelId,
                                            @RequestBody CreateThreadPostRequest request) {
        return threadService.createPost(user, channelId, request.title(), request.body());
    }

    @GetMapping("/channels/{channelId}/threads")
    public List<ThreadSummaryDTO> getPostsForChannel(@AuthenticationPrincipal User user,
                                                     @PathVariable Long channelId) {
        return threadService.getPostsForChannel(user, channelId);
    }

    @GetMapping("/threads")
    public List<ThreadSummaryDTO> getFollowedThreads(@AuthenticationPrincipal User user) {
        return threadService.getFollowedThreads(user);
    }

    @GetMapping("/threads/{conversationId}")
    public ThreadConversationDTO getThread(@AuthenticationPrincipal User user,
                                           @PathVariable Long conversationId) {
        return threadService.getThread(user, conversationId);
    }

    @PostMapping("/threads/{conversationId}/follow")
    public ResponseEntity<Void> followThread(@AuthenticationPrincipal User user,
                                             @PathVariable Long conversationId) {
        threadService.followThread(user, conversationId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/threads/{conversationId}/follow")
    public ResponseEntity<Void> unfollowThread(@AuthenticationPrincipal User user,
                                               @PathVariable Long conversationId) {
        threadService.unfollowThread(user, conversationId);
        return ResponseEntity.ok().build();
    }
}
