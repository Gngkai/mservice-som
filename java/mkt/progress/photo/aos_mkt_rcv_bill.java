package mkt.progress.photo;

import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.KDException;
import kd.bos.form.IFormView;
import kd.bos.form.container.Container;
import kd.bos.form.control.Control;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import common.sal.util.QFBuilder;
import mkt.common.MKTCom;
import mkt.common.otel.MmsOtelUtils;
import mkt.progress.ProgressUtil;
import mkt.progress.design3d.aos_mkt_3design_bill;
import mkt.progress.iface.iteminfo;

public class aos_mkt_rcv_bill extends AbstractBillPlugIn implements ItemClickListener {
	public final static String system = Cux_Common_Utl.SYSTEM;
	public final static String AOS_MKT_RCV = "aos_mkt_rcv";
	public final static String AOS_SEALSAMPLE = "aos_sealsample";

	private static final Tracer tracer = MmsOtelUtils.getTracer(aos_mkt_rcv_bill.class, RequestContext.get());

	@Override
	public void itemClick(ItemClickEvent evt) {
		Span span = MmsOtelUtils.getCusMainSpan(tracer, MmsOtelUtils.getMethodPath());
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try (Scope scope = span.makeCurrent()) {
			if ("aos_submit".equals(Control)) {
				DynamicObject dy_main = this.getModel().getDataEntity(true);
				aos_submit(dy_main, "A");// 提交
			} else if ("aos_history".equals(Control))
				aos_history();// 查看历史记录
			else if ("aos_return".equals(Control)) {
				aos_return(this.getModel().getDataEntity(true));
				this.getView().invokeOperation("save");
				this.getView().invokeOperation("refresh");
			} else if ("aos_querysample".equals(Control))
			{
				querySample(this.getView(), this.getView().getModel().getDataEntity().getPkValue());// 查看封样单
			}
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
			MmsOtelUtils.setException(span, fndMessage);
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
			MmsOtelUtils.setException(span, ex);
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 退回 **/
	private static void aos_return(DynamicObject dy_main) throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			// 退回原因
			Object aos_returnreason = dy_main.get("aos_returnreason");
			if (Cux_Common_Utl.IsNull(aos_returnreason))
				throw new FndError("退回原因未填");
			Object aos_orignbill = dy_main.get("aos_orignbill");
			if (Cux_Common_Utl.IsNull(aos_orignbill)) {
				QFilter filter = new QFilter("billno", "=", aos_orignbill);
				DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_photoreq", "aos_follower",
						new QFilter[] { filter });
				if (dy != null) {
					dy_main.set("aos_status", "退回");
					dy_main.set("aos_user", dy.get("aos_follower"));
					return;
				}
			}
			Object aos_vendor = dy_main.get("aos_vendor");
			DynamicObject dy_item = dy_main.getDynamicObject("aos_itemid");
			if (Cux_Common_Utl.IsNull(aos_vendor))
				throw new FndError("供应商为空，无法获取跟单");
			if (dy_item == null)
				throw new FndError("物料为空，获取数据异常");
			// 供应商id
			long verder = iteminfo.QueryVendorIdByName(dy_item.getPkValue(), aos_vendor.toString());
			// 获取跟单
			QFilter filter_id = new QFilter("id", "=", verder);
			DynamicObject dy = QueryServiceHelper.queryOne("bd_supplier", "aos_documentary", new QFilter[] { filter_id });
			if (dy == null)
				throw new FndError("获取跟单失败");
			dy_main.set("aos_status", "退回");
			dy_main.set("aos_user", dy.get("aos_documentary"));
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 打开历史记录 **/
	private void aos_history() throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			FndHistory.OpenHistory(this.getView());
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/**
	 * 查看封样图片
	 * @param iFormView	view
	 * @param fid		pk
	 * @throws FndError
	 */
	public static void querySample(IFormView iFormView, Object fid) throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			// 根据传入的样品入库通知单主键获取 样品入库通知单对象
			boolean exists = QueryServiceHelper.exists(AOS_MKT_RCV, fid);
			if (!exists)
				throw new FndError("封样单不存在");
			DynamicObject aosMktRcv = BusinessDataServiceHelper.loadSingle(fid, AOS_MKT_RCV);
			// 合同号
			String aosPoNumber = aosMktRcv.getString("aos_ponumber");
			// 货号
			DynamicObject aosItemId = aosMktRcv.getDynamicObject("aos_itemid");
			openSample(iFormView,aosItemId.getPkValue(),aosPoNumber);
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	public static void openSample(IFormView iFormView,Object itemid,Object poNumber){
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			// 封样单参数
			Object sampleId = null;
			// 查询是否存在对应封样单
			DynamicObject aosSealSample = QueryServiceHelper.queryOne(AOS_SEALSAMPLE, "id",
					new QFilter("aos_item", QCP.equals, itemid)
							.and("aos_contractnowb", QCP.equals, poNumber).toArray());

			if (FndGlobal.IsNull(aosSealSample)) {
				List<Object> primaryKeys = QueryServiceHelper.queryPrimaryKeys(AOS_SEALSAMPLE,
						new QFilter("aos_item", QCP.equals,itemid).toArray(), "createtime desc", 1);
				if (FndGlobal.IsNotNull(primaryKeys) && primaryKeys.size() > 0)
					sampleId = primaryKeys.get(0);
				else
					throw new FndError("未找到对应封样单!");
			} else {
				sampleId = aosSealSample.get("id");
			}
			FndMsg.debug("sampleId:" + sampleId);
			// 打开封样单
			FndGlobal.OpenBillById(iFormView, AOS_SEALSAMPLE, sampleId);
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	public void aos_submit(DynamicObject dy_main, String type) throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			SaveControl(dy_main);// 先做数据校验判断是否可以提交
			long aos_requireby = (long) (dy_main.getDynamicObject("aos_requireby")).getPkValue();
			String aos_status = dy_main.get("aos_status").toString();
			Object aos_photoflag = dy_main.get("aos_photoflag");
			Object aos_phstate = dy_main.get("aos_phstate");
			Boolean aos_vedio = dy_main.getBoolean("aos_vedio");
			Boolean UpdatePhotoReqFlag = false;
			Object aos_sourceid = dy_main.get("aos_sourceid");
			String SampleManager = ProgressUtil.findUserByNumber("021903");// 样品间管理人员
			Boolean newflag = false;

			switch (aos_status) {
				case "新建":
					if (!(boolean) aos_photoflag && !aos_vedio) {
						dy_main.set("aos_status", "已完成");
						dy_main.set("aos_user", SampleManager);
						UpdatePhotoReqFlag = true;
					} else {
						if ("来样拍照".equals(aos_phstate)) {
							dy_main.set("aos_user", SampleManager);
						} else if ("外包拍照".equals(aos_phstate)) {
							dy_main.set("aos_user", aos_requireby);
						}
						dy_main.set("aos_status", "备样中");
					}
					newflag = true;
					break;
				case "退回":
					if (!(boolean) aos_photoflag && !aos_vedio) {
						dy_main.set("aos_status", "已完成");
						dy_main.set("aos_user", SampleManager);
						UpdatePhotoReqFlag = true;
					} else {
						if ("来样拍照".equals(aos_phstate)) {
							dy_main.set("aos_user", SampleManager);
						} else if ("外包拍照".equals(aos_phstate)) {
							dy_main.set("aos_user", aos_requireby);
						}
						dy_main.set("aos_status", "备样中");
					}
					newflag = true;
					break;
				case "备样中":
					if ("来样拍照".equals(aos_phstate)) {
						dy_main.set("aos_status", "已入库");
						if ("来样拍照".equals(aos_phstate)) {
							dy_main.set("aos_user", SampleManager);
							updatePhoto(dy_main, SampleManager);
						} else if ("外包拍照".equals(aos_phstate)) {
							dy_main.set("aos_user", aos_requireby);
						}
					} else if (aos_phstate != null && ((aos_phstate + "").contains("工厂") || "外包拍照".equals(aos_phstate))) {
						dy_main.set("aos_status", "已完成");
						dy_main.set("aos_user", SampleManager);
						UpdatePhotoReqFlag = true;
					}
					dy_main.set("aos_waredate", new Date());
					break;
				case "已寄样":
					if ("外包拍照".equals(aos_phstate)) {
						dy_main.set("aos_user", aos_requireby);
					} else if ("来样拍照".equals(aos_phstate)) {
						dy_main.set("aos_user", SampleManager);
					}
					dy_main.set("aos_status", "已入库");
					break;
				case "已入库":
					dy_main.set("aos_status", "已完成");
					dy_main.set("aos_user", SampleManager);
					UpdatePhotoReqFlag = true;
					dy_main.set("aos_completdate", new Date());
					break;
				case "已安装":
					dy_main.set("aos_status", "已完成");
					dy_main.set("aos_user", SampleManager);
					UpdatePhotoReqFlag = true;
					break;
			}
			// 同步拍照需求表
			SyncPhotoReq(dy_main, newflag);
			// 完成后裂项
			if (UpdatePhotoReqFlag) {
				dy_main.set("aos_completedate", new Date());
				UpdatePhotoReq(aos_sourceid);
			}
			// 插入历史记录
			FndHistory.Create(dy_main, "提交", aos_status);
			SaveServiceHelper.save(new DynamicObject[] { dy_main });
			if (type.equals("A")) {
				this.getView().invokeOperation("refresh");
			}
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 通用控制校验 **/
	private static void SaveControl(DynamicObject dy_main) throws FndError {
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		long CurrentUserId = UserServiceHelper.getCurrentUserId();
		DynamicObject AosUser = dy_main.getDynamicObject("aos_user");
		long aos_user = (long) AosUser.getPkValue();
		String aos_status = dy_main.getString("aos_status").toString();
		Object aos_photoflag = dy_main.get("aos_photoflag");
		Object aos_protype = dy_main.get("aos_protype"); // 样品处理方式
		Object aos_contact = dy_main.get("aos_contact");
		Object aos_contactway = dy_main.get("aos_contactway");
		Object aos_returnadd = dy_main.get("aos_returnadd");
		Object aos_photo = dy_main.get("aos_photo");
		Object aos_reason = dy_main.get("aos_reason");
		Object aos_address = dy_main.get("aos_address");
		Object aos_phstate = dy_main.get("aos_phstate"); // 拍照地点
		Object aos_sameitemid = dy_main.get("aos_sameitemid");
		Boolean aos_vedio = dy_main.getBoolean("aos_vedio");
		Object aos_attach = dy_main.get("aos_attach");

		// ===== 2022/10/12 王丽娜新增校验 =====
		if ("新建".equals(aos_status) && Cux_Common_Utl.IsNull(aos_phstate)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "新建状态下拍照地点必填!");
		}

		// 新建节点，当拍照地点=外包拍照时，样品接收地址必填；确保不会只有外包拍摄地址而无外包简称
        if ("新建".equals(aos_status) && "外包拍照".equals(aos_phstate) && Cux_Common_Utl.IsNull(aos_address)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "新建状态下拍照地点=外包拍照时，样品接收地址必填!");
		}

		// ==== 2022/11/24 拍照地方=工厂简拍/工厂自拍时，样品接收地址/样品处理方式不需必填

		if (!(Boolean) aos_photoflag && Cux_Common_Utl.IsNull(aos_reason)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "是否拍照为否，不拍照原因必填!");
		}

