package dev.myagent.business.controller;

import java.util.List;
import java.util.UUID;
import dev.myagent.business.service.agent.AgentService;
import dev.myagent.business.dto.agent.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class AgentController {
    private final AgentService agent;
    public AgentController(AgentService agent) {
        this.agent = agent;
    }
    @GetMapping("/config")
    public AgentConfigResult config() { return agent.getConfig(); }
    @GetMapping("/conversations")
    public List<AgentConversation> list() { return agent.listConversations(); }
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentConversation create() { return agent.createConversation(); }
    @GetMapping("/conversations/{id}")
    public AgentConversationDetail detail(@PathVariable UUID id) { return agent.getConversation(id.toString()); }
    @PatchMapping("/conversations/{id}")
    public AgentConversation modify(@PathVariable UUID id, @Valid @RequestBody ConversationModifyDto request) {
        return agent.modifyConversation(id.toString(), request.title());
    }
    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { agent.deleteConversation(id.toString()); }
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AgentChatDto request) {
        return agent.chat(request.requestId().toString(), request.conversationId().toString(), request.message());
    }
    @PostMapping("/chat/cancel")
    public AgentCancelResult cancel(@Valid @RequestBody AgentCancelDto request) {
        return agent.cancel(request.requestId().toString(), request.conversationId().toString());
    }
}
