# Консольное меню: строки (substring, регистры, поиск, `endsWith`) + MySQL + Excel

Небольшое Java-приложение с интерактивным меню: операции со строками, сохранение результатов в **MySQL**, экспорт в **Excel**.
Проект: **string-menu-mysql-advanced**

---

## Требования

* **JDK 17+**
* **MySQL 8.x** (`localhost:3306`)
* Доступ к БД с правами на `CREATE TABLE`/`INSERT`/`SELECT`

---

## Установка БД

1. Выполнить SQL-скрипт:

```sql
SOURCE create_db.sql;
```

2. При необходимости поменять креды в коде:
   `src/main/java/org/example/Main.java` — константы `DB_URL`, `DB_USER`, `DB_PASS`.

---

## Сборка и запуск

**Сборка JAR**

```bash
mvn -q -DskipTests package
```

**Запуск**

```bash
java -jar target/string-menu-mysql-advanced-1.0.0-shaded.jar
```

---

## Пункты меню

1. **Показать все таблицы** в текущей схеме MySQL (проверка подключения).
2. **Создать/проверить** таблицу `string_ops`.
3. **Подстрока по индексам**: `substring(start, end)`
   — читает исходную строку и индексы; сохраняет результат в БД, выводит в консоль.
4. **Регистры**: перевести строку в **UPPER** и **lower**
   — сохраняет обе версии в БД, выводит в консоль.
5. **Поиск и окончание**: найти подстроку и проверить `endsWith`
   — сохраняет искомые значения и булев результат в БД, выводит в консоль.
6. **Экспорт в Excel**: выгрузить все записи `string_ops` в `string_ops.xlsx` и показать путь к файлу.

> Для пп. 3–5 каждая операция добавляется отдельной строкой в `string_ops` (с параметрами и результатами).

---

## Структура таблицы `string_ops`

(создаётся скриптом `create_db.sql` или пунктом меню №2)

* `id` — BIGINT PK AUTO_INCREMENT
* `created_at` — TIMESTAMP DEFAULT CURRENT_TIMESTAMP
* `source_text` — TEXT
* `sub_from`, `sub_to` — INT (границы `substring`)
* `substring_value` — TEXT
* `upper_text`, `lower_text` — TEXT
* `search_term` — VARCHAR(255)
* `ends_with` — VARCHAR(255)
* `ends_with_result` — BOOLEAN

---

## Примечания

* Семантика Java: `substring(start, end)` — **`end` не включается**, индексы **с нуля**.
* Диапазоны проверяются; при ошибке ввод/сообщение — без падения приложения.
* Excel-файл `string_ops.xlsx` создаётся рядом с JAR (или в корне проекта при запуске из IDE).
* Возможное предупреждение `StatusLogger / log4j2` при экспорте **не влияет** на работу.