		if ("同XX货号".equals(aos_reason) && Cux_Common_Utl.IsNull(aos_sameitemid)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "同XX货号，同货号必填!");
		}

		// ===== 原校验 =====
		if ((Boolean) aos_photoflag && Cux_Common_Utl.IsNull(aos_address) && !JudgePhotoLocat(aos_phstate)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "需要拍照时样品接收地址必填!");
		}

		if ("退回".equals(aos_protype) && (Cux_Common_Utl.IsNull(aos_contact) || Cux_Common_Utl.IsNull(aos_contactway)
				|| Cux_Common_Utl.IsNull(aos_returnadd))) {
			if ((Boolean) aos_photoflag || aos_vedio) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "退回类型退回信息必填!");
			}
		}

		if (!(Boolean) aos_photoflag && Cux_Common_Utl.IsNull(aos_reason)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照为勾选时不拍照原因必填!");
		}

		if (!(Boolean) aos_photo && Cux_Common_Utl.IsNull(aos_reason)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照为勾选时不拍照原因必填!");
		}

		if ("退回".equals(aos_protype) && (Cux_Common_Utl.IsNull(aos_contact) || Cux_Common_Utl.IsNull(aos_contactway)
				|| Cux_Common_Utl.IsNull(aos_returnadd))) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "退回类型退回信息必填!");
		}

		if (aos_user != CurrentUserId) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "只允许流程节点操作人点击提交!");
		}

		if ("已完成".equals(aos_status)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "已完成状态不允许点击提交!");
		}

		if ("工厂简拍".equals(aos_phstate) && FndGlobal.IsNull(aos_attach)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照地点=工厂简拍时，建模资料字段必填!");
		}

		if (("新建".equals(aos_status) ||"备样中".equals(aos_status))&& "工厂简拍".equals(aos_phstate)) {
			DynamicObject aos_itemid = dy_main.getDynamicObject("aos_itemid");
			String aos_itemname = dy_main.getString("aos_itemname");
			String aos_ponumber =dy_main.getString("aos_ponumber");
			String category = (String) SalUtil.getCategoryByItemId(aos_itemid.getPkValue().toString()).get("name");
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			String AosCategory2 = null;
			String AosCategory3 = null;
			int category_length = category_group.length;
			if (category_length > 0)
				AosCategory1 = category_group[0];
			if (category_length > 1)
				AosCategory2 = category_group[1];
			if (category_length > 2)
				AosCategory3 = category_group[2];

			boolean cond1 = QueryServiceHelper.exists("aos_sealsample",
					new QFilter("aos_item.id", QCP.equals, aos_itemid.getPkValue().toString())
							.and("aos_contractnowb", QCP.equals, aos_ponumber)
							.and("aos_model", QCP.equals, "否").toArray());

			boolean cond2 = QueryServiceHelper.exists("aos_sealsample",
					new QFilter("aos_item.id", QCP.equals, aos_itemid.getPkValue().toString())
							.and("aos_contractnowb", QCP.equals, aos_ponumber)
							.and("aos_model", QCP.equals, "").toArray());

			boolean cond3 = QueryServiceHelper.exists("aos_sealsample",
					new QFilter("aos_item.id", QCP.equals, aos_itemid.getPkValue().toString())
							.and("aos_contractnowb", QCP.equals, aos_ponumber).toArray());

			boolean cond4 = QueryServiceHelper.exists("aos_mkt_3dselect",
					new QFilter("aos_category1", QCP.equals, AosCategory1)
							.and("aos_category2", QCP.equals, AosCategory2)
							.and("aos_category3", QCP.equals, AosCategory3)
							.and("aos_name", QCP.equals, aos_itemname)
							.toArray());

			if (cond1||cond2||(!cond3 && !cond4)){
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "无法建模的产品不允许改成工厂简拍!");
			}
		}

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}
	}

	public static void SyncPhotoReq(DynamicObject dy_main, Boolean newflag) throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			Object aos_sourceid = dy_main.get("aos_sourceid");
			String aos_status = dy_main.getString("aos_status");
			Object aos_photoflag = dy_main.get("aos_photoflag");
			Object aos_reason = dy_main.get("aos_reason");
			Object aos_sameitemid = dy_main.get("aos_sameitemid");
			Object aos_phstate = dy_main.get("aos_phstate"); // 拍照地点
			boolean aos_vedio = dy_main.getBoolean("aos_vedio");
			Object aos_desc = dy_main.get("aos_desc");
			Object aos_comment = dy_main.get("aos_comment");

			if (aos_sourceid == null)
				return;
			// 同步拍照需求表
			DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_photoreq");
			Object aos_sourceidlist = aos_mkt_photoreq.get("aos_sourceid");
			aos_mkt_photoreq.set("aos_samplestatus", aos_status);
			aos_mkt_photoreq.set("aos_desc", aos_desc);
			aos_mkt_photoreq.set("aos_phstate", aos_phstate);

			// 同步拍照任务清单
			DynamicObject aos_mkt_photolist = null;
			boolean photoListSave = true;
			try {
				aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(aos_sourceidlist, "aos_mkt_photolist");
			} catch (KDException e) {
				aos_mkt_photolist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photolist");
				photoListSave = false;
			}

			aos_mkt_photolist.set("aos_samplestatus", aos_status);
			aos_mkt_photolist.set("aos_desc", aos_desc);
			if ("已入库".equals(aos_status)) {
				aos_mkt_photoreq.set("aos_sampledate", new Date());
				aos_mkt_photoreq.set("aos_comment", aos_comment);// 样品备注
				aos_mkt_photolist.set("aos_sampledate", new Date());
			}

			if ("已完成".equals(aos_status)) {
				aos_mkt_photoreq.set("aos_installdate", new Date());
				aos_mkt_photolist.set("aos_installdate", new Date());
			}

			if (newflag) {
				aos_mkt_photoreq.set("aos_photoflag", aos_photoflag);
				aos_mkt_photoreq.set("aos_reason", aos_reason);
				aos_mkt_photoreq.set("aos_sameitemid", aos_sameitemid);

				if (!(boolean) aos_photoflag && !aos_vedio) {
					String phStatus = aos_mkt_photoreq.getString("aos_status");
					// 生成不需要拍
					aos_mkt_nophoto_bill.create_noPhotoEntity(aos_mkt_photoreq);
					aos_mkt_photoreq.set("aos_status", "不需拍");
					aos_mkt_photoreq.set("aos_user", system);
					aos_mkt_photolist.set("aos_phstatus", "不需拍");
					FndHistory.Create(aos_mkt_photoreq, "提交(样品入库回写),下节点：不需拍", phStatus);
				}
			}

			OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] { aos_mkt_photoreq },
					OperateOption.create());
			if (photoListSave)
				OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
						new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());

		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	public static void UpdatePhotoReq(Object aos_sourceid) throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_photoreq");
			String aos_status_s = aos_mkt_photoreq.getString("aos_status"); // 初始状态
			// 异常参数
			int ErrorCount = 0;
			String ErrorMessage = "";
			// 消息参数
			String MessageId = null;
			String MessageStr = null;
			// 数据层
			Object AosPhotoFlag = aos_mkt_photoreq.get("aos_photoflag");
			Object AosVedioFlag = aos_mkt_photoreq.get("aos_vedioflag");
			Object AosSourceid = aos_mkt_photoreq.get("aos_sourceid");
			Object AosWhiteph = aos_mkt_photoreq.get("aos_whiteph");
			Object aos_actph = aos_mkt_photoreq.get("aos_actph");
			Object AosVedior = aos_mkt_photoreq.get("aos_vedior");
			Object AosBillno = aos_mkt_photoreq.get("billno");
			Object ReqFId = aos_mkt_photoreq.get("id"); // 当前界面主键
			Object ReqFIdSplit = null; // 列主键
			Object aos_3d = aos_mkt_photoreq.get("aos_3d");
			String aos_phstate = aos_mkt_photoreq.getString("aos_phstate");
			Boolean Need3DFlag = false;
			Boolean SkipWhite = false;

			if ("工厂简拍".equals(aos_phstate))
				Need3DFlag = true;
			if ("去工厂拍".equals(aos_phstate) || "外包拍照".equals(aos_phstate) || "工厂自拍".equals(aos_phstate))
				SkipWhite = true;
			// 校验
			// 校验白底摄影师是否为空
			if ((boolean) AosPhotoFlag && AosWhiteph == null) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "白底摄影师为空,拍照流程无法流转!");
			}

			if ((boolean) AosVedioFlag && AosVedior == null) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "摄像师为空,视频流程无法流转!");
			}

			if (ErrorCount > 0) {
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}

			// 根据是否拍照是否拍视频分为两张单据
			if ((boolean) AosPhotoFlag && (boolean) AosVedioFlag) {
				// 需要拍照与视频
				// 裂项复制的字段
				DynamicObject AosMktPhotoReq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photoreq");
				AosMktPhotoReq.set("billstatus", "A");
				AosMktPhotoReq.set("aos_requireby", aos_mkt_photoreq.get("aos_requireby"));
				AosMktPhotoReq.set("aos_requiredate", aos_mkt_photoreq.get("aos_requiredate"));
				AosMktPhotoReq.set("aos_shipdate", aos_mkt_photoreq.getDate("aos_shipdate"));
				AosMktPhotoReq.set("aos_urgent", aos_mkt_photoreq.get("aos_urgent"));
				AosMktPhotoReq.set("aos_photoflag", aos_mkt_photoreq.get("aos_photoflag"));
				AosMktPhotoReq.set("aos_reason", aos_mkt_photoreq.get("aos_reason"));
				AosMktPhotoReq.set("aos_sameitemid", aos_mkt_photoreq.get("aos_sameitemid"));
				AosMktPhotoReq.set("aos_vedioflag", aos_mkt_photoreq.get("aos_vedioflag"));
				AosMktPhotoReq.set("aos_reqtype", aos_mkt_photoreq.get("aos_reqtype"));
				AosMktPhotoReq.set("aos_sourceid", aos_mkt_photoreq.get("aos_sourceid"));
				AosMktPhotoReq.set("aos_itemid", aos_mkt_photoreq.get("aos_itemid"));
				AosMktPhotoReq.set("aos_is_saleout",
						ProgressUtil.Is_saleout(aos_mkt_photoreq.getDynamicObject("aos_itemid").getPkValue()));
				AosMktPhotoReq.set("aos_itemname", aos_mkt_photoreq.get("aos_itemname"));
				AosMktPhotoReq.set("aos_contrybrand", aos_mkt_photoreq.get("aos_contrybrand"));
				AosMktPhotoReq.set("aos_newitem", aos_mkt_photoreq.get("aos_newitem"));
				AosMktPhotoReq.set("aos_newvendor", aos_mkt_photoreq.get("aos_newvendor"));
				AosMktPhotoReq.set("aos_ponumber", aos_mkt_photoreq.get("aos_ponumber"));
				AosMktPhotoReq.set("aos_linenumber", aos_mkt_photoreq.get("aos_linenumber"));
				AosMktPhotoReq.set("aos_earlydate", aos_mkt_photoreq.get("aos_earlydate"));
				AosMktPhotoReq.set("aos_checkdate", aos_mkt_photoreq.get("aos_checkdate"));
				AosMktPhotoReq.set("aos_specification", aos_mkt_photoreq.get("aos_specification"));
				AosMktPhotoReq.set("aos_seting1", aos_mkt_photoreq.get("aos_seting1"));
				AosMktPhotoReq.set("aos_seting2", aos_mkt_photoreq.get("aos_seting2"));
				AosMktPhotoReq.set("aos_sellingpoint", aos_mkt_photoreq.get("aos_sellingpoint"));
				AosMktPhotoReq.set("aos_vendor", aos_mkt_photoreq.get("aos_vendor"));
				AosMktPhotoReq.set("aos_city", aos_mkt_photoreq.get("aos_city"));
				AosMktPhotoReq.set("aos_contact", aos_mkt_photoreq.get("aos_contact"));
				AosMktPhotoReq.set("aos_address", aos_mkt_photoreq.get("aos_address"));
				AosMktPhotoReq.set("aos_phone", aos_mkt_photoreq.get("aos_phone"));
				AosMktPhotoReq.set("aos_phstate", aos_mkt_photoreq.get("aos_phstate"));
				AosMktPhotoReq.set("aos_rcvbill", aos_mkt_photoreq.get("aos_rcvbill"));
				AosMktPhotoReq.set("aos_sampledate", aos_mkt_photoreq.get("aos_sampledate"));
				AosMktPhotoReq.set("aos_installdate", aos_mkt_photoreq.get("aos_installdate"));
				AosMktPhotoReq.set("aos_poer", aos_mkt_photoreq.get("aos_poer"));
				AosMktPhotoReq.set("aos_developer", aos_mkt_photoreq.get("aos_developer"));
				AosMktPhotoReq.set("aos_follower", aos_mkt_photoreq.get("aos_follower"));
				AosMktPhotoReq.set("aos_whiteph", aos_mkt_photoreq.get("aos_whiteph"));
				AosMktPhotoReq.set("aos_actph", aos_mkt_photoreq.get("aos_actph"));
				AosMktPhotoReq.set("aos_vedior", aos_mkt_photoreq.get("aos_vedior"));
				AosMktPhotoReq.set("aos_3d", aos_mkt_photoreq.get("aos_3d"));
				AosMktPhotoReq.set("aos_whitedate", aos_mkt_photoreq.get("aos_whitedate"));
				AosMktPhotoReq.set("aos_actdate", aos_mkt_photoreq.get("aos_actdate"));
				AosMktPhotoReq.set("aos_picdate", aos_mkt_photoreq.get("aos_picdate"));
				AosMktPhotoReq.set("aos_funcpicdate", aos_mkt_photoreq.get("aos_funcpicdate"));
				AosMktPhotoReq.set("aos_vedio", aos_mkt_photoreq.get("aos_vedio"));
				AosMktPhotoReq.set("billno", aos_mkt_photoreq.get("billno"));

				if ("工厂简拍".equals(aos_phstate))
					AosMktPhotoReq.set("aos_user", system);
				else
					AosMktPhotoReq.set("aos_user", aos_mkt_photoreq.get("aos_vedior"));

				AosMktPhotoReq.set("aos_type", "视频");
				AosMktPhotoReq.set("aos_designer", aos_mkt_photoreq.get("aos_designer"));
				AosMktPhotoReq.set("aos_sale", aos_mkt_photoreq.get("aos_sale"));
				AosMktPhotoReq.set("aos_vediotype", aos_mkt_photoreq.get("aos_vediotype"));
				AosMktPhotoReq.set("aos_organization1", aos_mkt_photoreq.get("aos_organization1"));
				AosMktPhotoReq.set("aos_organization2", aos_mkt_photoreq.get("aos_organization2"));
				AosMktPhotoReq.set("aos_vediotype", aos_mkt_photoreq.get("aos_vediotype"));
				AosMktPhotoReq.set("aos_orgtext", aos_mkt_photoreq.get("aos_orgtext"));
				AosMktPhotoReq.set("aos_samplestatus", aos_mkt_photoreq.get("aos_samplestatus"));
				AosMktPhotoReq.set("aos_3dflag", aos_mkt_photoreq.get("aos_3dflag"));
				AosMktPhotoReq.set("aos_3d_reason", aos_mkt_photoreq.get("aos_3d_reason"));
				AosMktPhotoReq.set("aos_desc", aos_mkt_photoreq.get("aos_desc"));
				// 带出字幕需求
				AosMktPhotoReq.set("aos_subtitle", aos_mkt_photoreq.get("aos_subtitle"));
				AosMktPhotoReq.set("aos_language", aos_mkt_photoreq.get("aos_language"));

				// 新增质检完成日期
				QFilter qFilter_contra = new QFilter("aos_insrecordentity.aos_insresultchk", "=", "A");
				QFilter qFilter_lineno = new QFilter("aos_insrecordentity.aos_instypedetailchk", "=", "1");
				QFilter qFilter_ponumber = new QFilter("aos_insrecordentity.aos_contractnochk", "=",
						aos_mkt_photoreq.get("aos_ponumber"));
				QFilter qFilter_linenumber = new QFilter("aos_insrecordentity.aos_lineno", "=",
						aos_mkt_photoreq.get("aos_linenumber"));
				QFilter[] qFilters = { qFilter_contra, qFilter_lineno, qFilter_ponumber, qFilter_linenumber };
				DynamicObject dy_date = QueryServiceHelper.queryOne("aos_qctasklist", "aos_quainscomdate", qFilters);
				if (dy_date != null) {
					AosMktPhotoReq.set("aos_quainscomdate", dy_date.get("aos_quainscomdate"));
				}

				// 照片需求单据体
				DynamicObjectCollection aos_entryentityS = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity");
				DynamicObjectCollection aos_entryentityOriS = aos_mkt_photoreq
						.getDynamicObjectCollection("aos_entryentity");
				for (DynamicObject aos_entryentityOri : aos_entryentityOriS) {
					DynamicObject aos_entryentity = aos_entryentityS.addNew();
					aos_entryentity.set("aos_applyby", aos_entryentityOri.get("aos_applyby"));
					aos_entryentity.set("aos_picdesc", aos_entryentityOri.get("aos_picdesc"));
				}

				// 照片需求单据体(新)
				DynamicObjectCollection aos_entryentity5S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity5");
				DynamicObjectCollection aos_entryentity5OriS = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity5");
				for (DynamicObject aos_entryentity5Ori : aos_entryentity5OriS) {
					DynamicObject aos_entryentity5 = aos_entryentity5S.addNew();
					aos_entryentity5.set("aos_reqfirst", aos_entryentity5Ori.get("aos_reqfirst"));
					aos_entryentity5.set("aos_reqother", aos_entryentity5Ori.get("aos_reqother"));
					aos_entryentity5.set("aos_detail", aos_entryentity5Ori.get("aos_detail"));
					aos_entryentity5.set("aos_scene1", aos_entryentity5Ori.get("aos_scene1"));
					aos_entryentity5.set("aos_object1", aos_entryentity5Ori.get("aos_object1"));
					aos_entryentity5.set("aos_scene2", aos_entryentity5Ori.get("aos_scene2"));
					aos_entryentity5.set("aos_object2", aos_entryentity5Ori.get("aos_object2"));
					aos_entryentity5.set("aos_productstyle_new", aos_entryentity5Ori.get("aos_productstyle_new"));
					aos_entryentity5.set("aos_shootscenes", aos_entryentity5Ori.get("aos_shootscenes"));
				}
				// 照片需求单据体(新2)
				DynamicObjectCollection aos_entryentity6S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity6");
				DynamicObjectCollection aos_entryentity6OriS = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity6");
				for (DynamicObject aos_entryentity6Ori : aos_entryentity6OriS) {
					DynamicObject aos_entryentity6 = aos_entryentity6S.addNew();
					aos_entryentity6.set("aos_reqsupp", aos_entryentity6Ori.get("aos_reqsupp"));
					aos_entryentity6.set("aos_devsupp", aos_entryentity6Ori.get("aos_devsupp"));
				}

				// 视频需求单据体
				DynamicObjectCollection aos_entryentity1S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity1");
				DynamicObjectCollection aos_entryentityOri1S = aos_mkt_photoreq
						.getDynamicObjectCollection("aos_entryentity1");
				for (DynamicObject aos_entryentityOri1 : aos_entryentityOri1S) {
					DynamicObject aos_entryentity1 = aos_entryentity1S.addNew();
					aos_entryentity1.set("aos_applyby2", aos_entryentityOri1.get("aos_applyby2"));
					aos_entryentity1.set("aos_veddesc", aos_entryentityOri1.get("aos_veddesc"));
				}

				// 拍摄情况单据体
				DynamicObjectCollection aos_entryentity2S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity2");
				DynamicObjectCollection aos_entryentityOri2S = aos_mkt_photoreq
						.getDynamicObjectCollection("aos_entryentity2");
				for (DynamicObject aos_entryentityOri2 : aos_entryentityOri2S) {
					DynamicObject aos_entryentity2 = aos_entryentity2S.addNew();
					aos_entryentity2.set("aos_phtype", aos_entryentityOri2.get("aos_phtype"));
					aos_entryentity2.set("aos_complete", aos_entryentityOri2.get("aos_complete"));
					aos_entryentity2.set("aos_completeqty", aos_entryentityOri2.get("aos_completeqty"));
				}

				// 流程退回原因单据体
				DynamicObjectCollection aos_entryentity3S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity3");
				DynamicObjectCollection aos_entryentityOri3S = aos_mkt_photoreq
						.getDynamicObjectCollection("aos_entryentity3");
				for (DynamicObject aos_entryentityOri3 : aos_entryentityOri3S) {
					DynamicObject aos_entryentity3 = aos_entryentity3S.addNew();
					aos_entryentity3.set("aos_returnby", aos_entryentityOri3.get("aos_returnby"));
					aos_entryentity3.set("aos_return", aos_entryentityOri3.get("aos_return"));
					aos_entryentity3.set("aos_returnreason", aos_entryentityOri3.get("aos_returnreason"));
				}

				// 视频地址单据体
				DynamicObjectCollection aos_entryentity4S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity4");
				DynamicObjectCollection aos_entryentityOri4S = aos_mkt_photoreq
						.getDynamicObjectCollection("aos_entryentity4");
				for (DynamicObject aos_entryentityOri4 : aos_entryentityOri4S) {
					DynamicObject aos_entryentity4 = aos_entryentity4S.addNew();
					aos_entryentity4.set("aos_orgshort", aos_entryentityOri4.get("aos_orgshort"));
					aos_entryentity4.set("aos_brand", aos_entryentityOri4.get("aos_brand"));
					aos_entryentity4.set("aos_s3address1", aos_entryentityOri4.get("aos_s3address1"));
					aos_entryentity4.set("aos_s3address2", aos_entryentityOri4.get("aos_s3address2"));
				}

				// 本节点中改变的字段
				AosMktPhotoReq.set("aos_type", "视频");
				AosMktPhotoReq.set("aos_status", "视频拍摄");

				OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
						new DynamicObject[] { AosMktPhotoReq }, OperateOption.create());
				if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
					ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "裂项提交失败!");
					FndError fndMessage = new FndError(ErrorMessage);
					throw fndMessage;
				}
				aos_mkt_photoreq.set("aos_type", "拍照");
				// 判断是否需要3D建模
				if (Need3DFlag) {
					aos_mkt_photoreq.set("aos_status", "3D建模");
					aos_mkt_photoreq.set("aos_user", aos_3d);// 流转给3D摄影师
					MessageId = ((DynamicObject) aos_3d).getPkValue().toString();
				} else {
					// 跳过白底
					if (SkipWhite) {
						aos_mkt_photoreq.set("aos_status", "实景拍摄");
						aos_mkt_photoreq.set("aos_user", aos_actph);// 流转给实景摄影师
						MessageId = ((DynamicObject) aos_actph).getPkValue().toString();
					} else {
						aos_mkt_photoreq.set("aos_status", "白底拍摄");
						aos_mkt_photoreq.set("aos_user", AosWhiteph);// 流转给白底摄影师
						MessageId = ((DynamicObject) AosWhiteph).getPkValue().toString();
					}
				}
				ReqFIdSplit = operationrst.getSuccessPkIds().get(0);
			} else if ((boolean) AosPhotoFlag) {
				// 只需要拍照
				aos_mkt_photoreq.set("aos_type", "拍照");
				if (Need3DFlag) {
					aos_mkt_photoreq.set("aos_status", "3D建模");
					aos_mkt_photoreq.set("aos_user", aos_3d);// 流转给3D摄影师
					MessageId = ((DynamicObject) aos_3d).getPkValue().toString();
				} else {
					// 跳过白底
					if (SkipWhite) {
						aos_mkt_photoreq.set("aos_status", "实景拍摄");
						aos_mkt_photoreq.set("aos_user", aos_actph);// 流转给实景摄影师
						MessageId = ((DynamicObject) aos_actph).getPkValue().toString();
					} else {
						aos_mkt_photoreq.set("aos_status", "白底拍摄");
						aos_mkt_photoreq.set("aos_user", AosWhiteph);// 流转给白底摄影师
						MessageId = ((DynamicObject) AosWhiteph).getPkValue().toString();
					}
				}
				;
				MessageStr = "拍照需求表-白底拍摄";
			} else if ((boolean) AosVedioFlag) {
				// 只需要视频
				aos_mkt_photoreq.set("aos_type", "视频");
				aos_mkt_photoreq.set("aos_status", "视频拍摄");
				aos_mkt_photoreq.set("aos_user", AosVedior);// 流转给摄像师
				MessageId = ((DynamicObject) AosVedior).getPkValue().toString();
				MessageStr = "拍照需求表-视频拍摄";
			}

			String aos_status_e = aos_mkt_photoreq.getString("aos_status");
			if (!aos_status_s.equals(aos_status_e)) {
				FndHistory.Create(aos_mkt_photoreq, "提交(样品入库提交),下节点：" + aos_status_e, aos_status_s);
			}
			// 源单保存
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
					new DynamicObject[] { aos_mkt_photoreq }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "源单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}

			// 回写拍照任务清单
			boolean photoListSave = true;
			DynamicObject aos_mkt_photolist = null;
			try {
				aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			} catch (KDException ex) {
				aos_mkt_photolist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photolist");
				photoListSave = false;
			}

			if ((boolean) AosPhotoFlag && (boolean) AosVedioFlag) {
				if (Need3DFlag) {
					aos_mkt_photolist.set("aos_phstatus", "3D建模");
				} else {
					// 跳过白底
					if (SkipWhite) {
						aos_mkt_photoreq.set("aos_status", "实景拍摄");
					} else {
						aos_mkt_photolist.set("aos_phstatus", "白底拍摄");
					}
				}
				aos_mkt_photolist.set("aos_vedstatus", "视频拍摄");
			} else if ((boolean) AosPhotoFlag) {
				if (Need3DFlag) {
					aos_mkt_photolist.set("aos_phstatus", "3D建模");
				} else {
					// 跳过白底
					if (SkipWhite) {
						aos_mkt_photoreq.set("aos_status", "实景拍摄");
					} else {
						aos_mkt_photolist.set("aos_phstatus", "白底拍摄");
					}
				}
			} else if ((boolean) AosVedioFlag) {
				aos_mkt_photolist.set("aos_vedstatus", "视频拍摄");
			}
			if (photoListSave) {
				OperationResult operationrst1 = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
						new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
				if (operationrst1.getValidateResult().getValidateErrors().size() != 0) {
					ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
					FndError fndMessage = new FndError(ErrorMessage);
					throw fndMessage;
				}
			}

			if (Need3DFlag) {
				aos_mkt_3design_bill.Generate3Design(aos_mkt_photoreq);// 生成3D确认单
			}

			// 发送消息
			if ((boolean) AosPhotoFlag && (boolean) AosVedioFlag) {
				MKTCom.SendGlobalMessage(MessageId, "aos_mkt_photoreq", ReqFId + "", AosBillno + "", "拍照需求表-白底拍摄");
				MKTCom.SendGlobalMessage(MessageId, "aos_mkt_photoreq", ReqFIdSplit + "", AosBillno + "", "拍照需求表-视频拍摄");
			} else {
				MKTCom.SendGlobalMessage(MessageId, "aos_mkt_photoreq", ReqFId + "", AosBillno + "", MessageStr);
			}

		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 创建3D产品设计单 **/

	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		StatusControl();
		GenerateLov();

		Boolean aosPhotoFlag = (Boolean) this.getModel().getValue("aos_photoflag");
		if (aosPhotoFlag) {
			this.getModel().setValue("aos_reason", null);
			this.getModel().setValue("aos_sameitemid", null);
			this.getView().setEnable(false, "aos_reason");
			this.getView().setEnable(false, "aos_sameitemid");
		} else {
			this.getView().setEnable(true, "aos_reason");
			this.getView().setEnable(true, "aos_sameitemid");
		}
	}

	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		StatusControl();
		GenerateLov();
	}

	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if (name.equals("aos_protype") && "退回".equals(this.getModel().getValue(name).toString()))
			AosProTypeChange();
		else if ("aos_photoflag".equals(name))
			AosPhotoFlagChange();
		else if ("aos_phstate".equals(name))
			AosPhstateChange();
		else if ("aos_address".equals(name))
			AosAddressChange();
	}

	/**
	 * 样品接收地址值改变事件
	 */
	private void AosAddressChange() {
		QFilter filter_type = new QFilter("aos_entryentity.aos_content", QCP.equals,
				this.getModel().getValue("aos_address"));
		DynamicObject aos_mkt_sampleaddress = QueryServiceHelper.queryOne("aos_mkt_sampleaddress",
				"aos_entryentity.aos_desc aos_desc", filter_type.toArray());
		if (FndGlobal.IsNotNull(aos_mkt_sampleaddress))
			this.getModel().setValue("aos_desc", aos_mkt_sampleaddress.get("aos_desc"));
	}

	private void AosPhstateChange() {
		GenerateLov();
	}

	/** 是否拍照值改变 **/
	private void AosPhotoFlagChange() {
		Boolean aosPhotoFlag = (Boolean) this.getModel().getValue("aos_photoflag");
		this.getModel().setValue("aos_photo", aosPhotoFlag);


		if (aosPhotoFlag) {
			this.getModel().setValue("aos_reason", null);
			this.getModel().setValue("aos_sameitemid", null);
			this.getView().setEnable(false, "aos_reason");
			this.getView().setEnable(false, "aos_sameitemid");
		} else {
			this.getView().setEnable(true, "aos_reason");
			this.getView().setEnable(true, "aos_sameitemid");
		}
	}

	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
	}

	private void AosProTypeChange() {
		Object aos_vendor = this.getModel().getValue("aos_vendor");
		QFilter filter_vendor = new QFilter("aos_vendor", "=", aos_vendor);
		QFilter filter_type = new QFilter("aos_protype", "=", "退回");
		QFilter[] filters = { filter_vendor, filter_type };
		DynamicObjectCollection aos_mkt_rcvS = QueryServiceHelper.query("aos_mkt_rcv",
				"aos_contact,aos_contactway,aos_returnadd", filters, "createtime desc");
		for (DynamicObject aos_mkt_rcv : aos_mkt_rcvS) {
			this.getModel().setValue("aos_contact", aos_mkt_rcv.get("aos_contact"));
			this.getModel().setValue("aos_contactway", aos_mkt_rcv.get("aos_contactway"));
			this.getModel().setValue("aos_returnadd", aos_mkt_rcv.get("aos_returnadd"));
			return;
		}
	}

	private void GenerateLov() {
		Object aos_phstate = this.getModel().getValue("aos_phstate");
		// 根据拍照地点 动态设置下拉框
		ComboEdit comboEdit = this.getControl("aos_address");
		// 设置下拉框的值
		List<ComboItem> data = new ArrayList<>();
		QFilter filter_status = new QFilter("aos_address", "=", aos_phstate);
		QFilter filter_type = new QFilter("aos_entryentity.aos_valid", "=", true);
		QFilter[] filters = { filter_status, filter_type };
		DynamicObjectCollection aos_mkt_sampleaddress = QueryServiceHelper.query("aos_mkt_sampleaddress",
				"aos_entryentity.aos_content aos_content", filters);
		for (DynamicObject d : aos_mkt_sampleaddress) {
			String aos_content = d.getString("aos_content");
			data.add(new ComboItem(new LocaleString(aos_content), aos_content));
		}
		comboEdit.setComboItems(data);
	}

	/** 全局状态控制 **/
	private void StatusControl() {
		// 数据层
		String aos_status = this.getModel().getValue("aos_status").toString();
		DynamicObject aos_user = (DynamicObject) this.getModel().getValue("aos_user");
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");
		// 当前节点操作人不为当前用户 全锁
		if (!aos_user.getPkValue().toString().equals(CurrentUserId.toString())
				&& !"刘中怀".equals(CurrentUserName.toString()) && !"程震杰".equals(CurrentUserName.toString())
				&& !"陈聪".equals(CurrentUserName.toString()) && !"杨晶晶".equals(CurrentUserName.toString())) {
			this.getView().setEnable(false, "titlepanel");// 标题面板
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
		}
		// 状态控制
		if (!"新建".equals(aos_status) && !"已入库".equals(aos_status) && !"退回".equals(aos_status)) {
			List<String> list_noEnable = Arrays.asList("aos_returnreason", "aos_issuedate", "aos_express_info");
			setContinEnable("aos_contentpanelflex", list_noEnable);
		} else if ("已入库".equals(aos_status)) {
			List<String> list_noEnable = Arrays.asList("aos_returnreason", "aos_issuedate", "aos_express_info",
					"aos_phstate", "aos_address");
			setContinEnable("aos_contentpanelflex", list_noEnable);
		} else {
			this.getView().setEnable(true, "contentpanelflex");// 主界面面板
		}
		if ("已完成".equals(aos_status)) {
			this.getView().setVisible(true, "bar_save");
			this.getView().setEnable(true, "bar_save");
			this.getView().setVisible(false, "aos_submit");
		}
		if ("已入库".equals(aos_status)) {
			this.getView().setVisible(true, "aos_return");
			this.getView().setEnable(true, "aos_returnreason");
		} else if ("备样中".equals(aos_status)) {
			this.getView().setVisible(true, "aos_return");
			this.getView().setEnable(true, "aos_returnreason");
		} else {
			this.getView().setVisible(false, "aos_return");
		}
		if (!"新建".equals(aos_status) && !"退回".equals(aos_status)) {
			this.getView().setEnable(false, "aos_comment");// 样品备注
		}
	}

	public void setContinEnable(String name, List<String> list_noEableField) {
		String panelName = name;
		if (panelName.equals("aos_contentpanelflex"))
			panelName = "aos_flexpanelap1";
		Container flexPanel = this.getView().getControl(panelName);
		String[] keys = flexPanel.getItems().stream().map(Control::getKey)
				.filter(key -> !list_noEableField.contains(key)).collect(Collectors.toList()).toArray(new String[0]);
		this.getView().setEnable(false, keys);
	}

	/** 判断拍照地点 **/
	private static boolean JudgePhotoLocat(Object locat) {
		if (String.valueOf(locat).equals("工厂简拍") || String.valueOf(locat).equals("工厂自拍"))
			return true;
		else
			return false;
	}

	/** 拍照地点=来样拍照，状态=备样中,提交时,更新对应拍照需求表的当前节点操作人 **/
	private void updatePhoto(DynamicObject dy_main, Object user) {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			QFBuilder builder = new QFBuilder();
			builder.add("billno", "=", dy_main.get("aos_orignbill"));
			DynamicObject[] dyc_photo = BusinessDataServiceHelper.load("aos_mkt_photoreq", "id,aos_user",
					builder.toArray());
			if (dyc_photo.length > 0) {
				dyc_photo[0].set("aos_user", user);
				SaveServiceHelper.update(dyc_photo);
			}
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}


	}

}