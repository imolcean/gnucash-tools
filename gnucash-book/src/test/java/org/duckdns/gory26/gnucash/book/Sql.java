package org.duckdns.gory26.gnucash.book;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Raw SQL against a test database, with positional {@code ?} parameters, on top of {@link Jdbc}. */
final class Sql {

    private final Jdbc jdbc;

    Sql(DataSource dataSource) {
        this.jdbc = Jdbc.over(dataSource);
    }

    int update(String sql, Object... params) {
        return jdbc.update(sql, params);
    }

    /** The first column of the only row. */
    <T> T one(Class<T> type, String sql, Object... params) {
        return jdbc.single(sql, rs -> first(rs, type), params);
    }

    /** The first column of every row. */
    <T> List<T> list(Class<T> type, String sql, Object... params) {
        return jdbc.list(sql, rs -> first(rs, type), params);
    }

    /** The only row, by column label. */
    Map<String, Object> row(String sql, Object... params) {
        return jdbc.single(sql, Sql::toMap, params);
    }

    /** Every row, by column label. */
    List<Map<String, Object>> rows(String sql, Object... params) {
        return jdbc.list(sql, Sql::toMap, params);
    }

    /** {@code COUNT(*)} is a bigint, which the driver will not hand out as an Integer by itself. */
    private static <T> T first(ResultSet rs, Class<T> type) throws SQLException {
        return type == Integer.class ? type.cast(rs.getInt(1)) : rs.getObject(1, type);
    }

    private static Map<String, Object> toMap(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for(int i = 1; i <= meta.getColumnCount(); i++) {
            row.put(meta.getColumnLabel(i), rs.getObject(i));
        }
        return row;
    }
}
