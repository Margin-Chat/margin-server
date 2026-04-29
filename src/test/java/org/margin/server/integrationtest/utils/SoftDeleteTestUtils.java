package org.margin.server.integrationtest.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SoftDeleteTestUtils {

    private static JdbcTemplate jdbcTemplate;

    @Autowired
    public SoftDeleteTestUtils(JdbcTemplate jdbcTemplate) {
        SoftDeleteTestUtils.jdbcTemplate = jdbcTemplate;
    }

    public static Instant getDeletedAt(String table, String idColumn, Long id) {
        String sql = "SELECT deleted_at FROM " + table + " WHERE " + idColumn + " = ?";
        return jdbcTemplate.queryForObject(sql, Instant.class, id);
    }

    public static Long getConversationIdForChannel(Long channelId) {
        String sql = "SELECT conversation_id FROM conversations WHERE channel_id = ?";
        return jdbcTemplate.queryForObject(sql, Long.class, channelId);
    }

    public static boolean isDeleted(String table, String idColumn, Long id) {
        return getDeletedAt(table, idColumn, id) != null;
    }
}
