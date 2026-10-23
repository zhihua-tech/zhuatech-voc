/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.voc.operations;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * NPS分类一致性与小样本提示：真实定点核算、只读建议、不自动审批或过账。上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@Service
public class OperationsReviewService {
  private final Map<String, Object> schema;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @SuppressWarnings("unchecked")
  public OperationsReviewService() {
    try (var in = getClass().getResourceAsStream("/operations/schema.json")) {
      if (in == null) throw new IllegalStateException("缺少业务核算契约");
      schema = new JsonMapper().readValue(in, Map.class);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("规则读取失败", ex);
    }
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public Map<String, Object> schema() {
    return schema;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public Map<String, Object> request(String raw) {
    return execute(OpsInput.parse(raw));
  }

  /**
   * 全批验证成功后才返回结果，不丢弃失败记录。上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
   * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> execute(Map<String, Object> input) {
    if (input == null) throw new IllegalArgumentException("请求不能为空");
    OpsInput.inspect(input, 0, new int[] {0, 0});
    OpsInput.keys(input, List.of("asOf", "records"));
    LocalDate asOf = OpsInput.date(input.get("asOf"));
    if (!(input.get("records") instanceof List<?> records)
        || records.isEmpty()
        || records.size() > 500) throw new IllegalArgumentException("records须为1至500条记录");
    List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");
    var names = fields.stream().map(f -> f.get("key").toString()).toList();
    Set<String> ids = new HashSet<>();
    List<Map<String, Object>> normalized = new ArrayList<>();
    for (int index = 0; index < records.size(); index++) {
      try {
        if (!(records.get(index) instanceof Map<?, ?> raw))
          throw new IllegalArgumentException("记录须为对象");
        OpsInput.keys(raw, names);
        Map<String, Object> row = new LinkedHashMap<>();
        for (var f : fields) {
          String key = f.get("key").toString(), type = f.get("type").toString();
          Object value = raw.get(key);
          try {
            switch (type) {
              case "ID" -> row.put(key, OpsInput.id(value));
              case "TEXT" -> row.put(key, OpsInput.text(value));
              case "DATE" -> row.put(key, OpsInput.date(value).toString());
              case "ENUM" -> {
                String s = OpsInput.text(value);
                if (!((List<?>) f.get("options")).contains(s))
                  throw new IllegalArgumentException("不在允许选项内");
                row.put(key, s);
              }
              case "NUMBER" -> {
                BigDecimal n = OpsInput.decimal(value);
                if (n.compareTo(new BigDecimal(f.get("min").toString())) < 0
                    || n.compareTo(new BigDecimal(f.get("max").toString())) > 0
                    || n.stripTrailingZeros().scale() > ((Number) f.get("scale")).intValue())
                  throw new IllegalArgumentException("数值范围或小数位不正确");
                row.put(key, n);
              }
              default -> throw new IllegalStateException("未知字段类型");
            }
          } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(key + "：" + ex.getMessage());
          }
        }
        String id = row.get("id").toString();
        if (!ids.add(id.toLowerCase(Locale.ROOT)))
          throw new IllegalArgumentException("批次编号重复（不区分大小写）");
        normalized.add(row);
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("第" + (index + 1) + "条记录：" + ex.getMessage());
      }
    }
    List<Map<String, Object>> results = new ArrayList<>();
    for (var row : normalized) {
      try {
        results.add(evaluate(row, asOf));
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("记录" + row.get("id") + "：" + ex.getMessage());
      }
    }
    Map<String, Integer> rank = Map.of("HOLD", 0, "REVIEW", 1, "PASS", 2);
    results.sort(
        Comparator.comparingInt((Map<String, Object> row) -> rank.get(row.get("decision")))
            .thenComparing(row -> row.get("id").toString()));
    return Map.of(
        "asOf",
        asOf.toString(),
        "recordCount",
        results.size(),
        "holdCount",
        results.stream().filter(r -> r.get("decision").equals("HOLD")).count(),
        "reviewCount",
        results.stream().filter(r -> r.get("decision").equals("REVIEW")).count(),
        "passCount",
        results.stream().filter(r -> r.get("decision").equals("PASS")).count(),
        "results",
        results,
        "automaticWrite",
        false);
  }

  /**
   * 用互斥的推荐者、中立者和贬损者数量计算NPS及响应率，样本量不足时明确标记，不做情绪模型推断。 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
   * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  private Map<String, Object> evaluate(Map<String, Object> v, LocalDate asOf) {
    var total = n(v, "promoters").add(n(v, "passives")).add(n(v, "detractors"));
    require(total.compareTo(n(v, "invited")) <= 0, "互斥响应数量不能超过邀请人数");
    boolean has = total.signum() > 0;
    var score =
        has ? ratio(n(v, "promoters").subtract(n(v, "detractors")), total) : BigDecimal.ZERO;
    return result(
        v,
        total.compareTo(n(v, "minimumResponses")) < 0 ? "REVIEW" : "PASS",
        "分类须已按调查问卷确定且一人一次响应；无响应显示N/A，不据此作统计显著性、客群代表性或人事判断",
        Map.of(
            "responses",
            total,
            "responsePct",
            ratio(total, n(v, "invited")),
            "nps",
            has ? score : "N/A",
            "responseGap",
            n(v, "minimumResponses").subtract(total).max(BigDecimal.ZERO)));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static BigDecimal n(Map<String, Object> v, String key) {
    return (BigDecimal) v.get(key);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static String s(Map<String, Object> v, String key) {
    return v.get(key).toString();
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static LocalDate d(Map<String, Object> v, String key) {
    return OpsInput.date(v.get(key));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static BigDecimal money(BigDecimal v) {
    return v.setScale(2, RoundingMode.HALF_UP);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static BigDecimal ratio(BigDecimal a, BigDecimal b) {
    return b.signum() == 0
        ? BigDecimal.ZERO
        : a.multiply(new BigDecimal("100")).divide(b, 4, RoundingMode.HALF_UP);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static void require(boolean ok, String message) {
    if (!ok) throw new IllegalArgumentException(message);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static Map<String, Object> result(
      Map<String, Object> v, String decision, String reason, Map<String, Object> metrics) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("id", s(v, "id"));
    r.put("decision", decision);
    r.put("reason", reason);
    r.putAll(metrics);
    return r;
  }
}
