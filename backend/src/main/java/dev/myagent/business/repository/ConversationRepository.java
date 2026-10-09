package dev.myagent.business.repository;

import dev.myagent.business.convert.AgentHistoryConverter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.myagent.business.dto.agent.*;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Repository
@DependsOnDatabaseInitialization
public class ConversationRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AgentHistoryConverter codec;
    public ConversationRepository(JdbcTemplate jdbc, TransactionTemplate transactions, AgentHistoryConverter codec) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.codec = codec;
    }

    /** A previous process cannot leave a conversation permanently busy. */
    @PostConstruct
    void recoverInterruptedTurns() {
        jdbc.update("UPDATE turns SET status='error', error_message='服务重启，生成已中断' WHERE status='running'");
    }

    public List<AgentConversation> list() {
        return jdbc.query("SELECT * FROM conversations ORDER BY updated_at DESC, rowid DESC LIMIT 200", this::conversation);
    }

    public AgentConversation create() {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO conversations(id,title) VALUES (?,?)", id, "新对话");
        return get(id);
    }

    public AgentConversation get(String id) {
        return jdbc.query("SELECT * FROM conversations WHERE id=?", this::conversation, id).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对话不存在"));
    }

    public AgentConversationDetail detail(String id) {
        AgentConversation conversation = get(id);
        List<AgentTurnResult> turns = jdbc.query("SELECT * FROM turns WHERE conversation_id=? ORDER BY rowid", (rs, n) ->
                new AgentTurnResult(rs.getString("id"), rs.getString("prompt"), rs.getString("answer"),
                        rs.getString("status"), rs.getString("error_message"),
                        codec.read(rs.getString("tools_json"), new TypeReference<List<AgentToolResult>>() {}),
                        rs.getString("created_at")), id);
        return new AgentConversationDetail(conversation, turns);
    }

    public AgentConversation rename(String id, String title) {
        get(id);
        jdbc.update("UPDATE conversations SET title=? WHERE id=?", title.strip(), id);
        return get(id);
    }

    public void delete(String id) {
        if (jdbc.update("DELETE FROM conversations WHERE id=?", id) != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "对话不存在");
    }

    public void start(String requestId, String conversationId, String prompt) {
        transactions.executeWithoutResult(status -> {
            get(conversationId);
            jdbc.update("INSERT INTO turns(id,conversation_id,prompt,status) VALUES (?,?,?,'running')",
                    requestId, conversationId, prompt);
            jdbc.update("UPDATE conversations SET title=CASE WHEN title='新对话' THEN ? ELSE title END, "
                    + "updated_at=strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id=?",
                    prompt.substring(0, Math.min(prompt.length(), 32)), conversationId);
        });
    }

    public boolean hasRequest(String requestId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM turns WHERE id=?)", Boolean.class, requestId));
    }

    public void finish(String requestId, String conversationId, String state, String answer,
                       String error, List<AgentToolResult> tools, List<Message> history) {
        transactions.executeWithoutResult(status -> {
            int changed = jdbc.update("UPDATE turns SET status=?,answer=?,error_message=?,tools_json=?,history_json=? "
                            + "WHERE id=? AND status='running'", state, answer, error, codec.write(tools),
                    codec.encode(history), requestId);
            if (changed != 1) throw new IllegalStateException("Turn was already finalized");
            jdbc.update("UPDATE conversations SET updated_at=strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id=?", conversationId);
        });
    }

    /** Bound context by whole turns and a character budget; never split a tool call/result pair. */
    public List<Message> history(String id, int limit) {
        List<String> rows = jdbc.queryForList("SELECT history_json FROM turns WHERE conversation_id=? AND status='done' "
                + "ORDER BY rowid DESC LIMIT ?", String.class, id, limit);
        List<List<Message>> selected = new ArrayList<>();
        int chars = 0;
        for (String row : rows) {
            if (chars + row.length() > 64000) break;
            selected.add(codec.decode(row));
            chars += row.length();
        }
        Collections.reverse(selected);
        return selected.stream().flatMap(List::stream).toList();
    }

    private AgentConversation conversation(ResultSet rs, int row) throws SQLException {
        return new AgentConversation(rs.getString("id"), rs.getString("title"), rs.getString("created_at"), rs.getString("updated_at"));
    }
}
