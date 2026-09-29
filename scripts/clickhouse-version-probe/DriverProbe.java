import java.sql.*;
public class DriverProbe {
  public static void main(String[] a) throws Exception {
    Class.forName("com.clickhouse.jdbc.ClickHouseDriver");
    String url = "jdbc:clickhouse://127.0.0.1:8124/default?compress=0&max_final_threads=1";
    try (Connection c = DriverManager.getConnection(url, "default", "")) {
      System.out.println("  [OK] 连接成功  服务端=" + c.getMetaData().getDatabaseProductVersion());
      try (PreparedStatement ps = c.prepareStatement(
             "SELECT count() FROM default.hmp_agentobs_observations_all FINAL WHERE service_name = ?")) {
        ps.setString(1, "svc-a");
        try (ResultSet rs = ps.executeQuery()) { rs.next();
          System.out.println("  [OK] 参数绑定查询  结果=" + rs.getLong(1)); }
      }
      try (PreparedStatement ps = c.prepareStatement(
             "SELECT toString(trace_id), JSONLength(tool_definitions) FROM default.hmp_agentobs_observations_all FINAL WHERE trace_id = ? LIMIT 1")) {
        ps.setString(1, "0123456789abcdef0123456789abcdef");
        try (ResultSet rs = ps.executeQuery()) { rs.next();
          System.out.println("  [OK] JSONLength 经驱动  结果=" + rs.getInt(2)); }
      }
    } catch (Throwable t) {
      System.out.println("  [FAIL] " + t.getClass().getSimpleName() + ": "
        + String.valueOf(t.getMessage()).substring(0, Math.min(150, String.valueOf(t.getMessage()).length())));
    }
  }
}
