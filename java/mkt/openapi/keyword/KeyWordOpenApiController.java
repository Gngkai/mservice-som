package mkt.openapi.keyword;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sun.istack.NotNull;
import common.fnd.FndError;
import common.sal.util.SalUtil;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDBizException;
import kd.bos.openapi.common.custom.annotation.ApiController;
import kd.bos.openapi.common.custom.annotation.ApiMapping;
import kd.bos.openapi.common.custom.annotation.ApiParam;
import kd.bos.openapi.common.custom.annotation.ApiPostMapping;
import kd.bos.openapi.common.result.CustomApiResult;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.util.ExceptionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.validation.Valid;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author aosom
 * @version SKU关键词库开放接口
 */
@ApiController(value = "MMS/DATA", desc = "营销数据库")
@ApiMapping(value = "/mms/openapi/KeyWord")
public class KeyWordOpenApiController implements Serializable {
    private static final String[] SELECTFIELDS =
        new String[] {"aos_orgid.number", "aos_category1", "aos_category2", "aos_category3", "aos_itemname",
            "aos_itemid.number", "aos_new", "aos_entryentity.aos_mainvoc", "aos_entryentity.aos_sort",
            "aos_entryentity.aos_search", "aos_entryentity.aos_apply", "aos_entryentity.aos_attribute",
            "aos_entryentity.aos_employ", "aos_entryentity.aos_promote", "aos_entryentity.aos_remake",
            "aos_entryentity1.aos_pr_keyword", "aos_entryentity1.aos_pr_sort", "aos_entryentity1.aos_pr_search",
            "aos_entryentity1.aos_pr_employ", "aos_entryentity1.aos_pr_state", "aos_entryentity1.aos_pr_lable",
            "aos_entryentity2.aos_se_keyword", "aos_linentity_gw.aos_gw_keyword", "aos_linentity_gw.aos_gw_search"};

    @ApiPostMapping(value = "/KeyWord", desc = "SKU关键词库同步")
    public CustomApiResult<JSONObject>
        keyWordSync(@Valid @NotNull @ApiParam(value = "JSON数据", required = true) String jsonData) {
        try {
            ObjectNode json = getJson(jsonData);
            Iterable<String> parameterS = json::fieldNames;
            ArrayList<QFilter> qfList = new ArrayList<>();
            for (String parameter : parameterS) {
                qfList.add(new QFilter(parameter, QCP.equals, json.get(parameter).asText()));
            }
            int size = qfList.size();
            QFilter[] qfArray = qfList.toArray(new QFilter[size]);
            DynamicObjectCollection keyWordS =
                QueryServiceHelper.query("aos_mkt_keyword", String.join(",", SELECTFIELDS), qfArray);
            JSONObject returnData = new JSONObject();
            JSONArray data = new JSONArray();
            for (DynamicObject keyWord : keyWordS) {
                JSONObject detail = new JSONObject();
                for (String key : SELECTFIELDS) {
                    detail.put(key, keyWord.get(key));
                }
                data.add(detail);
            }
            returnData.put("details", data);
            return CustomApiResult.success(returnData);
        } catch (KDBizException kdBizException) {
            FndError.sendSOM("关键词库同步失败:" + SalUtil.getExceptionStr(kdBizException));
            return CustomApiResult.fail("1000001", kdBizException.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            FndError.sendSOM("关键词库同步失败:" + SalUtil.getExceptionStr(e));
            String exceptionStackTraceMessage = ExceptionUtils.getExceptionStackTraceMessage(e);
            return CustomApiResult.fail("1000002", exceptionStackTraceMessage);
        }
    }

    private ObjectNode getJson(String jsonString) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            return (ObjectNode)jsonNode;
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new Exception();
    }
}