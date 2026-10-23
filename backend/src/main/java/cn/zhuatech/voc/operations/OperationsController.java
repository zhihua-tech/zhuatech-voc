/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.voc.operations;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * 对请求者提交的批次执行只读核算，不查询他人业务数据。上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@RestController("specializedOperationsReviewController")
@RequestMapping("/api/operations-review")
public class OperationsController {
  private final OperationsReviewService service;
  private final cn.zhuatech.voc.Auth auth;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public OperationsController(OperationsReviewService service, cn.zhuatech.voc.Auth auth) {
    this.service = service;
    this.auth = auth;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private void authorize(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    auth.current(authorization);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/schema")
  public ResponseEntity<?> schema(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    authorize(authorization);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("success", true, "data", service.schema()));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/evaluate")
  public ResponseEntity<?> evaluate(
      @RequestBody String raw,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    authorize(authorization);
    try {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.noStore())
          .body(
              Map.of(
                  "success",
                  true,
                  "message",
                  "核算完成；未变更业务记录",
                  "data",
                  OpsInput.safe(service.request(raw))));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest()
          .cacheControl(CacheControl.noStore())
          .body(Map.of("success", false, "message", ex.getMessage()));
    }
  }
}
