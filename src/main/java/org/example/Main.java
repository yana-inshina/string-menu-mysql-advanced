package org.example;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class Main {

    // ====== БД ======
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/string_menu?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "app";
    private static final String DB_PASS = "app123";

    // схема/имена 
    private static final String TABLE = "string_ops_adv";
    private static final String EXCEL_FILE = "string_ops.xlsx";

    private static final Scanner SC = new Scanner(System.in);

    public static void main(String[] args) {
        
        try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (ClassNotFoundException ignored) {}

        println("Строковое меню (advanced)  +  MySQL  +  Excel");
        println("Схема: string_menu, таблица: " + TABLE);
        while (true) {
            printMenu();
            int choice = readInt("Ваш выбор (0 — выход): ");
            switch (choice) {
                case 0 -> {
                    println("Выход. До встречи!");
                    return;
                }
                case 1 -> safeRun(Main::listTables);
                case 2 -> safeRun(Main::ensureTable);
                case 3 -> safeRun(Main::opSubstringSaveAndPrint);
                case 4 -> safeRun(Main::opUpperLowerUpdateAndPrint);
                case 5 -> safeRun(Main::opFindAndEndsWithUpdateAndPrint);
                case 6 -> safeRun(Main::exportAllToExcel);
                default -> println("Неизвестный пункт меню.");
            }
        }
    }

    // ---------- Меню ----------
    private static void printMenu() {
        println("\n=================================");
        println("1. Вывести все таблицы из MySQL.");
        println("2. Создать/проверить таблицу в MySQL.");
        println("3. Возвращение подстроки по индексам (сохранить и вывести).");
        println("4. Перевод строк в верхний и нижний регистры (сохранить и вывести).");
        println("5. Поиск подстроки и проверка окончания (сохранить и вывести).");
        println("6. Сохранить все данные из MySQL в Excel и вывести на экран.");
        println("=================================");
    }

    // ---------- Пункты меню ----------
    private static void listTables() throws Exception {
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "select table_name from information_schema.tables where table_schema = database() order by table_name");
             ResultSet rs = ps.executeQuery()) {

            println("Таблицы в текущей базе:");
            boolean any = false;
            while (rs.next()) {
                any = true;
                println("  - " + rs.getString(1));
            }
            if (!any) println("  — (пусто)");
        }
    }

    private static void ensureTable() throws Exception {
        String sql = """
                create table if not exists %s (
                  id           bigint primary key auto_increment,
                  created_at   timestamp default current_timestamp,
                  src          text not null,
                  sub_from     int,
                  sub_to       int,
                  substring_res text,
                  upper_res    text,
                  lower_res    text,
                  find_query   varchar(255),
                  find_pos     int,
                  ends_suffix  varchar(255),
                  ends_with    boolean
                ) engine=InnoDB default charset=utf8mb4;
                """.formatted(TABLE);
        try (Connection c = getConn(); Statement st = c.createStatement()) {
            st.execute(sql);
            println("Таблица %s создана/проверена.".formatted(TABLE));
        }
    }

    // 3) Подстрока: ввод -> вычислить -> СОХРАНИТЬ строкой -> вывести
    private static void opSubstringSaveAndPrint() throws Exception {
        ensureTable();
        String src = readLine("Введите исходную строку (>= 50 символов): ");
        while (src.length() < 50) {
            println("Строка короче 50 символов, попробуйте ещё раз.");
            src = readLine("Введите исходную строку (>= 50 символов): ");
        }
        int n = src.length();
        println("Длина исходной строки: " + n + ". Индексы Java: 0.."+(n-1)+". Конец НЕ включается.");
        int from = readInt("Введите from (0.."+n+"): ");
        int to   = readInt("Введите to   (from.."+n+"): ");

        // нормализуем
        if (from < 0) from = 0;
        if (to < 0)   to = 0;
        if (from > n) from = n;
        if (to > n)   to = n;
        if (to < from) { int t = from; from = to; to = t; }

        String sub = src.substring(from, to);
        println("substring(" + from + ", " + to + "):");
        println(sub);

        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "insert into " + TABLE + " (src, sub_from, sub_to, substring_res) values (?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, src);
            ps.setInt(2, from);
            ps.setInt(3, to);
            ps.setString(4, sub);
            ps.executeUpdate();
            long id = fetchGeneratedId(ps);
            println("Сохранено как запись id=" + id + ".");
        }
    }

    // 4) Верхний/нижний регистр над ПОСЛЕДНЕЙ записью -> update -> вывести
    private static void opUpperLowerUpdateAndPrint() throws Exception {
        ensureTable();
        Row r = fetchLastRow();
        if (r == null) {
            println("Нет данных. Сначала выполните пункт 3 (подстрока).");
            return;
        }
        String upper = r.src.toUpperCase();
        String lower = r.src.toLowerCase();

        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "update " + TABLE + " set upper_res=?, lower_res=? where id=?")) {
            ps.setString(1, upper);
            ps.setString(2, lower);
            ps.setLong(3, r.id);
            ps.executeUpdate();
        }
        println("UPPER(src):\n" + upper);
        println("LOWER(src):\n" + lower);
        println("Запись обновлена (id=" + r.id + ").");
    }

    // 5) Поиск подстроки + endsWith над ПОСЛЕДНЕЙ записью -> update -> вывести
    private static void opFindAndEndsWithUpdateAndPrint() throws Exception {
        ensureTable();
        Row r = fetchLastRow();
        if (r == null) {
            println("Нет данных. Сначала выполните пункт 3 (подстрока).");
            return;
        }
        String needle = readLine("Что ищем в src (find_query)? ");
        String suffix = readLine("Чем должна заканчиваться src (ends_suffix)? ");

        int pos = r.src.indexOf(needle);        // -1 если не найдено
        boolean ends = r.src.endsWith(suffix);

        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "update " + TABLE +
                     " set find_query=?, find_pos=?, ends_suffix=?, ends_with=? where id=?")) {
            ps.setString(1, needle);
            ps.setInt(2, pos);
            ps.setString(3, suffix);
            ps.setBoolean(4, ends);
            ps.setLong(5, r.id);
            ps.executeUpdate();
        }

        println("Результаты:");
        println(" indexOf(\"" + needle + "\") = " + pos);
        println(" endsWith(\"" + suffix + "\") = " + ends);
        println("Запись обновлена (id=" + r.id + ").");
    }

    // 6) Экспорт всех строк таблицы в Excel
    private static void exportAllToExcel() throws Exception {
        ensureTable();
        List<Row> rows = new ArrayList<>();
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement("select * from " + TABLE + " order by id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(map(rs));
        }

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("data");
            int r = 0;
            RowExcel(sh, r++, "id","created_at","src","sub_from","sub_to","substring_res",
                    "upper_res","lower_res","find_query","find_pos","ends_suffix","ends_with");

            DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (Row row : rows) {
                org.apache.poi.ss.usermodel.Row e = sh.createRow(r++);
                int c = 0;
                e.createCell(c++).setCellValue(row.id);
                e.createCell(c++).setCellValue(row.createdAt == null ? "" : row.createdAt);
                e.createCell(c++).setCellValue(row.src == null ? "" : row.src);
                e.createCell(c++).setCellValue(row.subFrom == null ? "" : String.valueOf(row.subFrom));
                e.createCell(c++).setCellValue(row.subTo == null ? "" : String.valueOf(row.subTo));
                e.createCell(c++).setCellValue(row.substringRes == null ? "" : row.substringRes);
                e.createCell(c++).setCellValue(row.upperRes == null ? "" : row.upperRes);
                e.createCell(c++).setCellValue(row.lowerRes == null ? "" : row.lowerRes);
                e.createCell(c++).setCellValue(row.findQuery == null ? "" : row.findQuery);
                e.createCell(c++).setCellValue(row.findPos == null ? "" : String.valueOf(row.findPos));
                e.createCell(c++).setCellValue(row.endsSuffix == null ? "" : row.endsSuffix);
                e.createCell(c++).setCellValue(row.endsWith == null ? "" : String.valueOf(row.endsWith));
            }
            for (int i = 0; i < 12; i++) sh.autoSizeColumn(i);

            Path out = Path.of(EXCEL_FILE).toAbsolutePath();
            try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
                wb.write(fos);
            }
            println("Экспорт завершён: " + out);
        }
    }

    private static void RowExcel(Sheet sh, int row, String... headers) {
        org.apache.poi.ss.usermodel.Row r = sh.createRow(row);
        for (int i = 0; i < headers.length; i++) {
            r.createCell(i).setCellValue(headers[i]);
        }
    }

    // ---------- Вспомогательное ----------
    private static Connection getConn() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    private static long fetchGeneratedId(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) return rs.getLong(1);
            return -1;
        }
    }

    private static Row fetchLastRow() throws SQLException {
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(
                     "select * from " + TABLE + " order by id desc limit 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return map(rs);
            return null;
        }
    }

    private static Row map(ResultSet rs) throws SQLException {
        Row r = new Row();
        r.id = rs.getLong("id");
        Timestamp ts = rs.getTimestamp("created_at");
        r.createdAt = ts == null ? null : ts.toLocalDateTime().toString();
        r.src = rs.getString("src");
        r.subFrom = getIntOrNull(rs, "sub_from");
        r.subTo = getIntOrNull(rs, "sub_to");
        r.substringRes = rs.getString("substring_res");
        r.upperRes = rs.getString("upper_res");
        r.lowerRes = rs.getString("lower_res");
        r.findQuery = rs.getString("find_query");
        r.findPos = getIntOrNull(rs, "find_pos");
        r.endsSuffix = rs.getString("ends_suffix");
        Object b = rs.getObject("ends_with");
        r.endsWith = (b == null) ? null : rs.getBoolean("ends_with");
        return r;
    }

    private static Integer getIntOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static String readLine(String prompt) {
        System.out.print(prompt);
        return SC.nextLine();
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = SC.nextLine().trim();
            try { return Integer.parseInt(s); }
            catch (Exception e) { System.out.println("Нужно целое число."); }
        }
    }

    private static void println(String s) { System.out.println(s); }

    private static void safeRun(ThrowingRunnable r) {
        try { r.run(); }
        catch (SQLIntegrityConstraintViolationException e) {
            println("SQL-ошибка: " + e.getMessage());
        }
        catch (SQLSyntaxErrorException e) {
            println("SQL синтаксис: " + e.getMessage());
        }
        catch (SQLException e) {
            println("SQL: " + e.getMessage());
        }
        catch (Exception e) {
            println("Ошибка: " + e.getMessage());
        }
    }

    @FunctionalInterface interface ThrowingRunnable { void run() throws Exception; }

    // простая модель строки таблицы
    private static class Row {
        long id;
        String createdAt;
        String src;
        Integer subFrom;
        Integer subTo;
        String substringRes;
        String upperRes;
        String lowerRes;
        String findQuery;
        Integer findPos;
        String endsSuffix;
        Boolean endsWith;
    }
}
