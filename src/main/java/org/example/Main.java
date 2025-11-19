package org.example;

import java.nio.file.Path;
import java.io.FileOutputStream;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class Main {

    
    private static final Scanner in = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("""
                ===============================
                Строковое меню (advanced) + MySQL + Excel
                Схема: string_menu, таблица: string_ops_adv
                ===============================""");

        while (true) {
            printMenu();
            int choice = readInt("Ваш выбор (0 — выход): ");

            switch (choice) {
                case 0 -> { System.out.println("Выход. До встречи!"); return; }
                case 1 -> handleListTables();
                case 2 -> handleEnsureTable();
                case 3 -> handleSubstring();
                case 4 -> handleCase();
                case 5 -> handleSearchEnds();
                case 6 -> handleExport();
                default -> System.out.println("Неизвестный пункт меню.");
            }
            System.out.println();
        }
    }

    private static void printMenu() {
        System.out.println("""
                ===============================
                1. Вывести все таблицы из MySQL.
                2. Создать/проверить таблицу в MySQL.
                3. Возвращение подстроки по индексам (сохранить и вывести).
                4. Перевод строк в верхний и нижний регистры (сохранить и вывести).
                5. Поиск подстроки и проверка окончания (сохранить и вывести).
                6. Сохранить все данные из MySQL в Excel и вывести на экран.
                ===============================""");
    }

    // ========= Handlers =========

    private static void handleListTables() {
        List<String> tables = Db.listTables();
        System.out.println("Таблицы в текущей базе:");
        if (tables.isEmpty()) System.out.println("  — (пусто)");
        else tables.forEach(t -> System.out.println("  - " + t));
    }

    private static void handleEnsureTable() {
        Db.ensureTable();
        System.out.println("Таблица string_ops_adv создана/проверена.");
    }

    private static void handleSubstring() {
        System.out.println("Введите исходную строку:");
        String source = readLine();

        int from, to;
        while (true) {
            from = readInt("from (0-based, включительно): ");
            to   = readInt("to (0-based, исключая): ");
            if (0 <= from && from <= to && to <= source.length()) break;
            System.out.println("Некорректные границы. Диапазон должен удовлетворять 0 <= from <= to <= " + source.length());
        }

        String ss = source.substring(from, to);
        long id = Db.insertSubstring(source, from, to, ss);

        System.out.println("Результат:");
        System.out.println("  id=" + id + " | op=SUBSTRING | from=" + from + " | to=" + to);
        System.out.println("  source: " + Db.shortPreview(source));
        System.out.println("  substring: " + Db.shortPreview(ss));
    }

    private static void handleCase() {
        System.out.println("Введите исходную строку:");
        String source = readLine();

        String up = source.toUpperCase();
        String low = source.toLowerCase();
        long id = Db.insertCase(source, up, low);

        System.out.println("Результат:");
        System.out.println("  id=" + id + " | op=CASE");
        System.out.println("  source: " + Db.shortPreview(source));
        System.out.println("  upper : " + Db.shortPreview(up));
        System.out.println("  lower : " + Db.shortPreview(low));
    }

    private static void handleSearchEnds() {
        System.out.println("Введите исходную строку:");
        String source = readLine();
        System.out.println("Введите искомую подстроку:");
        String query = readLine();

        int pos = source.indexOf(query);          // -1 если не нашлось
        boolean ends = source.endsWith(query);

        long id = Db.insertSearchEnds(source, query, pos, ends);

        System.out.println("Результат:");
        System.out.println("  id=" + id + " | op=SEARCH_ENDS");
        System.out.println("  source: " + Db.shortPreview(source));
        System.out.println("  query : " + Db.shortPreview(query));
        System.out.println("  found_pos=" + pos + " | ends_with=" + ends);
    }

    private static void handleExport() {
        List<LinkedHashMap<String, Object>> rows = Db.fetchAllForExport();
        if (rows.isEmpty()) {
            System.out.println("В таблице пока нет записей.");
            return;
        }
        try {
            Path path = ExcelExporter.export(rows, "string_ops_adv.xlsx");
            System.out.println("Экспортировано строк: " + rows.size());
            System.out.println("Файл: " + path.toAbsolutePath());

            System.out.println("\nПредпросмотр:");
            int i = 1;
            for (var r : rows) {
                System.out.printf("#%d | id=%s | %s | source=%s%n",
                        i++, r.get("id"), r.get("op_type"),
                        Db.shortPreview((String) r.get("source_text")));
            }
        } catch (Exception e) {
            System.out.println("Ошибка экспорта: " + e.getMessage());
        }
    }

    // ========= Ввод-утилиты =========

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = in.nextLine().trim();
            try { return Integer.parseInt(s); }
            catch (NumberFormatException e) { System.out.println("Введите целое число."); }
        }
    }

    private static String readLine() {
        String s = in.nextLine();
        while (s == null || s.isEmpty()) {
            // защищаемся от случайного пустого ввода
            s = in.nextLine();
        }
        return s;
    }

    // ========= Встроенный класс работы с БД =========
    static class Db {
        
        private static final String DB_URL  =
                "jdbc:mysql://localhost:3306/string_menu?useSSL=false&serverTimezone=UTC";
        private static final String DB_USER = "abc";
        private static final String DB_PASS = "abc123";

        static Connection getConnection() throws SQLException {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        }

        static List<String> listTables() {
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

        static void ensureTable() {
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

        static long insertSubstring(String source, int from, int to, String substr) {
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

        static long insertCase(String source, String up, String low) {
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

        static long insertSearchEnds(String source, String query, int pos, boolean ends) {
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

        static List<LinkedHashMap<String, Object>> fetchAllForExport() {
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

        static String shortPreview(String s) {
            if (s == null) return "null";
            return (s.length() <= 40) ? s : s.substring(0, 37) + "...";
        }
    }

    // ========= Встроенный класс экспорта в Excel =========
    static class ExcelExporter {
        private static final String[] COLS = new String[] {
                "id","created_at","op_type","source_text",
                "from_idx","to_idx","substring",
                "upper_text","lower_text",
                "query_text","found_pos","ends_with","note"
        };

        static Path export(List<LinkedHashMap<String, Object>> rows, String fileName) throws Exception {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("string_ops_adv");

                // Заголовок
                Row header = sheet.createRow(0);
                CellStyle bold = wb.createCellStyle();
                Font f = wb.createFont(); f.setBold(true); bold.setFont(f);
                for (int i = 0; i < COLS.length; i++) {
                    Cell c = header.createCell(i);
                    c.setCellValue(COLS[i]);
                    c.setCellStyle(bold);
                }

                // Данные
                AtomicInteger r = new AtomicInteger(1);
                for (LinkedHashMap<String, Object> row : rows) {
                    Row x = sheet.createRow(r.getAndIncrement());
                    for (int i = 0; i < COLS.length; i++) {
                        Object val = row.get(COLS[i]);
                        Cell c = x.createCell(i);
                        if (val == null) c.setBlank();
                        else if (val instanceof Number num) c.setCellValue(num.doubleValue());
                        else if (val instanceof Boolean b) c.setCellValue(b);
                        else c.setCellValue(String.valueOf(val));
                    }
                }

                for (int i = 0; i < COLS.length; i++) sheet.autoSizeColumn(i);

                Path path = Path.of(fileName);
                try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                    wb.write(fos);
                }
                return path;
            }
        }
    }
}
