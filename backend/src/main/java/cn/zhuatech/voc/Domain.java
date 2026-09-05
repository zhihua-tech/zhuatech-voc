/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.voc;
import org.springframework.stereotype.Component;
import java.util.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import static cn.zhuatech.voc.Model.*;
import static cn.zhuatech.voc.Engine.*;
@Component public class Domain {
 static Map<String,Object> copy(Row r){return new LinkedHashMap<>(r.data());}
 static BigDecimal n(Row r,String k){return num(r.data(),k);}
 static BigDecimal z(Map<String,Object>d,String k){return d.containsKey(k)?num(d,k):BigDecimal.ZERO;}
 static String t(Row r,String k){return txt(r.data(),k);}
 static List<Row> linked(Engine e,User u,String module,String key,String id){return e.all(u,module).stream().filter(r->t(r,key).equals(id)).toList();}
 static void unique(Engine e,User u,String module,Map<String,Object>d,String key){require(e.all(u,module).stream().noneMatch(r->t(r,key).equalsIgnoreCase(txt(d,key))),"重复的"+key);}
 static void dates(Map<String,Object>d,String from,String to){require(!date(d,to).isBefore(date(d,from)),"结束日期不能早于开始日期");}
 static void change(Engine e,User u,Row row,String state,Map<String,Object>d,String note){e.save(u,row,state,d,"LINKED",note);}
 public void edit(Engine e,User u,Row r,Map<String,Object>d){
  if(r.module().equals("readings")){require(e.ref(u,r.data(),"job","jobs").state().equals("RUNNING")&&txt(d,"job").equals(t(r,"job")),"仅进行中的任务可以修改测量值，且不得迁移任务");require(linked(e,u,"readings","job",t(r,"job")).stream().noneMatch(x->!x.id().equals(r.id())&&t(x,"point").equals(txt(d,"point"))),"测量点编号重复");return;}
  if(r.module().equals("versions")){require(e.ref(u,r.data(),"artwork","artworks").state().equals("DRAFT"),"已送审稿件不可修改");require(txt(d,"artwork").equals(t(r,"artwork"))&&num(d,"revision").compareTo(n(r,"revision"))==0,"版本不能迁移任务或改写版本号");return;}
  for(var m:e.spec().modules())for(Row other:e.all(u,m.key()))if(!other.id().equals(r.id())&&other.data().values().stream().anyMatch(v->r.id().equals(v)))throw new Failure(409,"资料已有下游引用，请新建版本而不是改写历史");
  var fields=e.spec().module(r.module()).fields().stream().map(Field::key).toList();
  r.data().forEach((k,v)->{if(!fields.contains(k))d.put(k,v);});
  if(d.containsKey("start")&&d.containsKey("end"))dates(d,"start","end");
  if(d.containsKey("from")&&d.containsKey("to"))dates(d,"from","to");
  for(String key:List.of("serial","sku","invoice","invoiceNo","lockNo"))if(d.containsKey(key))require(e.all(u,r.module()).stream().noneMatch(x->!x.id().equals(r.id())&&t(x,key).equalsIgnoreCase(txt(d,key))),"重复唯一业务标识: "+key);
  if(d.containsKey("bonusRate"))require(num(d,"bonusRate").compareTo(num(d,"baseRate"))>=0,"达档返利率不能低于基础返利率");
  if(d.containsKey("lifeLimit"))require(num(d,"serviceEvery").compareTo(num(d,"lifeLimit"))<=0,"保养间隔不能大于寿命");
  if(d.containsKey("defects"))require(num(d,"defects").compareTo(num(d,"shots"))<=0,"不良数不能超过生产次数");
  if(d.containsKey("nps"))require(num(d,"nps").compareTo(BigDecimal.TEN)<=0&&num(d,"csat").compareTo(new BigDecimal("5"))<=0,"评价分数超出范围");
  if(d.containsKey("oxygenMin"))require(num(d,"oxygenMin").compareTo(num(d,"oxygenMax"))<0,"氧气下限须小于上限");
  if(r.module().equals("invoices"))require(e.all(u,"invoices").stream().noneMatch(x->!x.id().equals(r.id())&&t(x,"shipment").equals(txt(d,"shipment"))),"运单已关联结算账单");
  if(r.module().equals("sales")){Row program=e.ref(u,d,"program","programs");require(program.state().equals("ACTIVE")&&!date(d,"soldAt").isBefore(date(program.data(),"start"))&&!date(d,"soldAt").isAfter(date(program.data(),"end")),"协议状态或销售日期无效");}
  if(r.module().equals("jobs")){Row instrument=e.ref(u,d,"instrument","instruments"),standard=e.ref(u,d,"standard","standards");require(!instrument.state().equals("RETIRED")&&t(instrument,"unit").equals(t(standard,"unit")),"器具状态或计量单位无效");require(!date(d,"performedAt").isAfter(LocalDate.now()),"不能记录未来校准");}
  if(r.module().equals("permits")){require(ChronoUnit.DAYS.between(date(d,"start"),date(d,"end"))<=7,"许可最长七天");require(t(e.ref(u,d,"isolation","isolations"),"location").equals(txt(d,"location")),"隔离区域不匹配");}
  if(r.module().equals("responses")){require(e.all(u,"responses").stream().noneMatch(x->!x.id().equals(r.id())&&t(x,"survey").equals(txt(d,"survey"))&&t(x,"customer").equals(txt(d,"customer"))),"客户已存在该问卷反馈");require(t(e.ref(u,d,"customer","customers"),"consent").equals("YES"),"客户未允许反馈邀请");}
  if(r.module().equals("products")){String barcode=txt(d,"barcode");require(barcode.matches("\\d{13}"),"条码须为 EAN-13");int sum=0;for(int x=0;x<12;x++)sum+=(barcode.charAt(x)-'0')*(x%2==0?1:3);require((10-sum%10)%10==barcode.charAt(12)-'0',"EAN-13 校验位不正确");}

 }
 public Map<String,Object> metrics(Engine e,User u){
  var out=new LinkedHashMap<String,Object>();var feedback=e.all(u,"responses").stream().filter(r->r.state().equals("SUBMITTED")).toList();long promoters=feedback.stream().filter(r->n(r,"nps").intValue()>=9).count(),detractors=feedback.stream().filter(r->n(r,"nps").intValue()<=6).count();out.put("有效反馈数",feedback.size());out.put("NPS",feedback.isEmpty()?"暂无样本":money(BigDecimal.valueOf((promoters-detractors)*100).divide(BigDecimal.valueOf(feedback.size()),4,RoundingMode.HALF_UP)));out.put("平均满意度",feedback.isEmpty()?"暂无样本":money(feedback.stream().map(r->n(r,"csat")).reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(feedback.size()),4,RoundingMode.HALF_UP)));out.put("逾期回访",e.all(u,"cases").stream().filter(r->!r.state().equals("CLOSED")&&date(r.data(),"dueDate").isBefore(LocalDate.now())).count());;return out;
 }
 public void create(Engine e,User u,String module,Map<String,Object>d){switch(module){case "surveys" -> dates(d,"start","end");
case "responses" -> {require(num(d,"nps").compareTo(BigDecimal.TEN)<=0&&num(d,"csat").compareTo(new BigDecimal("5"))<=0,"NPS 为 0—10，满意度为 1—5");Row customer=e.ref(u,d,"customer","customers");require(t(customer,"consent").equals("YES"),"客户未允许邀请反馈");require(e.all(u,module).stream().noneMatch(x->t(x,"survey").equals(txt(d,"survey"))&&t(x,"customer").equals(txt(d,"customer"))),"客户已存在该问卷反馈");} default -> {} }}
 public String action(Engine e,User u,Row r,String action,Map<String,Object>i,Map<String,Object>d){
  String k=r.module()+"."+action;switch(k){
case "surveys.publish" -> require(!date(d,"end").isBefore(LocalDate.now()),"调查已过期");
case "responses.submit" -> {
 Row survey=e.ref(u,d,"survey","surveys");require(survey.state().equals("ACTIVE"),"问卷尚未发布或已关闭");LocalDate now=LocalDate.now();require(!now.isBefore(date(survey.data(),"start"))&&!now.isAfter(date(survey.data(),"end")),"不在调查有效期");require(t(e.ref(u,d,"customer","customers"),"consent").equals("YES"),"客户已取消邀请许可");
 d.put("submittedAt",Instant.now().toString());if(z(d,"nps").compareTo(n(survey,"lowScore"))<=0)e.ledger(u,"cases","OPEN",Map.of("response",r.id(),"customer",txt(d,"customer"),"dueDate",now.plusDays(n(survey,"slaDays").longValueExact()).toString()));
}
case "responses.withdraw" -> {d.put("withdrawReason",txt(i,"reason"));d.put("comment","客户已撤回反馈，不再纳入统计");}
case "cases.assign" -> d.putAll(i);
case "cases.resolve" -> {d.putAll(i);d.put("resolvedBy",u.id());}
case "cases.close" -> {require(!txt(d,"resolvedBy").equals(u.id()),"处理人与结案复核人须分离");require(linked(e,u,"improvements","case",r.id()).stream().allMatch(x->x.state().equals("CLOSED")),"关联改进行动尚未验收");d.putAll(i);}
case "cases.reopen","improvements.complete","improvements.verify" -> d.putAll(i);
 default -> {} }return null;
 }
}
