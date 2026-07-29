package org.margin.server.social.conversation.controllers;

import org.margin.server.users.api.UserLookup;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.social.conversation.models.dtos.CreateThreadPostRequest;
import org.margin.server.social.conversation.models.dtos.ThreadConversationDTO;
import org.margin.server.social.conversation.models.dtos.ThreadSummaryDTO;
import org.margin.server.social.conversation.services.ThreadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ThreadController {

    private final ThreadService threadService;
    private final UserLookup userLookup;

    public ThreadController(ThreadService threadService,
                              UserLookup userLookup) {
        this.threadService = threadService;
        this.userLookup = userLookup;
    }

    @PostMapping("/channels/{channelId}/threads")
    public ThreadConversationDTO createPost(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable Long channelId,
                                            @RequestBody CreateThreadPostRequest request) {
        return threadService.createPost(user.id(), channelId, request.title(), request.body());
    }

    @GetMapping("/channels/{channelId}/threads")
    public List<ThreadSummaryDTO> getPostsForChannel(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable Long channelId) {
        return threadService.getPostsForChannel(user.id(), channelId);
    }

    @GetMapping("/threads")
    public List<ThreadSummaryDTO> getFollowedThreads(@AuthenticationPrincipal AuthenticatedUser user) {
        return threadService.getFollowedThreads(user.id());
    }

    @GetMapping("/threads/{conversationId}")
    public ThreadConversationDTO getThread(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable Long conversationId) {
        return threadService.getThread(user.id(), conversationId);
    }

    @PostMapping("/threads/{conversationId}/follow")
    public ResponseEntity<Void> followThread(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable Long conversationId) {
        threadService.followThread(user.id(), conversationId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/threads/{conversationId}/follow")
    public ResponseEntity<Void> unfollowThread(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable Long conversationId) {
        threadService.unfollowThread(user.id(), conversationId);
        return ResponseEntity.ok().build();
    }

}
