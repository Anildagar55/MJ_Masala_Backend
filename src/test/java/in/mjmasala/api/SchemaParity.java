package in.mjmasala.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares the hand-written migration against the schema Hibernate would create for the
 * same dialect.
 *
 * {@code ddl-auto=validate} is what runs at startup, so the migration has to agree with the
 * entity model column for column. Running the real database is the only complete check, but
 * this catches the class of mistake that actually happens — a column type drifting, or a
 * column being forgotten — without needing a server.
 */
final class SchemaParity {

    private SchemaParity() {
    }

    private static final Set<String> CONSTRAINT_STARTS = Set.of(
            "not", "null", "default", "primary", "unique", "check", "references",
            "generated", "auto_increment", "engine", "constraint", "collate", "comment");

    /** Hibernate's script: one table per line, {@code create table x (a int, b varchar(9))}. */
    static Map<String, Map<String, String>> parseGenerated(Path script) throws IOException {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        Pattern table = Pattern.compile(
                "create table (?:if not exists )?(\\w+) \\((.*)\\)(?: engine=\\w+)?;?$",
                Pattern.CASE_INSENSITIVE);

        for (String raw : Files.readAllLines(script)) {
            String line = raw.trim();
            Matcher m = table.matcher(line);
            if (!m.matches()) continue;

            Map<String, String> columns = new LinkedHashMap<>();
            for (String part : splitTopLevel(m.group(2))) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                String lower = trimmed.toLowerCase(Locale.ROOT);
                if (lower.startsWith("primary key") || lower.startsWith("unique")
                        || lower.startsWith("foreign key") || lower.startsWith("constraint")
                        || lower.startsWith("key ") || lower.startsWith("index ")) {
                    continue;
                }
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length < 2) continue;
                columns.put(tokens[0].toLowerCase(Locale.ROOT), typeOf(tokens, 1));
            }
            out.put(m.group(1).toLowerCase(Locale.ROOT), columns);
        }
        return out;
    }

    /** The hand-written migration: one column per line inside {@code create table x (}. */
    static Map<String, Map<String, String>> parseMigration(Path file) throws IOException {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        Pattern table = Pattern.compile("create table (?:if not exists )?(\\w+)\\s*\\(",
                Pattern.CASE_INSENSITIVE);
        String current = null;

        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("--")) continue;

            Matcher m = table.matcher(line);
            if (m.find()) {
                current = m.group(1).toLowerCase(Locale.ROOT);
                out.put(current, new LinkedHashMap<>());
                continue;
            }
            if (current != null && line.startsWith(")")) {
                current = null;
                continue;
            }
            if (current == null) continue;

            String body = line.replaceAll(",$", "").trim();
            String lower = body.toLowerCase(Locale.ROOT);
            if (lower.startsWith("primary key") || lower.startsWith("unique")
                    || lower.startsWith("constraint") || lower.startsWith("foreign key")
                    || lower.startsWith("key ") || lower.startsWith("index ")
                    || lower.startsWith("check ")) {
                continue;
            }
            String[] tokens = body.split("\\s+");
            if (tokens.length < 2) continue;
            out.get(current).put(tokens[0].replace("`", "").toLowerCase(Locale.ROOT), typeOf(tokens, 1));
        }
        return out;
    }

    /** Every difference between the generated schema and the migration. */
    static List<String> differences(Map<String, Map<String, String>> generated,
                                    Map<String, Map<String, String>> migration) {
        List<String> problems = new ArrayList<>();

        for (String table : new TreeSet<>(generated.keySet())) {
            if (!migration.containsKey(table)) {
                problems.add("table " + table + " is in the entity model but not in the migration");
                continue;
            }
            for (Map.Entry<String, String> column : generated.get(table).entrySet()) {
                String name = column.getKey();
                String expected = column.getValue();
                String actual = migration.get(table).get(name);
                if (actual == null) {
                    problems.add(table + "." + name + " is missing (Hibernate wants " + expected + ")");
                } else if (!expected.equals(actual)) {
                    problems.add(table + "." + name + ": Hibernate wants " + expected
                            + " but the migration says " + actual);
                }
            }
        }
        for (String table : new TreeSet<>(migration.keySet())) {
            if (!generated.containsKey(table)) {
                problems.add("table " + table + " is in the migration but not in the entity model");
            }
        }
        return problems;
    }

    /* ------------------------------ helpers ------------------------------ */

    /** Splits on commas that are not inside parentheses — enum lists contain commas. */
    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }

    /** Reads the type, stopping at the first constraint keyword. */
    private static String typeOf(String[] tokens, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < tokens.length; i++) {
            if (CONSTRAINT_STARTS.contains(tokens[i].toLowerCase(Locale.ROOT))) break;
            if (sb.length() > 0) sb.append(' ');
            sb.append(tokens[i]);
        }
        return normalise(sb.toString());
    }

    /** Aliases that mean the same thing to the driver. */
    private static String normalise(String type) {
        String t = type.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        t = t.replaceAll(",$", "");
        t = t.replace("numeric(", "decimal(");
        t = t.replaceAll("^int\\b", "integer");
        t = t.replace("timestamp(6) with time zone", "timestamp with time zone");
        t = t.replace("timestamptz", "timestamp with time zone");
        t = t.replace("bit(1)", "bit");
        return t;
    }
}
