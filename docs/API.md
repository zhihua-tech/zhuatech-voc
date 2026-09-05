# REST API 使用说明

上海如静知华信息科技有限公司 · https://www.zhuatech.cn/

除 `GET /api/health` 与 `POST /api/auth/login` 外，接口均需 `Authorization: Bearer <token>`。写单据接口需要 `Idempotency-Key`，长度 8—80，建议 UUID。同一键重试必须保持请求内容一致。

| 方法与路径 | 用途 |
|---|---|
| POST /api/auth/login | username、password 登录，返回 token、user |
| POST /api/auth/logout | 注销当前会话 |
| GET /api/catalog | 模块字段、动作、角色与状态定义 |
| GET /api/dashboard | 企业范围业务汇总与最近操作 |
| GET /api/records?module=...&q=...&state=...&page=1&size=20 | 查询、筛选与分页，size 最大 100 |
| POST /api/records/{module} | 新建，body 为 code、data |
| PUT /api/records/{id} | 编辑草稿或尚未被引用的主数据，body 为 version、data |
| GET /api/records/{id} | 单据明细及派生字段 |
| POST /api/records/{id}/actions/{action} | 执行业务，body 为 version、data、remark |
| GET /api/records/{id}/history | 单据所有变更快照 |
| GET /api/export/{module} | CSV 导出，防止表格公式注入 |
| POST /api/attachments/{recordId} | multipart 的 file 字段 |
| GET /api/attachments/{recordId} | 附件摘要列表 |
| GET /api/attachments/download/{attachmentId} | 鉴权附件下载 |
| GET/POST /api/admin/users | 管理员查看或新建账号 |
| PATCH /api/admin/users/{id} | 调整 role、active 或 password，撤销旧会话 |
| GET /api/admin/audit | 最近 200 条业务及管理审计 |

关联字段必须填写当前企业下真实记录的 UUID，不能传其他企业 ID。版本来自最近一次 GET；出现 409 应重新读取并核对业务，不能盲目重试旧版本。400 表示输入不符合类型/范围，401 表示未登录，403 表示角色不允许，404 表示记录不可见，409 表示业务冲突，429 表示登录暂时锁定。

## 动作字典

### 满意度调查 `surveys`

- `publish`：审批发布；DRAFT → ACTIVE；最低角色 REVIEWER；禁止创建者自审。业务输入：无，但必须填写 remark。
- `close`：结束调查；ACTIVE → CLOSED；最低角色 REVIEWER。业务输入：无，但必须填写 remark。

### 客户反馈 `responses`

- `submit`：确认录入；DRAFT → SUBMITTED；最低角色 OPERATOR。业务输入：无，但必须填写 remark。
- `withdraw`：撤回并停止统计；SUBMITTED → WITHDRAWN；最低角色 REVIEWER；禁止创建者自审。业务输入：reason（撤回依据）。

### 回访工单 `cases`

- `assign`：分派回访；OPEN → ASSIGNED；最低角色 REVIEWER。业务输入：owner（回访负责人）。
- `resolve`：登记回访结果；ASSIGNED → RESOLVED；最低角色 OPERATOR。业务输入：resolution（解决措施与回访记录）。
- `close`：复核结案；RESOLVED → CLOSED；最低角色 REVIEWER；禁止创建者自审。业务输入：verification（复核依据）。
- `reopen`：重新处理；RESOLVED / CLOSED → ASSIGNED；最低角色 REVIEWER。业务输入：reason（重开原因）。

### 改进行动 `improvements`

- `start`：落实改进；DRAFT → IN_PROGRESS；最低角色 OPERATOR。业务输入：无，但必须填写 remark。
- `complete`：提交改善结果；IN_PROGRESS → REVIEW；最低角色 OPERATOR。业务输入：result（措施与效果）。
- `verify`：效果验收；REVIEW → CLOSED；最低角色 REVIEWER；禁止创建者自审。业务输入：evidence（验收证据）。

资金类动作仅保存企业确认的结算凭证，并不连接支付机构。系统不会自动读取或上传外部账户数据。
