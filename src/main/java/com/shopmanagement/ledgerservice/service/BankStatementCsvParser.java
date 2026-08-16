package com.shopmanagement.ledgerservice.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses common Indian bank / cash-book CSV exports. File import only — not a live bank API.
 */
public final class BankStatementCsvParser {

    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MMM/yyyy").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yy").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM yyyy").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yyyy").toFormatter(Locale.ENGLISH));

    private BankStatementCsvParser() {
    }

    public record ParsedLine(
            LocalDate txnDate,
            LocalDate valueDate,
            String description,
            String reference,
            double debit,
            double credit,
            Double balance) {
    }

    public record ParseResult(List<ParsedLine> lines, int skipped, List<String> warnings) {
    }

    public static ParseResult parse(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("File is empty");
        }
        String name = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            throw new IllegalArgumentException("Excel is not supported — export the statement as CSV");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return parseCsv(text);
    }

    static ParseResult parseCsv(String text) {
        List<String> warnings = new ArrayList<>();
        List<List<String>> table = readCsv(text);
        if (table.isEmpty()) {
            return new ParseResult(List.of(), 0, List.of("No rows"));
        }
        int headerRow = findHeaderRow(table);
        if (headerRow < 0) {
            throw new IllegalArgumentException(
                    "No statement header found. Expected columns such as date, narration/description, debit, credit, amount, balance, cheque/ref.");
        }
        Map<String, Integer> cols = headerMap(table.get(headerRow));
        List<ParsedLine> lines = new ArrayList<>();
        int skipped = 0;
        for (int r = headerRow + 1; r < table.size(); r++) {
            List<String> row = table.get(r);
            ParsedLine line = parseRow(row, cols);
            if (line == null) {
                skipped++;
                continue;
            }
            lines.add(line);
        }
        if (headerRow > 0) {
            warnings.add("Skipped " + headerRow + " metadata row(s) before the header");
        }
        return new ParseResult(List.copyOf(lines), skipped, List.copyOf(warnings));
    }

    private static ParsedLine parseRow(List<String> row, Map<String, Integer> cols) {
        LocalDate txnDate = parseDate(col(row, cols, "date", "txndate", "transactiondate", "trandate"));
        LocalDate valueDate = parseDate(col(row, cols, "valuedate", "valuedt"));
        if (txnDate == null) {
            txnDate = valueDate;
        }
        if (txnDate == null) {
            return null;
        }
        String description = firstNonBlank(
                col(row, cols, "narration", "description", "particulars", "remarks", "transactionremarks", "details"));
        String reference = firstNonBlank(col(row, cols,
                "chqrefno", "refnochequeno", "chequenumber", "chequeno", "chqno", "reference", "refno", "cheque",
                "chqref", "instrument"));
        double debit = nz(parseAmount(col(row, cols,
                "withdrawalamt", "withdrawalamountinr", "withdrawalamount", "withdrawal", "debit", "debitamount",
                "dramount", "withdrawalamt.")));
        double credit = nz(parseAmount(col(row, cols,
                "depositamt", "depositamountinr", "depositamount", "deposit", "credit", "creditamount",
                "cramount", "depositamt.")));
        Double amount = parseAmount(col(row, cols, "amount", "txnamount", "transactionamount"));
        String drCr = firstNonBlank(col(row, cols, "drcr", "dcr", "type", "txntype", "withdrawaldeposit", "crdr"));
        if (debit <= 0.009 && credit <= 0.009 && amount != null) {
            Direction dir = direction(drCr, amount);
            if (dir == Direction.DEBIT) {
                debit = Math.abs(amount);
            } else if (dir == Direction.CREDIT) {
                credit = Math.abs(amount);
            } else if (amount < 0) {
                debit = Math.abs(amount);
            } else if (amount > 0) {
                credit = amount;
            }
        }
        Double balance = parseAmount(col(row, cols, "closingbalance", "balance", "runningbalance", "balanceinr"));
        if (debit <= 0.009 && credit <= 0.009) {
            return null;
        }
        return new ParsedLine(
                txnDate,
                valueDate,
                clip(description, 500),
                clip(reference, 80),
                round2(debit),
                round2(credit),
                balance != null ? round2(balance) : null);
    }

    private enum Direction { DEBIT, CREDIT, UNKNOWN }

    private static Direction direction(String raw, double amount) {
        if (raw == null || raw.isBlank()) {
            return Direction.UNKNOWN;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("dr") || t.contains("withdraw") || t.contains("debit") || t.equals("w")) {
            return Direction.DEBIT;
        }
        if (t.startsWith("cr") || t.contains("deposit") || t.contains("credit") || t.equals("d")) {
            return Direction.CREDIT;
        }
        if (amount < 0) {
            return Direction.DEBIT;
        }
        return Direction.UNKNOWN;
    }

    private static int findHeaderRow(List<List<String>> table) {
        int limit = Math.min(table.size(), 25);
        for (int i = 0; i < limit; i++) {
            Map<String, Integer> cols = headerMap(table.get(i));
            boolean hasDate = hasAny(cols, "date", "txndate", "transactiondate", "trandate", "valuedate", "valuedt");
            boolean hasMoney = hasAny(cols,
                    "debit", "credit", "withdrawal", "withdrawalamt", "deposit", "depositamt", "amount",
                    "txnamount", "debitamount", "creditamount");
            if (hasDate && hasMoney) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, Integer> headerMap(List<String> header) {
        Map<String, Integer> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = normalizeHeader(header.get(i));
            if (!key.isEmpty() && !map.containsKey(key)) {
                map.put(key, i);
            }
        }
        return map;
    }

    static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT)
                .replace('\u00a0', ' ')
                .replaceAll("[^a-z0-9]+", "");
    }

    private static boolean hasAny(Map<String, Integer> cols, String... keys) {
        for (String key : keys) {
            if (cols.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static String col(List<String> row, Map<String, Integer> cols, String... keys) {
        for (String key : keys) {
            Integer idx = cols.get(key);
            if (idx != null && idx >= 0 && idx < row.size()) {
                String v = row.get(idx);
                if (v != null && !v.isBlank()) {
                    return v.trim();
                }
            }
        }
        return "";
    }

    static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    static Double parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim()
                .replace("₹", "")
                .replace("Rs.", "")
                .replace("Rs", "")
                .replace("INR", "")
                .replace(",", "")
                .replace(" ", "");
        if (s.isEmpty() || "-".equals(s) || "—".equals(s)) {
            return null;
        }
        boolean parenNeg = s.startsWith("(") && s.endsWith(")");
        if (parenNeg) {
            s = s.substring(1, s.length() - 1);
        }
        try {
            double v = Double.parseDouble(s);
            return parenNeg ? -Math.abs(v) : v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static List<List<String>> readCsv(String text) {
        String src = text.replace("\r\n", "\n").replace('\r', '\n');
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < src.length() && src.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                current.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n') {
                current.add(cell.toString());
                cell.setLength(0);
                if (current.stream().anyMatch(s -> s != null && !s.isBlank())) {
                    rows.add(current);
                }
                current = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        current.add(cell.toString());
        if (current.stream().anyMatch(s -> s != null && !s.isBlank())) {
            rows.add(current);
        }
        return rows;
    }

    private static String firstNonBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String t = value.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static double nz(Double value) {
        return value == null ? 0.0 : Math.abs(value);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
