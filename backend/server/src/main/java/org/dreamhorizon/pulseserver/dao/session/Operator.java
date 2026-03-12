package org.dreamhorizon.pulseserver.dao.session;

import lombok.Getter;

import java.util.Collection;
import java.util.stream.Collectors;

@Getter
public enum Operator {

    EQ("equals", ValueType.SINGLE) {
        @Override
        public String toSql(String field, Object value) {
            return field + " = " + quoteValue(value);
        }
    },
    NEQ("does not equal", ValueType.SINGLE) {
        @Override
        public String toSql(String field, Object value) {
            return field + " != " + quoteValue(value);
        }
    },
    IN("is one of", ValueType.ARRAY) {
        @Override
        public String toSql(String field, Object value) {
            if (!(value instanceof Collection<?> items)) {
                throw new IllegalArgumentException("IN operator requires a Collection value");
            }
            String list = items.stream()
                    .map(Operator::quoteValue)
                    .collect(Collectors.joining(", "));
            return field + " IN (" + list + ")";
        }
    },
    GT("greater than", ValueType.SINGLE) {
        @Override
        public String toSql(String field, Object value) {
            return field + " > " + numericValue(value);
        }
    },
    LT("less than", ValueType.SINGLE) {
        @Override
        public String toSql(String field, Object value) {
            return field + " < " + numericValue(value);
        }
    },
    GTE("greater than or equal to", ValueType.SINGLE) {
        @Override
        public String toSql(String field, Object value) {
            return field + " >= " + numericValue(value);
        }
    },
    LTE("less than or equal to", ValueType.SINGLE) {
        @Override
        public String toSql(String field, Object value) {
            return field + " <= " + numericValue(value);
        }
    },
    BETWEEN("between", ValueType.RANGE) {
        @Override
        public String toSql(String field, Object value) {
            if (!(value instanceof Collection<?> items) || items.size() != 2) {
                throw new IllegalArgumentException("BETWEEN operator requires a Collection of exactly 2 values");
            }
            Object[] bounds = items.toArray();
            return field + " BETWEEN " + numericValue(bounds[0]) + " AND " + numericValue(bounds[1]);
        }
    },
    EMPTY("is empty", ValueType.NONE) {
        @Override
        public String toSql(String field, Object value) {
            return field + " = ''";
        }
    },
    NOT_EMPTY("is not empty", ValueType.NONE) {
        @Override
        public String toSql(String field, Object value) {
            return field + " != ''";
        }
    },
    IS_NULL("is null", ValueType.NONE) {
        @Override
        public String toSql(String field, Object value) {
            return field + " IS NULL";
        }
    },
    IS_NOT_NULL("is not null", ValueType.NONE) {
        @Override
        public String toSql(String field, Object value) {
            return field + " IS NOT NULL";
        }
    };

    private final String displayName;
    private final ValueType valueType;

    Operator(String displayName, ValueType valueType) {
        this.displayName = displayName;
        this.valueType = valueType;
    }

    public enum ValueType { SINGLE, ARRAY, RANGE, NONE }

    public abstract String toSql(String field, Object value);

    static String quoteValue(Object value) {
        if (value instanceof Number) {
            return value.toString();
        }
        return "'" + escapeString(String.valueOf(value)) + "'";
    }

    static String numericValue(Object value) {
        if (value instanceof Number) {
            return value.toString();
        }
        try {
            return String.valueOf(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected numeric value, got: " + value);
        }
    }

    static String escapeString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
