package org.example;

import java.sql.*;
import java.util.*;

public class DbUtil {
    private static final String DB_URL  =
            "jdbc:mysql://localhost:3306/string_menu?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "abc";
    private static final String DB_PASS = "abc123";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // Показать таблицы текущей схемы
    public static List<String> listTables() {
        List<String> tables = new ArrayList<>();
        String sql = "SHOW TABLES";
        try (Connection c = getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) tables.add(rs.getString(1));
        } catch (SQLException e) {
            System.out.println("Ошибка при получении списка таблиц: " + e.getMessage());
        }
        return tables;
    }

    // Создать/проверить таблицу string_ops_adv
    public static void ensureTable() {
        final String ddl = """
            CREATE TABLE IF NOT EXISTS string_ops_adv (
              id           BIGINT PRIMARY KEY AUTO_INCREMENT,
              created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
              op_type      VARCHAR(40) NOT NULL,
              source_text  TEXT,
              from_idx     INT NULL,
              to_idx       INT NULL,
              substring    TEXT NULL,
              upper_text   TEXT NULL,
              lower_text   TEXT NULL,
              query_text   TEXT NULL,
              found_pos    INT NULL,
              ends_with    BOOLEAN NULL,
              note         VARCHAR(255) NULL
            )
            """;
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(ddl);
        } catch (SQLException e) {
            System.out.println("Ошибка при создании таблицы: " + e.getMessage());
        }
    }

    // Вставки
    public static long insertSubstring(String source, int from, int to, String substr) {
        String sql = """
            INSERT INTO string_ops_adv
            (op_type, source_text, from_idx, to_idx, substring)
            VALUES ('SUBSTRING', ?, ?, ?, ?)
            """;
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, source);
            ps.setInt(2, from);
            ps.setInt(3, to);
            ps.setString(4, substr);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (SQLException e) {
            System.out.println("Ошибка insertSubstring: " + e.getMessage());
        }
        return -1L;
    }

    public static long insertCase(String source, String up, String low) {
        String sql = """
            INSERT INTO string_ops_adv
            (op_type, source_text, upper_text, lower_text)
            VALUES ('CASE', ?, ?, ?)
            """;
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, source);
            ps.setString(2, up);
            ps.setString(3, low);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (SQLException e) {
            System.out.println("Ошибка insertCase: " + e.getMessage());
        }
        return -1L;
    }

    public static long insertSearchEnds(String source, String query, int pos, boolean ends) {
        String sql = """
            INSERT INTO string_ops_adv
            (op_type, source_text, query_text, found_pos, ends_with)
            VALUES ('SEARCH_ENDS', ?, ?, ?, ?)
            """;
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, source);
            ps.setString(2, query);
            ps.setInt(3, pos);
            ps.setBoolean(4, ends);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (SQLException e) {
            System.out.println("Ошибка insertSearchEnds: " + e.getMessage());
        }
        return -1L;
    }

    // Все строки для экспорта (сохраняем порядок столбцов)
    public static List<LinkedHashMap<String, Object>> fetchAllForExport() {
        String sql = "SELECT * FROM string_ops_adv ORDER BY id";
        List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
        try (Connection c = getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();

            while (rs.next()) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    String col = md.getColumnLabel(i);
                    Object val = rs.getObject(i);
                    row.put(col, val);
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            System.out.println("Ошибка select: " + e.getMessage());
        }
        return rows;
    }

    // Утилита: короткий вывод строки
    public static String shortPreview(String s) {
        if (s == null) return "null";
        return (s.length() <= 40) ? s : s.substring(0, 37) + "...";
    }
}
