package com.example.backend.config.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Разбор полей из {@link CheckRuleDefinition#params()}. */
public final class JsonRuleParams {

    private JsonRuleParams() {
    }

    public static String stringValue(Map<String, Object> m, String key, String defaultValue) {
        if (m == null) {
            return defaultValue;
        }
        Object v = m.get(key);
        if (v == null) {
            return defaultValue;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? defaultValue : s;
    }

    @SuppressWarnings("unchecked")
    public static List<String> stringList(Map<String, Object> m, String key, List<String> defaultValue) {
        if (m == null) {
            return defaultValue;
        }
        Object v = m.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o).trim());
                }
            }
            return out.isEmpty() ? defaultValue : List.copyOf(out);
        }
        return defaultValue;
    }

    public static int intValue(Map<String, Object> m, String key, int defaultValue) {
        if (m == null) {
            return defaultValue;
        }
        Object v = m.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            return Integer.parseInt(s.trim());
        }
        return defaultValue;
    }

    public static double doubleValue(Map<String, Object> m, String key, double defaultValue) {
        if (m == null) {
            return defaultValue;
        }
        Object v = m.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            return Double.parseDouble(s.trim().replace(',', '.'));
        }
        return defaultValue;
    }
}
