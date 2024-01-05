package mkt.openapi.ListingReq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sun.istack.NotNull;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.IOperateInfo;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.openapi.common.custom.annotation.ApiController;
import kd.bos.openapi.common.custom.annotation.ApiMapping;
import kd.bos.openapi.common.custom.annotation.ApiParam;
import kd.bos.openapi.common.custom.annotation.ApiPostMapping;
import kd.bos.openapi.common.result.CustomApiResult;
import kd.bos.orm.ORM;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.threads.ThreadPool;
import kd.bos.threads.ThreadPools;


import javax.validation.Valid;
import java.io.File;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;


/**
 * @author create by gk
 * @date 2022/11/28 13:58
 * @action  OA list优化申请表推送 sst 生成list优化需求表
 */
@ApiController(value = "SOM/REQ",desc = "优化需求表")
@ApiMapping(value = "/mkt/openapi/ListingReq")
@SuppressWarnings("unchecked")
public class ListingReqOpenApiController implements Serializable {
    @ApiPostMapping(
            value = "createReqEntity",
            desc = "根据传入参数创建优化需求表"
    )
    public CustomApiResult<@ApiParam("return") Data> createReqEntity(@Valid @NotNull @ApiParam(value = "JSON数据",required = true)String jsonData){
        String biill = "aos_mkt_listing_req";
        Object parse1 = JSONObject.parse(jsonData);
        List<ListingReqModel> listingReqModels;
        Date date_now = new Date();
        List<Result> list_re = new ArrayList<>();
        int sucCounts = 0,failCounts = 0;
        Data reData = new Data();
        Log log = LogFactory.getLog("ListingReqOpenApiController");
        try {
            listingReqModels = JSON.parseArray(parse1.toString(), ListingReqModel.class);
        }
        catch (Exception e){
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            log.error(sw.toString());
            CustomApiResult<Data> fail = CustomApiResult.fail(null, e.getMessage());
            fail.setData(reData);
            e.printStackTrace();
            return  fail;
        }
        JSONObject js_entitys = new JSONObject();  //记录单据里面的附件
        for (int i = 0; i < listingReqModels.size(); i++) {
            Result re = new Result();
            re.setIndex(i);
            ListingReqModel reqModel = listingReqModels.get(i);
            try {
                if (Cux_Common_Utl.IsNull(reqModel.getAos_org())) {
                    throw new FndError("国别异常");
                }
                String orgid = utils.orgID(reqModel.getAos_org());
                if (Cux_Common_Utl.IsNull(orgid))
                    throw new FndError("国别异常");
                if (Cux_Common_Utl.IsNull(reqModel.getAos_type()))
                    throw new FndError("任务类型为空");
                DynamicObject dy_req = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_req");

                dy_req.set("aos_orgid", orgid);
                dy_req.set("aos_type", reqModel.getAos_type());

                String user = utils.getUser(reqModel.getAos_user());
                if (Cux_Common_Utl.IsNull(user)) {
                    user = Cux_Common_Utl.SYSTEM;
                }
                //组织
                List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(user);
                if (MapList != null) {
                    if (MapList.size() >= 3 && MapList.get(2) != null)
                        dy_req.set("aos_organization1", MapList.get(2).get("id"));
                    if (MapList.size() >= 4 && MapList.get(3) != null)
                        dy_req.set("aos_organization2", MapList.get(3).get("id"));
                }
                dy_req.set("id", ORM.create().genLongId("aos_mkt_listing_req"));
                dy_req.set("aos_requireby", user);//申请人
                dy_req.set("aos_user", user);     //操作人
                dy_req.set("aos_requiredate", date_now);
                dy_req.set("aos_productbill", "OA推送数据");
                dy_req.set("aos_status", "申请人");

                //明细行
                String detail = reqModel.getDetail();
                List<DetailModel> detailModels = JSON.parseArray(detail, DetailModel.class);
                DynamicObjectCollection dyc_ent = dy_req.getDynamicObjectCollection("aos_entryentity");

                JSONObject js_attr = new JSONObject();

                for (DetailModel detailModel : detailModels) {
                    if (Cux_Common_Utl.IsNull(detailModel.getAos_item()))
                        throw new FndError("物料为空");
                    DynamicObject dy_item = utils.getItem(detailModel.getAos_item());
                    DynamicObject dy_entNew = dyc_ent.addNew();
                    dy_entNew.set("aos_itemid", dy_item.getString("id"));
                    dy_entNew.set("aos_require", detailModel.getAos_require());
                    dy_entNew.set("aos_requirepic", detailModel.getAos_requirepic());
                    dy_entNew.set("aos_segment3", dy_item.getString("aos_productno"));
                    dy_entNew.set("aos_itemname", dy_item.getString("name"));
                    dy_entNew.set("aos_orgtext", utils.getOrderCountry(dy_item));
                    dy_entNew.set("aos_broitem", utils.getBroItem(dy_item.getString("number"), dy_item.getString("aos_productno")));
                    String aos_attribute = detailModel.getAos_attribute();

                    js_attr.put(dy_item.getString("number"),aos_attribute);
                }
                OperationResult result = SaveServiceHelper.saveOperate("aos_mkt_listing_req", new DynamicObject[]{dy_req}, OperateOption.create());
                if (!result.isSuccess()) {
                    StringJoiner str_err = new StringJoiner( " ,");
                    str_err.add("保存失败");
                    for (IOperateInfo info : result.getAllErrorOrValidateInfo()) {
                        str_err.add(info.getMessage());
                    }
                    throw new FndError(str_err.toString());
                }
                js_entitys.put(String.valueOf(result.getSuccessPkIds().get(0)),js_attr);

                StringJoiner str_no = new StringJoiner(";");
                result.getBillNos().entrySet().stream().forEach(no->str_no.add(no.getValue() ));
                re.setOAProcessNo(str_no.toString());
                re.setStatus(true);
                sucCounts++;
            }
            catch (FndError fndError){
                fndError.printStackTrace();
                re.setStatus(false);
                re.setErrors(fndError.getErrorMessage());
                log.error(fndError.getErrorMessage());
                failCounts++;
            }
            catch (Exception e){
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                log.error("row: "+i+"   "+sw.toString());
                re.setStatus(false);
                re.setErrors(e.getMessage());
                failCounts++;
            }
            finally {
                list_re.add(re);
            }
        }
        reData.setFailCount(failCounts);
        reData.setSuccessCount(sucCounts);
        reData.setResult(list_re);
        CustomApiResult result = CustomApiResult.success(reData);
        result.setMessage("success");
        ThreadPool threadPool = ThreadPools.newCachedThreadPool("OA推送优化需求表附件赋值"+ LocalDateTime.now());
        threadPool.execute(new attributeAdd(js_entitys),RequestContext.get());
        threadPool.close();
        return result;
    }
    class attributeAdd implements Runnable{
        JSONObject js_entity; //需要赋值附件的单据
        attributeAdd(JSONObject jsData){
            js_entity = jsData;
        }
        @Override
        public void run() {
            Log log = LogFactory.getLog("ListingReqOpenApiController");
            try {
                do_operate();
            }catch (FndError fndError){
                log.error(fndError.getErrorMessage());
                fndError.printStackTrace();
            }
            catch (Exception e){
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                log.error(sw.toString());
                e.printStackTrace();
            }
        }
        public void do_operate(){
            for (Map.Entry<String, Object> entry : js_entity.entrySet()) {
                JSONObject attrs = (JSONObject) entry.getValue();
                DynamicObject dy_req = BusinessDataServiceHelper.loadSingle(entry.getKey(), "aos_mkt_listing_req");
                DynamicObjectCollection dyc_ent = dy_req.getDynamicObjectCollection("aos_entryentity");
                for (DynamicObject dy_ent : dyc_ent) {
                    String itemNumber = dy_ent.getDynamicObject("aos_itemid").getString("number");
                    if (attrs.keySet().contains(itemNumber)){
                        //附件
                        String aos_attribute = attrs.get(itemNumber).toString();
                        List<AttaributeModel> attaributeModels = JSON.parseArray(aos_attribute, AttaributeModel.class);
                        List<File> fileList = new ArrayList<>();
                        for (AttaributeModel attachment : attaributeModels) {
                            String name = attachment.getName();
                            String data = attachment.getData();
                            File filePath = new File("D:\\" + name);
                            boolean b = utils.base64ToFile(data, filePath);
                            if (b) {
                                fileList.add(filePath);
                            }
                            else {
                                throw new FndError(dy_req.getString("billno")+"  "+itemNumber+ "  附件信息错误，无法转换");
                            }
                        }
                        //给单据提上传附件并且赋值
                        utils.addAttachmentForEntry(dy_req, dy_ent, "aos_attribute", fileList);
                    }
                }
                SaveServiceHelper.save(new DynamicObject[]{dy_req});
                dy_req = BusinessDataServiceHelper.loadSingle(entry.getKey(), "aos_mkt_listing_req");
                new mkt.progress.listing.AosMktListingReqBill().aosSubmit(dy_req,"B");  //单据提交
            }
        }
    }
}
