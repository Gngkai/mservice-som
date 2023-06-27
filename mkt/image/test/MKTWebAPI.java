package mkt.image.test;

import java.util.Map;

import kd.bos.bill.IBillWebApiPlugin;
import kd.bos.entity.api.ApiResult;

public class MKTWebAPI implements IBillWebApiPlugin {
	@Override
	public ApiResult doCustomService(Map<String, Object> params) {
		ApiResult apiResult = new ApiResult();

		//JSONObject jsonObject = JSONObject.fromObject(params);
		// jsonObject.getString(key);
		
		//kd.isc.iscb.platform.core.api.OpenApiVersion2Dispatcher

		apiResult.setSuccess(true);
		apiResult.setSuccess(false);
		apiResult.setMessage("保存失败");

		return apiResult;
	}

}
