package mkt.progress.listing;

import java.util.*;
import java.util.stream.Collectors;

import com.sun.istack.NotNull;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import common.sal.sys.basedata.dao.CountryDao;
import common.sal.sys.basedata.dao.impl.CountryDaoImpl;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.entity.property.EntryProp;
import kd.bos.form.ClientProperties;
import kd.bos.form.ConfirmCallBackListener;
import kd.bos.form.MessageBoxOptions;
import kd.bos.form.MessageBoxResult;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.events.MessageBoxClosedEvent;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.GlobalMessage;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import mkt.progress.parameter.errorListing.ErrorListEntity;

public class aos_mkt_listingson_bill extends AbstractBillPlugIn implements ItemClickListener, HyperLinkClickListener {

    /**
     * 系统管理员
     **/
    public final static String system = Cux_Common_Utl.SYSTEM;

    /**
     * 回写拍照需求表状态至 视频剪辑
     **/
    public static void UpdatePhotoToCut(Object aos_sourceid) throws FndError {
        String MessageId = null;
        // 异常参数
        // String ErrorMessage = "";
        // 回写拍照需求表
        DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_photoreq");
        String aos_status = aos_mkt_photoreq.getString("aos_status");
        Object aos_vedior = aos_mkt_photoreq.get("aos_vedior");// 摄像师
        Object AosPhotoList = aos_mkt_photoreq.get("aos_sourceid");
        aos_mkt_photoreq.set("aos_status", "视频剪辑");
        aos_mkt_photoreq.set("aos_user", aos_vedior);
        // 回写拍照任务清单
        if (QueryServiceHelper.exists("aos_mkt_photolist", AosPhotoList)) {
            DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosPhotoList, "aos_mkt_photolist");
            aos_mkt_photolist.set("aos_vedstatus", "视频剪辑");
            OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[]{aos_mkt_photolist}, OperateOption.create());
        }

        FndHistory.Create(aos_mkt_photoreq, "提交(文案回写)，下节点：视频剪辑", aos_status);

        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
                new DynamicObject[]{aos_mkt_photoreq}, OperateOption.create());
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            MKTCom.SendGlobalMessage(MessageId, aos_mkt_photoreq + "", operationrst.getSuccessPkIds().get(0) + "",
                    aos_mkt_photoreq.getString("billno"), "拍照需求表-视频剪辑");
        }
    }

    /**
     * 设置操作人的组织
     **/
    public static void setListSonUserOrganizate(DynamicObject dy_main) {
        //设置操作人
        setUser(dy_main);
        //添加数据到 改错任务清单
        setErrorList(dy_main);
    }

    private static void setUser(DynamicObject dy_main) {
        Object aos_user = dy_main.get("aos_user");
        Object userID;
        if (aos_user instanceof Long) {
            userID = aos_user;
        } else if (aos_user instanceof DynamicObject) {
            userID = ((DynamicObject) aos_user).get("id");
        } else if (aos_user instanceof EntryProp) {
            userID = ((DynamicObject) aos_user).get("id");
        } else if (aos_user instanceof String) {
            userID = aos_user;
        } else
            return;
        List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(userID);
        if (MapList != null) {
            if (MapList.size() >= 3 && MapList.get(2) != null)
                dy_main.set("aos_userorganizat1", MapList.get(2).get("id"));
            if (MapList.size() >= 4 && MapList.get(3) != null)
                dy_main.set("aos_userorganizat2", MapList.get(3).get("id"));
            if (MapList.size() >= 5 && MapList.get(4) != null)
                dy_main.set("aos_userorganizat3", MapList.get(4).get("id"));
        }
    }

    private static void setErrorList(DynamicObject dy_main) {
        String aos_status = dy_main.getString("aos_status");
        if (!aos_status.equals("结束"))
            return;
        String aos_type = dy_main.getString("aos_type");
        if (!ErrorListEntity.errorListType.contains(aos_type))
            return;
        List<String> errorCountries = ErrorListEntity.errorCountries;
        DynamicObject dy_org = dy_main.getDynamicObject("aos_orgid");
        String billno = dy_main.getString("billno");
        //国别为空
        if (dy_org == null) {
            DynamicObjectCollection dyc_ent = dy_main.getDynamicObjectCollection("aos_entryentity");
            CountryDao countryDao = new CountryDaoImpl();
            for (DynamicObject dy : dyc_ent) {
                if (dy.get("aos_itemid") == null) {
                    continue;
                }
                String itemid = dy.getDynamicObject("aos_itemid").getString("id");
                String orgtextR = dy.getString("aos_orgtext_r");
                if (Cux_Common_Utl.IsNull(orgtextR))
                    continue;
                String[] split = orgtextR.split(";");
                for (String org : split) {
                    if (errorCountries.contains(org)) {
                        String orgid = countryDao.getCountryID(org);
                        ErrorListEntity errorListEntity = new ErrorListEntity(billno, aos_type, orgid, itemid);
                        errorListEntity.save();
                    }
                }

            }
        } else {
            String orgid = dy_org.getString("id");
            String orgNumber = dy_org.getString("number");
            if (errorCountries.contains(orgNumber)) {
                DynamicObjectCollection dyc_ent = dy_main.getDynamicObjectCollection("aos_entryentity");
                for (DynamicObject dy : dyc_ent) {
                    if (dy.get("aos_itemid") == null) {
                        continue;
                    }
                    String itemid = dy.getDynamicObject("aos_itemid").getString("id");
                    ErrorListEntity errorListEntity = new ErrorListEntity(billno, aos_type, orgid, itemid);
                    errorListEntity.save();
                }
            }
        }
    }

    private static void SubmitToOsConfirm(DynamicObject dy_main) {
        long currentUserId = UserServiceHelper.getCurrentUserId();
        dy_main.set("aos_make", currentUserId);
        dy_main.set("aos_status", "海外确认");// 设置单据流程状态
    }

    /**
     * 来源类型=设计需求表时，编辑确认节点可编辑；提交后将值回写到设计需求表的功能图文案备注字段
     **/
    private static void fillDesign(DynamicObject dy_main) {
        String aos_sourcetype = dy_main.getString("aos_sourcetype");
        if (aos_sourcetype.equals("DESIGN")) {
            String aos_sourceid = dy_main.getString("aos_sourceid");
            DynamicObject dy_design = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_designreq");// 设计需求表
            // 获取文中物料对应的行
            Map<String, DynamicObject> map_itemToRow = dy_main.getDynamicObjectCollection("aos_entryentity").stream()
                    .collect(Collectors.toMap(dy -> dy.getDynamicObject("aos_itemid").getString("id"), dy -> dy,
                            (key1, key2) -> key1));
            DynamicObjectCollection dyc_dsign = dy_design.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject dy_row : dyc_dsign) {
                DynamicObject aos_itemid = dy_row.getDynamicObject("aos_itemid");
                if (aos_itemid == null)
                    continue;
                String itemid = aos_itemid.getString("id");
                if (map_itemToRow.containsKey(itemid)) {
                    DynamicObject dy_sonRow = map_itemToRow.get(itemid);
                    dy_row.set("aos_remakes", dy_sonRow.get("aos_remakes"));

                }
            }
            SaveServiceHelper.update(new DynamicObject[]{dy_design});
        }
    }

    /**
     * 申请人提交
     **/
    private static void SubmitForApply(DynamicObject dy_main) {
        String MessageId = null;
        String Message = "Listing优化需求表子表-编辑确认";
        Object aos_editor = dy_main.get("aos_editor");
        Object ReqFId = dy_main.getPkValue(); // 当前界面主键
        Object billno = dy_main.get("billno");
        MessageId = aos_editor + "";
        dy_main.set("aos_status", "编辑确认");// 设置单据流程状态
        dy_main.set("aos_user", aos_editor);// 流转给编辑
        MKTCom.SendGlobalMessage(MessageId, "aos_mkt_listing_son", ReqFId + "", billno + "", Message);
    }

    /**
     * 值校验
     **/
    private static void SaveControl(DynamicObject dy_main) throws FndError {
        int ErrorCount = 0;
        String ErrorMessage = "";
        // 数据层

        Object aos_osconfirmlov = dy_main.get("aos_osconfirmlov");
        if (Cux_Common_Utl.IsNull(aos_osconfirmlov)) {
            ErrorCount++;
            ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "海外文字确认必填!");
        }

        // 校验
        if (ErrorCount > 0) {
            FndError fndMessage = new FndError(ErrorMessage);
            throw fndMessage;
        }
    }

    /**
     * 编辑确认状态下提交
     **/
    private static void SubmitForEditor(DynamicObject dy_main) throws FndError {
        // 异常参数
        int ErrorCount = 0;
        String ErrorMessage = "";
        // 数据层
        Object aos_designer = dy_main.get("aos_designer");
        Object aos_orignbill = dy_main.get("aos_orignbill");
        Object aos_sourceid = dy_main.get("aos_sourceid"); // 源单id
        Object aos_sourcetype = dy_main.get("aos_sourcetype");
        Boolean aos_ismall = false;
        Object aos_ismalllov = dy_main.get("aos_ismalllov");

        Object aos_orgid = dy_main.get("aos_orgid");
        if (FndGlobal.IsNotNull(aos_orgid))
            aos_orgid = dy_main.getDynamicObject("aos_orgid");

        Object aos_type = dy_main.get("aos_type");// 任务类型

        if ("是".equals(aos_ismalllov))
            aos_ismall = true;
        List<DynamicObject> ListLanguageListing = new ArrayList<DynamicObject>();// 需要生成小语种的行
        List<DynamicObject> ListLanguageListingCA = new ArrayList<DynamicObject>();// 需要生成小语种的行
        // 校验
        DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");

		Object LastItemId = 0;
		for (DynamicObject aos_entryentity : aos_entryentityS) {
            // 获取优化项 合计
            int aos_write = aos_entryentity.getInt("aos_write");
            int aos_opt = aos_entryentity.getInt("aos_opt");
            int aos_pic = aos_entryentity.getInt("aos_pic");
            int aos_subtitle = aos_entryentity.getInt("aos_subtitle");
            int aos_title = aos_entryentity.getInt("aos_title");
            int aos_keyword = aos_entryentity.getInt("aos_keyword");
            int aos_other = aos_entryentity.getInt("aos_other");
            int aos_etc = aos_entryentity.getInt("aos_etc");
            int total = aos_write + aos_opt + aos_pic + aos_subtitle + aos_title + aos_keyword + aos_other + aos_etc
                    + aos_etc;
            if (total == 0)
                continue;// 优化项无数字 跳过

			if ("0".equals(LastItemId.toString()))
				LastItemId = aos_entryentity.getDynamicObject("aos_itemid").getPkValue();

            Object aos_case = aos_entryentity.get("aos_case");
            if (Cux_Common_Utl.IsNull(aos_case) && !"DESIGN".equals(aos_sourcetype))
                ErrorCount++;
            DynamicObject aos_subentryentity = aos_entryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
            String aos_orgtext = aos_subentryentity.getString("aos_orgtext");
            if (aos_orgtext.contains("ES") || aos_orgtext.contains("IT") || aos_orgtext.contains("FR")
                    || aos_orgtext.contains("DE"))
                ListLanguageListing.add(aos_entryentity);
            if (aos_orgtext.contains("CA")) {
                ListLanguageListingCA.add(aos_entryentity);
            }
        }
        if (ErrorCount > 0)
            ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "优化方案不允许为空!");
        if (Cux_Common_Utl.IsNull(aos_designer)) {
            ErrorCount++;
            ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "设计为空,流程无法流转!");
        }
        if (ErrorCount > 0) {
            FndError fndMessage = new FndError(ErrorMessage);
            throw fndMessage;
        }

        if (aos_ismall && ListLanguageListing.size() > 0 && !"DESIGN".equals(aos_sourcetype))
            // 是否抄送小语种 按照下单国别分组生成listing小语种
            GenerateListingLanguage(dy_main, ListLanguageListing);
        else if ("VED".equals(aos_sourcetype) && aos_ismall && ListLanguageListing.size() == 0)
            // 视频类型 抄送小语种 但是物料中下单国别没有小语种 直接回写视频状态 至视频剪辑
            UpdatePhotoToCut(aos_sourceid);

        if ("四者一致".equals(aos_type) && ListLanguageListingCA.size() > 0)
            GenerateListingLanguageCA(dy_main, ListLanguageListingCA);

        // 先执行保存操作
        dy_main.set("aos_make", RequestContext.get().getCurrUserId());
        dy_main.set("aos_status", "结束");// 设置单据流程状态
        dy_main.set("aos_user", system);// 设置操作人为系统管理员
        dy_main.set("aos_ecdate", new Date());

        // 如果是US 获取抄送人员
//        FndMsg.debug("Org:" + "US".equals(((DynamicObject)aos_orgid).getString("number")));

        if (FndGlobal.IsNotNull(aos_orgid) &&
				"US".equals(((DynamicObject)aos_orgid).getString("number"))) {
            FndMsg.debug("====Into US====");
			String category = MKTCom.getItemCateNameZH(LastItemId);
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			String AosCategory2 = null;
			int category_length = category_group.length;
			if (category_length > 0)
				AosCategory1 = category_group[0];
			if (category_length > 1)
				AosCategory2 = category_group[1];
            FndMsg.debug("AosCategory1:" + AosCategory1);
            FndMsg.debug("AosCategory2:" + AosCategory2);
			DynamicObject aos_mkt_progorguser =
					QueryServiceHelper.queryOne("aos_mkt_progorguser",
							"aos_oseditor",
							(new QFilter("aos_orgid", "=", ((DynamicObject)aos_orgid).getPkValue().toString())
									.and("aos_category1", QCP.equals, AosCategory1)
									.and("aos_category2", QCP.equals, AosCategory2)).toArray());
			if (aos_mkt_progorguser != null) {
				long aos_oseditor = aos_mkt_progorguser.getLong("aos_oseditor");
				dy_main.set("aos_sendto", aos_oseditor);
				MKTCom.SendGlobalMessage(String.valueOf(aos_oseditor),
						"aos_mkt_listing_son",
                        dy_main.getPkValue().toString(),
                        dy_main.getString("billno"),
						"Listing优化需求表-文案处理完成!");
                GlobalMessage.SendMessage(dy_main.getString("billno") +
                        "-Listing优化需求表-文案处理完成!",String.valueOf(aos_oseditor));
			}
		}

            SaveServiceHelper.save(new DynamicObject[]{dy_main});


        // 设计类型 判断本单下Listing需求子表是否全部已完成 非设计需求表过来的子表不触发此逻辑
        if ("DESIGN".equals(aos_sourcetype)) {
            QFilter filter_bill = new QFilter("aos_sourceid", "=", aos_sourceid);
            QFilter filter_status = new QFilter("aos_status", "!=", "结束");
            QFilter[] filters = new QFilter[]{filter_bill, filter_status};
            DynamicObject aos_mkt_listing_son = QueryServiceHelper.queryOne("aos_mkt_listing_son", "count(0)", filters);
            if (aos_mkt_listing_son == null || aos_mkt_listing_son.getInt(0) == 0) {
                DynamicObject aos_mkt_designreq = BusinessDataServiceHelper.loadSingle(aos_sourceid,
                        "aos_mkt_designreq");
                String aos_status = aos_mkt_designreq.getString("aos_status");
                String MessageId = ProgressUtil.getSubmitUser(aos_sourceid, "aos_mkt_designreq", "设计");// 发送消息
                if (Cux_Common_Utl.IsNull(MessageId)) {
                    MessageId = ((DynamicObject) aos_designer).getPkValue().toString();
                }
                aos_mkt_designreq.set("aos_user", MessageId);
                aos_mkt_designreq.set("aos_status", "设计确认:翻译");
                aos_mkt_designreq.set("aos_receivedate", new Date());
                mkt.progress.design.aos_mkt_designreq_bill.setEntityValue(aos_mkt_designreq);
                FndHistory.Create(aos_mkt_designreq, "提交", aos_status);
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
                        new DynamicObject[]{aos_mkt_designreq}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    MKTCom.SendGlobalMessage(MessageId, "aos_mkt_designreq", aos_sourceid + "", aos_orignbill + "",
                            "设计确认:翻译");
                }
            }
        }

        // 如果是Listing类型生成销售信息确认单
        if ("LISTING".equals(aos_sourcetype)) {
            GenerateListingSal(dy_main);// 销售信息确认单
        }
    }

    // 生成销售信息确认单
    private static void GenerateListingSal(DynamicObject dy_main) throws FndError {
        // 信息处理
        String MessageId = null;
        String Message = "";
        // 数据层
        Object aos_designer = dy_main.getDynamicObject("aos_designer").getPkValue();// 设计
        Object billno = dy_main.get("billno");
        Object ReqFId = dy_main.getPkValue(); // 当前界面主键
        Object aos_type = dy_main.get("aos_type");// 任务类型
        Object aos_editor = dy_main.get("aos_editor");// 任务类型
        Object aos_orgid = dy_main.get("aos_orgid");

        Object aos_editorid = ((DynamicObject) aos_editor).getPkValue();// 子表与小语种生成时 申请人为编辑
        Object aos_make = aos_editorid;
        if (dy_main.get("aos_make") != null)
            aos_make = dy_main.get("aos_make");

        DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");
        Map<String, List<DynamicObject>> Oumap = new HashMap<>();
        List<DynamicObject> MapList = new ArrayList<DynamicObject>();
        // 循环国别分组
        for (DynamicObject aos_entryentity : aos_entryentityS) {
            // 获取优化项 合计
            int aos_write = aos_entryentity.getInt("aos_write");
            int aos_opt = aos_entryentity.getInt("aos_opt");
            int aos_pic = aos_entryentity.getInt("aos_pic");
            int aos_subtitle = aos_entryentity.getInt("aos_subtitle");
            int aos_title = aos_entryentity.getInt("aos_title");
            int aos_keyword = aos_entryentity.getInt("aos_keyword");
            int aos_other = aos_entryentity.getInt("aos_other");
            int aos_etc = aos_entryentity.getInt("aos_etc");
            int total = aos_write + aos_opt + aos_pic + aos_subtitle + aos_title + aos_keyword + aos_other + aos_etc
                    + aos_etc;
            if (total == 0)
                continue;// 优化项无数字 跳过
            DynamicObjectCollection aos_subentryentityS = aos_entryentity
                    .getDynamicObjectCollection("aos_subentryentity");
            DynamicObject aos_subentryentity = aos_subentryentityS.get(0);
            String aos_orgtext = aos_subentryentity.getString("aos_orgtext");
            String[] aos_orgtextArray = aos_orgtext.split(";");
            for (int i = 0; i < aos_orgtextArray.length; i++) {
                String org = aos_orgtextArray[i];
                if (!("US".equals(org) || "CA".equals(org) || "UK".equals(org)))
                    continue;
                if (aos_orgid != null && !(((DynamicObject) aos_orgid).getString("number")).equals(org))
                    continue;
                MapList = Oumap.get(org);
                if (MapList == null || MapList.size() == 0) {
                    MapList = new ArrayList<DynamicObject>();
                }
                MapList.add(aos_entryentity);
                Oumap.put(org, MapList);
            }
        }

        // 循环每个分组后的国家 创建一个头
        for (String ou : Oumap.keySet()) {
            Object org_id = FndGlobal.get_import_id(ou, "bd_country");
            DynamicObject aos_mkt_listing_sal = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_sal");
            aos_mkt_listing_sal.set("aos_requireby", UserServiceHelper.getCurrentUserId());
            aos_mkt_listing_sal.set("aos_designer", aos_designer);
            aos_mkt_listing_sal.set("aos_status", "销售确认");
            aos_mkt_listing_sal.set("aos_orgid", org_id);
            aos_mkt_listing_sal.set("aos_orignbill", billno);
            aos_mkt_listing_sal.set("aos_sourceid", ReqFId);
            aos_mkt_listing_sal.set("aos_type", aos_type);
            aos_mkt_listing_sal.set("aos_requiredate", new Date());
            aos_mkt_listing_sal.set("aos_editor", aos_editor);
            aos_mkt_listing_sal.set("aos_sourcetype", "Listing优化需求表子表");

            // BOTP
            aos_mkt_listing_sal.set("aos_sourcebilltype", "aos_mkt_listing_son");
            aos_mkt_listing_sal.set("aos_sourcebillno", dy_main.get("billno"));
            aos_mkt_listing_sal.set("aos_srcentrykey", "aos_entryentity");

            DynamicObjectCollection cmp_entryentityS = aos_mkt_listing_sal
                    .getDynamicObjectCollection("aos_entryentity");
            List<DynamicObject> EntryList = Oumap.get(ou);
            if (EntryList.size() == 0)
                continue;
            Object LastItemId = 0;
            long aos_sale = 0;
            for (int i = 0; i < EntryList.size(); i++) {
                DynamicObject aos_entryentity = EntryList.get(i);
                DynamicObject aos_subentryentity = aos_entryentity.getDynamicObjectCollection("aos_subentryentity")
                        .get(0);
                DynamicObject cmp_entryentity = cmp_entryentityS.addNew();
                cmp_entryentity.set("aos_itemid", aos_entryentity.get("aos_itemid"));
                cmp_entryentity.set("aos_segment3", aos_subentryentity.get("aos_segment3"));
                cmp_entryentity.set("aos_itemname", aos_subentryentity.get("aos_itemname"));
                cmp_entryentity.set("aos_broitem", aos_subentryentity.get("aos_broitem"));
                cmp_entryentity.set("aos_salestatus", "已确认");
                cmp_entryentity.set("aos_text", aos_entryentity.get("aos_case"));
                cmp_entryentity.set("aos_srcrowseq", aos_entryentity.get("SEQ"));

                if ("0".equals(LastItemId.toString())) {
                    LastItemId = aos_entryentity.getDynamicObject("aos_itemid").getPkValue();
                    String category = MKTCom.getItemCateNameZH(LastItemId + "");
                    String[] category_group = category.split(",");
                    String AosCategory1 = null;
                    String AosCategory2 = null;
                    int category_length = category_group.length;
                    if (category_length > 0)
                        AosCategory1 = category_group[0];
                    if (category_length > 1)
                        AosCategory2 = category_group[1];

                    if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("")
                            && !AosCategory2.equals("")) {
                        QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
                        QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
                        QFilter filter_ou = new QFilter("aos_orgid", "=", org_id);
                        QFilter[] filters_category = new QFilter[]{filter_category1, filter_category2, filter_ou};
                        String SelectStr = "aos_salehelper aos_salehelper";
                        DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser",
                                SelectStr, filters_category);
                        if (aos_mkt_progorguser != null) {
                            aos_sale = aos_mkt_progorguser.getLong("aos_salehelper");
                        }
                    }
                }
            }
            aos_mkt_listing_sal.set("aos_sale", aos_sale);
            aos_mkt_listing_sal.set("aos_user", aos_sale);

            MessageId = aos_sale + "";
            Message = "Listing优化销售确认单-Listing优化子表自动创建";
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_sal",
                    new DynamicObject[]{aos_mkt_listing_sal}, OperateOption.create());

            // 修复关联关系
            try {
                ProgressUtil.botp("aos_mkt_listing_sal", aos_mkt_listing_sal.get("id"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                MKTCom.SendGlobalMessage(MessageId, aos_mkt_listing_sal + "",
                        operationrst.getSuccessPkIds().get(0) + "", aos_mkt_listing_sal.getString("billno"), Message);
                FndHistory.Create(aos_mkt_listing_sal, aos_mkt_listing_sal.getString("aos_status"),
                        "Listing优化销售确认单-Listing优化子表自动创建");
            }
        }
    }

    private static void GenerateListingLanguageCA(DynamicObject dy_main, List<DynamicObject> ListingLanguage)
            throws FndError {
        // 信息参数
        String MessageId = null;
        String Message = "";
        // 异常参数
        int ErrorCount = 0;
        String ErrorMessage = "";
        // 数据层
        DynamicObject AosDesigner = dy_main.getDynamicObject("aos_designer");
        Object AosDesignerId = AosDesigner.getPkValue();
        Object billno = dy_main.get("billno");
        Object ReqFId = dy_main.getPkValue(); // 当前界面主键
        Object aos_type = dy_main.get("aos_type");// 任务类型
        Object aos_source = dy_main.get("aos_source");// 任务来源
        Object aos_importance = dy_main.get("aos_importance");// 紧急程度
        Object aos_requireby = dy_main.get("aos_requireby");// 设计需求表申请人
        Object aos_sourcetype = dy_main.get("aos_sourcetype");
        DynamicObject orgid_dyn = dy_main.getDynamicObject("aos_orgid");
        Object org_id = null;
        if (!Cux_Common_Utl.IsNull(orgid_dyn))
            org_id = orgid_dyn.get("id");
        Object aos_orgid = dy_main.get("aos_orgid");

        List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(((DynamicObject) aos_requireby).getPkValue());
        Object LastItemId = null;
        Boolean MessageFlag = false;
        Object aos_demandate = new Date();
        if ("四者一致".equals(aos_type)) {
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
        } else if ("老品优化".equals(aos_type))
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);

        // 校验
        if (ListingLanguage.size() == 0) {
            ErrorCount++;
            ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "小语种功能图翻译行信息不存在!");
        }
        if (ErrorCount > 0) {
            FndError fndMessage = new FndError(ErrorMessage);
            throw fndMessage;
        }

        // 循环所有行
        for (int i = 0; i < ListingLanguage.size(); i++) {
            DynamicObject aos_subentryentity_son = ListingLanguage.get(i)
                    .getDynamicObjectCollection("aos_subentryentity").get(0);
            String aos_orgtext = aos_subentryentity_son.getString("aos_orgtext");
            String[] aos_orgtextArray = aos_orgtext.split(";");

            for (int o = 0; o < aos_orgtextArray.length; o++) {
                MessageFlag = false;
                String org = aos_orgtextArray[o];
                if (!"CA".equals(org))
                    continue;

                // 如果头上国别为空 则国别为法国
                org_id = aos_sal_import_pub.get_import_id("FR", "bd_country");

                QFilter filter_org = new QFilter("aos_orgid.id", "=", org_id);
                QFilter filter_sourceid = new QFilter("aos_sourceid", "=", ReqFId);
                QFilter filter_cafr = new QFilter("aos_cafr", "=", true);
                QFilter[] filters = new QFilter[]{filter_sourceid, filter_org, filter_cafr};
                DynamicObject aos_mkt_listing_min = QueryServiceHelper.queryOne("aos_mkt_listing_min", "id", filters);

                if (aos_mkt_listing_min == null) {
                    aos_mkt_listing_min = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
                    aos_mkt_listing_min.set("aos_requireby", aos_requireby);
                    aos_mkt_listing_min.set("aos_requiredate", new Date());
                    aos_mkt_listing_min.set("aos_type", aos_type);
                    aos_mkt_listing_min.set("aos_source", aos_source);
                    aos_mkt_listing_min.set("aos_importance", aos_importance);
                    aos_mkt_listing_min.set("aos_designer", AosDesignerId);
                    aos_mkt_listing_min.set("aos_orignbill", billno);
                    aos_mkt_listing_min.set("aos_sourceid", ReqFId);
                    aos_mkt_listing_min.set("aos_status", "编辑确认");
                    aos_mkt_listing_min.set("aos_sourcetype", aos_sourcetype);
                    aos_mkt_listing_min.set("aos_orgid", org_id);
                    aos_mkt_listing_min.set("aos_demandate", aos_demandate);

                    aos_mkt_listing_min.set("aos_cafr", true);

                    // BOTP
                    aos_mkt_listing_min.set("aos_sourcebilltype", "aos_mkt_listing_son");
                    aos_mkt_listing_min.set("aos_sourcebillno", dy_main.get("billno"));
                    aos_mkt_listing_min.set("aos_srcentrykey", "aos_entryentity");

                    if (MapList != null) {
                        if (MapList.get(2) != null)
                            aos_mkt_listing_min.set("aos_organization1", MapList.get(2).get("id"));
                        if (MapList.get(3) != null)
                            aos_mkt_listing_min.set("aos_organization2", MapList.get(3).get("id"));
                    }
                    MessageFlag = true;
                } else {
                    long fid = aos_mkt_listing_min.getLong("id");
                    aos_mkt_listing_min = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_listing_min");
                }

                DynamicObjectCollection mkt_listing_minS = aos_mkt_listing_min
                        .getDynamicObjectCollection("aos_entryentity");
                DynamicObject aos_entryentity = ListingLanguage.get(i);
                DynamicObject mkt_listing_min = mkt_listing_minS.addNew();
                DynamicObject subentryentity = aos_entryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
                LastItemId = aos_entryentity.get("aos_itemid.id");
                mkt_listing_min.set("aos_itemid", aos_entryentity.get("aos_itemid"));
                mkt_listing_min.set("aos_is_saleout", ProgressUtil.Is_saleout(LastItemId));
                mkt_listing_min.set("aos_require", aos_entryentity.get("aos_require"));
                mkt_listing_min.set("aos_case", aos_entryentity.get("aos_case"));
                // 附件
                DynamicObjectCollection aos_attributefrom = aos_entryentity.getDynamicObjectCollection("aos_attribute");
                DynamicObjectCollection minEntityAttribute = mkt_listing_min
                        .getDynamicObjectCollection("aos_attribute");
                DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                for (DynamicObject attribute : aos_attributefrom) {
                    DynamicObject tempFile = BusinessDataServiceHelper
                            .loadSingle(attribute.getDynamicObject("fbasedataid").get("id"), type);
                    minEntityAttribute.addNew().set("fbasedataid", tempFile);
                }
                mkt_listing_min.set("aos_write", aos_entryentity.get("aos_write"));
                mkt_listing_min.set("aos_opt", aos_entryentity.get("aos_opt"));
                mkt_listing_min.set("aos_pic", aos_entryentity.get("aos_pic"));
                mkt_listing_min.set("aos_subtitle", aos_entryentity.get("aos_subtitle"));
                mkt_listing_min.set("aos_title", aos_entryentity.get("aos_title"));
                mkt_listing_min.set("aos_keyword", aos_entryentity.get("aos_keyword"));
                mkt_listing_min.set("aos_other", aos_entryentity.get("aos_other"));
                mkt_listing_min.set("aos_etc", aos_entryentity.get("aos_etc"));
                mkt_listing_min.set("aos_srcrowseq", aos_entryentity.get("SEQ"));

                DynamicObjectCollection aos_subentryentityS = mkt_listing_min
                        .getDynamicObjectCollection("aos_subentryentity");
                DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
                aos_subentryentity.set("aos_segment3", subentryentity.get("aos_segment3"));
                aos_subentryentity.set("aos_broitem", subentryentity.get("aos_broitem"));
                aos_subentryentity.set("aos_itemname", subentryentity.get("aos_itemname"));
                aos_subentryentity.set("aos_orgtext", subentryentity.get("aos_orgtext"));
                aos_subentryentity.set("aos_reqinput", aos_entryentity.get("aos_require"));

                mkt_listing_min.set("aos_segment3_r", subentryentity.get("aos_segment3"));
                mkt_listing_min.set("aos_broitem_r", subentryentity.get("aos_broitem"));
                mkt_listing_min.set("aos_itemname_r", subentryentity.get("aos_itemname"));
                mkt_listing_min.set("aos_orgtext_r",
                        ProgressUtil.getOrderOrg(aos_entryentity.getDynamicObject("aos_itemid").getPkValue()));

                // 根据循环中最后一个物料去获取对应的 英语编辑师 小语种编辑师 LastItemId
                String category = MKTCom.getItemCateNameZH(LastItemId);
                String[] category_group = category.split(",");
                String AosCategory1 = null;
                String AosCategory2 = null;
                int category_length = category_group.length;
                if (category_length > 0)
                    AosCategory1 = category_group[0];
                if (category_length > 1)
                    AosCategory2 = category_group[1];

                long aos_editor = RequestContext.get().getCurrUserId(); // 默认取当前提交人

                if (aos_editor == 0) {
                    ErrorMessage = FndError.AddErrorMessage(ErrorMessage,
                            AosCategory1 + "," + AosCategory2 + "英语编辑不存在!");
                    FndError fndMessage = new FndError(ErrorMessage);
                    throw fndMessage;
                }
                long aos_oueditor = 0;
                if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("")
                        && !AosCategory2.equals("")) {
                    DynamicObject aos_mkt_progorguser = ProgressUtil.minListtFindEditorByType(org_id, AosCategory1,
                            AosCategory2, aos_type.toString());
                    if (aos_mkt_progorguser != null) {
                        aos_oueditor = aos_mkt_progorguser.getLong("aos_user");
                    }
                }
                if (aos_oueditor == 0) {
                    ErrorMessage = FndError.AddErrorMessage(ErrorMessage,
                            AosCategory1 + "," + AosCategory2 + "小语种编辑师不存在!");
                    FndError fndMessage = new FndError(ErrorMessage);
                    throw fndMessage;
                }
                aos_mkt_listing_min.set("aos_editor", aos_editor);// 英语编辑师
                aos_mkt_listing_min.set("aos_editormin", aos_oueditor);// 小语种编辑师
                aos_mkt_listing_min.set("aos_user", aos_oueditor);
                MessageId = aos_oueditor + "";
                Message = "Listing优化需求表小语种-Listing优化需求子表自动创建";
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
                        new DynamicObject[]{aos_mkt_listing_min}, OperateOption.create());

                // 修复关联关系
                try {
                    ProgressUtil.botp("aos_mkt_listing_min", aos_mkt_listing_min.get("id"));
                } catch (Exception ex) {
                }

                if (operationrst.getValidateResult().getValidateErrors().size() != 0 && MessageFlag) {
                    MKTCom.SendGlobalMessage(MessageId, "aos_mkt_listing_min",
                            operationrst.getSuccessPkIds().get(0) + "", aos_mkt_listing_min.getString("billno"),
                            Message);
                    aos_mkt_listing_min = BusinessDataServiceHelper.loadSingle(operationrst.getSuccessPkIds().get(0),
                            "aos_mkt_listing_min");
                    ProgressUtil.botp("aos_mkt_listing_min", aos_mkt_listing_min.get("id"));
                    FndHistory.Create(aos_mkt_listing_min, aos_mkt_listing_min.getString("aos_status"),
                            "Listing优化需求表小语种-Listing优化需求文案自动创建");
                }
            }
        }
    }

    /**
     * 子表抄送小语种
     **/
    private static void GenerateListingLanguage(DynamicObject dy_main, List<DynamicObject> ListingLanguage)
            throws FndError {
        // 信息参数
        String MessageId = null;
        String Message = "";
        // 异常参数
        int ErrorCount = 0;
        String ErrorMessage = "";
        // 数据层
        DynamicObject AosDesigner = dy_main.getDynamicObject("aos_designer");
        Object AosDesignerId = AosDesigner.getPkValue();
        Object billno = dy_main.get("billno");
        Object ReqFId = dy_main.getPkValue(); // 当前界面主键
        Object aos_type = dy_main.get("aos_type");// 任务类型
        Object aos_source = dy_main.get("aos_source");// 任务来源
        Object aos_importance = dy_main.get("aos_importance");// 紧急程度
        Object aos_requireby = dy_main.get("aos_requireby");// 设计需求表申请人
        Object aos_sourcetype = dy_main.get("aos_sourcetype");
        DynamicObject orgid_dyn = dy_main.getDynamicObject("aos_orgid");
        Object org_id = null;
        if (!Cux_Common_Utl.IsNull(orgid_dyn))
            org_id = orgid_dyn.get("id");
        Object aos_orgid = dy_main.get("aos_orgid");

        List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(((DynamicObject) aos_requireby).getPkValue());
        Object LastItemId = null;
        Boolean MessageFlag = false;
        Object aos_demandate = new Date();
        if ("四者一致".equals(aos_type)) {
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
        } else if ("老品优化".equals(aos_type))
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);

        // 校验
        if (ListingLanguage.size() == 0) {
            ErrorCount++;
            ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "小语种功能图翻译行信息不存在!");
        }
        if (ErrorCount > 0) {
            FndError fndMessage = new FndError(ErrorMessage);
            throw fndMessage;
        }

        // 循环所有行
        for (int i = 0; i < ListingLanguage.size(); i++) {
            DynamicObject aos_subentryentity_son = ListingLanguage.get(i)
                    .getDynamicObjectCollection("aos_subentryentity").get(0);
            String aos_orgtext = aos_subentryentity_son.getString("aos_orgtext");
            String[] aos_orgtextArray = aos_orgtext.split(";");

            for (int o = 0; o < aos_orgtextArray.length; o++) {
                MessageFlag = false;
                String org = aos_orgtextArray[o];
                if ("US".equals(org) || "CA".equals(org) || "UK".equals(org))
                    continue;
                if (aos_orgid == null)
                    // 如果头上国别为空 则国别为下单国别
                    org_id = aos_sal_import_pub.get_import_id(org, "bd_country");
                else if (aos_orgid != null && !(((DynamicObject) aos_orgid).getString("number")).equals(org))
                    // 如果头上国别不为空 则只生成该国别数据
                    continue;
                QFilter filter_org = new QFilter("aos_orgid.id", "=", org_id);
                QFilter filter_sourceid = new QFilter("aos_sourceid", "=", ReqFId);
                QFilter[] filters = new QFilter[]{filter_sourceid, filter_org};
                DynamicObject aos_mkt_listing_min = QueryServiceHelper.queryOne("aos_mkt_listing_min", "id", filters);

                if (aos_mkt_listing_min == null) {
                    aos_mkt_listing_min = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
                    aos_mkt_listing_min.set("aos_requireby", aos_requireby);
                    aos_mkt_listing_min.set("aos_requiredate", new Date());
                    aos_mkt_listing_min.set("aos_type", aos_type);
                    aos_mkt_listing_min.set("aos_source", aos_source);
                    aos_mkt_listing_min.set("aos_importance", aos_importance);
                    aos_mkt_listing_min.set("aos_designer", AosDesignerId);
                    aos_mkt_listing_min.set("aos_orignbill", billno);
                    aos_mkt_listing_min.set("aos_sourceid", ReqFId);
                    aos_mkt_listing_min.set("aos_status", "编辑确认");
                    aos_mkt_listing_min.set("aos_sourcetype", aos_sourcetype);
                    aos_mkt_listing_min.set("aos_orgid", org_id);
                    aos_mkt_listing_min.set("aos_demandate", aos_demandate);

                    // BOTP
                    aos_mkt_listing_min.set("aos_sourcebilltype", "aos_mkt_listing_son");
                    aos_mkt_listing_min.set("aos_sourcebillno", dy_main.get("billno"));
                    aos_mkt_listing_min.set("aos_srcentrykey", "aos_entryentity");

                    if (MapList != null) {
                        if (MapList.get(2) != null)
                            aos_mkt_listing_min.set("aos_organization1", MapList.get(2).get("id"));
                        if (MapList.get(3) != null)
                            aos_mkt_listing_min.set("aos_organization2", MapList.get(3).get("id"));
                    }
                    MessageFlag = true;
                } else {
                    long fid = aos_mkt_listing_min.getLong("id");
                    aos_mkt_listing_min = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_listing_min");
                }

                DynamicObjectCollection mkt_listing_minS = aos_mkt_listing_min
                        .getDynamicObjectCollection("aos_entryentity");
                DynamicObject aos_entryentity = ListingLanguage.get(i);
                DynamicObject mkt_listing_min = mkt_listing_minS.addNew();
                DynamicObject subentryentity = aos_entryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
                LastItemId = aos_entryentity.get("aos_itemid.id");
                mkt_listing_min.set("aos_itemid", aos_entryentity.get("aos_itemid"));
                mkt_listing_min.set("aos_is_saleout", ProgressUtil.Is_saleout(LastItemId));
                mkt_listing_min.set("aos_require", aos_entryentity.get("aos_require"));
                mkt_listing_min.set("aos_case", aos_entryentity.get("aos_case"));

                // 附件
                DynamicObjectCollection aos_attributefrom = aos_entryentity.getDynamicObjectCollection("aos_attribute");
                DynamicObjectCollection minEntityAttribute = mkt_listing_min
                        .getDynamicObjectCollection("aos_attribute");
                DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                for (DynamicObject attribute : aos_attributefrom) {
                    DynamicObject tempFile = BusinessDataServiceHelper
                            .loadSingle(attribute.getDynamicObject("fbasedataid").get("id"), type);
                    minEntityAttribute.addNew().set("fbasedataid", tempFile);
                }
                mkt_listing_min.set("aos_write", aos_entryentity.get("aos_write"));
                mkt_listing_min.set("aos_opt", aos_entryentity.get("aos_opt"));
                mkt_listing_min.set("aos_pic", aos_entryentity.get("aos_pic"));
                mkt_listing_min.set("aos_subtitle", aos_entryentity.get("aos_subtitle"));
                mkt_listing_min.set("aos_title", aos_entryentity.get("aos_title"));
                mkt_listing_min.set("aos_keyword", aos_entryentity.get("aos_keyword"));
                mkt_listing_min.set("aos_other", aos_entryentity.get("aos_other"));
                mkt_listing_min.set("aos_etc", aos_entryentity.get("aos_etc"));
                mkt_listing_min.set("aos_srcrowseq", aos_entryentity.get("SEQ"));

                DynamicObjectCollection aos_subentryentityS = mkt_listing_min
                        .getDynamicObjectCollection("aos_subentryentity");
                DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
                aos_subentryentity.set("aos_segment3", subentryentity.get("aos_segment3"));
                aos_subentryentity.set("aos_broitem", subentryentity.get("aos_broitem"));
                aos_subentryentity.set("aos_itemname", subentryentity.get("aos_itemname"));
                aos_subentryentity.set("aos_orgtext", subentryentity.get("aos_orgtext"));
                aos_subentryentity.set("aos_reqinput", aos_entryentity.get("aos_require"));

                mkt_listing_min.set("aos_segment3_r", subentryentity.get("aos_segment3"));
                mkt_listing_min.set("aos_broitem_r", subentryentity.get("aos_broitem"));
                mkt_listing_min.set("aos_itemname_r", subentryentity.get("aos_itemname"));
                mkt_listing_min.set("aos_orgtext_r",
                        ProgressUtil.getOrderOrg(aos_entryentity.getDynamicObject("aos_itemid").getPkValue()));

                // 根据循环中最后一个物料去获取对应的 英语编辑师 小语种编辑师 LastItemId
                String category = MKTCom.getItemCateNameZH(LastItemId);
                String[] category_group = category.split(",");
                String AosCategory1 = null;
                String AosCategory2 = null;
                int category_length = category_group.length;
                if (category_length > 0)
                    AosCategory1 = category_group[0];
                if (category_length > 1)
                    AosCategory2 = category_group[1];

                long aos_editor = RequestContext.get().getCurrUserId(); // 默认取当前提交人
                /*
                 * if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("")
                 * && !AosCategory2.equals("")) { QFilter filter_category1 = new
                 * QFilter("aos_category1", "=", AosCategory1); QFilter filter_category2 = new
                 * QFilter("aos_category2", "=", AosCategory2); QFilter[] filters_category = new
                 * QFilter[] { filter_category1, filter_category2 }; String SelectStr =
                 * "aos_eng aos_editor"; DynamicObject aos_mkt_proguser =
                 * QueryServiceHelper.queryOne("aos_mkt_proguser", SelectStr, filters_category);
                 * if (aos_mkt_proguser != null) { aos_editor =
                 * aos_mkt_proguser.getLong("aos_editor"); } }
                 */

                if (aos_editor == 0) {
                    ErrorMessage = FndError.AddErrorMessage(ErrorMessage,
                            AosCategory1 + "," + AosCategory2 + "英语编辑不存在!");
                    FndError fndMessage = new FndError(ErrorMessage);
                    throw fndMessage;
                }
                long aos_oueditor = 0;
                if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("")
                        && !AosCategory2.equals("")) {
                    DynamicObject aos_mkt_progorguser = ProgressUtil.minListtFindEditorByType(org_id, AosCategory1,
                            AosCategory2, aos_type.toString());
                    if (aos_mkt_progorguser != null) {
                        aos_oueditor = aos_mkt_progorguser.getLong("aos_user");
                    }
                }
                if (aos_oueditor == 0) {
                    ErrorMessage = FndError.AddErrorMessage(ErrorMessage,
                            AosCategory1 + "," + AosCategory2 + "小语种编辑师不存在!");
                    FndError fndMessage = new FndError(ErrorMessage);
                    throw fndMessage;
                }
                aos_mkt_listing_min.set("aos_editor", aos_editor);// 英语编辑师
                aos_mkt_listing_min.set("aos_editormin", aos_oueditor);// 小语种编辑师
                aos_mkt_listing_min.set("aos_user", aos_oueditor);

                MessageId = aos_oueditor + "";
                Message = "Listing优化需求表小语种-Listing优化需求子表自动创建";

                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
                        new DynamicObject[]{aos_mkt_listing_min}, OperateOption.create());


                // 修复关联关系
                try {
                    ProgressUtil.botp("aos_mkt_listing_min", aos_mkt_listing_min.get("id"));
                } catch (Exception ex) {
                }


                if (operationrst.getValidateResult().getValidateErrors().size() != 0 && MessageFlag) {
                    MKTCom.SendGlobalMessage(MessageId, "aos_mkt_listing_min",
                            operationrst.getSuccessPkIds().get(0) + "", aos_mkt_listing_min.getString("billno"),
                            Message);
                    aos_mkt_listing_min = BusinessDataServiceHelper.loadSingle(operationrst.getSuccessPkIds().get(0),
                            "aos_mkt_listing_min");
                    ProgressUtil.botp("aos_mkt_listing_min", aos_mkt_listing_min.get("id"));
                    FndHistory.Create(aos_mkt_listing_min, aos_mkt_listing_min.getString("aos_status"),
                            "Listing优化需求表小语种-Listing优化需求文案自动创建");
                }
            }
        }
    }

    /**
     * 物料改变时，带出相关信息
     *
     * @param dy_row 对应的行数据
     * @param row    改变的行数
     */
    private static void ItemChanged(@NotNull DynamicObject dy_row, @NotNull int row) {
        if (dy_row == null || row < 0)
            return;
        // 清除子单据信息
//		SubEntityDeleteRows(dy_row,"aos_subentryentity");
        DynamicObjectCollection aos_subentryentityS = dy_row.getDynamicObjectCollection("aos_subentryentity");
        DynamicObject aos_subentryentity;
        if (aos_subentryentityS.size() == 0)
            aos_subentryentity = aos_subentryentityS.addNew();
        else
            aos_subentryentity = aos_subentryentityS.get(0);
        if (dy_row.get("aos_itemid") == null) {
            dy_row.set("aos_segment3_r", "");
            dy_row.set("aos_broitem_r", "");
            dy_row.set("aos_itemname_r", "");
            dy_row.set("aos_orgtext_r", "");
            aos_subentryentity.set("aos_segment3", "");
            aos_subentryentity.set("aos_broitem", "");
            aos_subentryentity.set("aos_itemname", "");
            aos_subentryentity.set("aos_orgtext", "");
        } else {
            Object itemid = dy_row.getDynamicObject("aos_itemid").get("id");
            // 查找物料信息
            DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(itemid, "bd_material");
            String aos_productno = bd_material.getString("aos_productno");
            String aos_itemname = bd_material.getString("name");
            String item_number = bd_material.getString("number");
            String aos_orgtext = ProgressUtil.getOrderOrg(itemid);

            // 获取同产品号物料
            QFilter filter_productno = new QFilter("aos_productno", QCP.equals, aos_productno);
            QFilter[] filters = new QFilter[]{filter_productno};
            String SelectColumn = "number,aos_type";
            String aos_broitem = "";
            DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectColumn, filters);
            for (DynamicObject bd : bd_materialS) {
                if ("B".equals(bd.getString("aos_type")))
                    continue; // 配件不获取
                String number = bd.getString("number");
                if (item_number.equals(number))
                    continue;
                else
                    aos_broitem = aos_broitem + number + ";";
            }

            aos_subentryentity.set("aos_segment3", aos_productno);
            aos_subentryentity.set("aos_broitem", aos_broitem);
            aos_subentryentity.set("aos_itemname", aos_itemname);
            aos_subentryentity.set("aos_orgtext", aos_orgtext);
            dy_row.set("aos_segment3_r", aos_productno);
            dy_row.set("aos_broitem_r", aos_broitem);
            dy_row.set("aos_itemname_r", aos_itemname);
            dy_row.set("aos_orgtext_r", aos_orgtext);
        }
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
        int RowIndex = hyperLinkClickEvent.getRowIndex();
        String FieldName = hyperLinkClickEvent.getFieldName();
        if ("aos_segment3_r".equals(FieldName)) {
            Object aos_segment3_r = this.getModel().getValue("aos_segment3_r", RowIndex);
            DynamicObject aos_mkt_functreq = QueryServiceHelper.queryOne("aos_mkt_functreq", "id",
                    new QFilter[]{new QFilter("aos_segment3", QCP.equals, aos_segment3_r)});
            if (!Cux_Common_Utl.IsNull(aos_mkt_functreq))
                Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_functreq", aos_mkt_functreq.get("id"));
            else
                this.getView().showErrorNotification("功能图需求表信息不存在!");
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
        this.addItemClickListeners("aos_submit"); // 提交
        this.addItemClickListeners("aos_back"); // 编辑退回
        this.addItemClickListeners("aos_open"); // 打开来源流程
        this.addItemClickListeners("aos_product");
        EntryGrid entryGrid = this.getControl("aos_entryentity");
        entryGrid.addHyperClickListener(this);
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String Control = evt.getItemKey();
        try {
            if ("aos_submit".equals(Control)) {
                DynamicObject dy_main = this.getModel().getDataEntity(true);
                aos_submit(dy_main, "A");
            } else if ("aos_back".equals(Control))
                aos_back();
            else if ("aos_history".equals(Control))
                aos_history();// 查看历史记录
            else if ("aos_open".equals(Control))
                aos_open();
            else if ("aos_product".equals(Control))
                aos_product();
            else if ("aos_close".equals(Control))
                aos_close();
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    private void aos_close() {
        String KEY_CANCEL = "bar_cancel";
        ConfirmCallBackListener confirmCallBackListener = new ConfirmCallBackListener(KEY_CANCEL, this);
        // 设置页面确认框，参数为：标题，选项框类型，回调监听
        this.getView().showConfirm("您确认关闭此申请单吗？", MessageBoxOptions.YesNo, confirmCallBackListener);
    }

    @Override
    public void confirmCallBack(MessageBoxClosedEvent event) {
        super.confirmCallBack(event);
        String callBackId = event.getCallBackId();
        if (callBackId.equals("bar_cancel")) {
            if (event.getResult().equals(MessageBoxResult.Yes)) {
                this.getModel().setValue("aos_user", system);
                this.getModel().setValue("aos_status", "结束");
                this.getView().invokeOperation("save");
                this.getView().invokeOperation("refresh");
                FndHistory.Create(this.getView(), "手工关闭", "手工关闭");
                StatusControl();
                setErrorList(this.getModel().getDataEntity(true));
            }
        }
    }

    private void aos_product() {
        Object aos_productid = this.getModel().getValue("aos_productid");
        DynamicObject aos_prodatachan = QueryServiceHelper.queryOne("aos_prodatachan", "id",
                new QFilter[]{new QFilter("id", QCP.equals, aos_productid)});
        if (!Cux_Common_Utl.IsNull(aos_prodatachan))
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_prodatachan", aos_prodatachan.get("id"));
        else
            this.getView().showErrorNotification("产品资料变跟单信息不存在!");
    }

    /**
     * 值改变事件
     **/
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (name.equals("aos_case") || name.equals("aos_caseinput"))
            SyncInput(name);
        else if (name.equals("aos_type")) {
            AosTypeChanged();
        } else if (name.equals("aos_itemid")) {
            int row = e.getChangeSet()[0].getRowIndex();
            if (row >= 0) {
                DynamicObject dy_row = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity")
                        .get(row);
                ItemChanged(dy_row, row);
                this.getView().updateView("aos_entryentity", row);
            }
        }
    }

    /**
     * 任务类型值改变事件
     **/
    private void AosTypeChanged() {
        Object aos_type = this.getModel().getValue("aos_type");
        Object aos_demandate = new Date();
        if ("四者一致".equals(aos_type)) {
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
        } else if ("老品优化".equals(aos_type))
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
        this.getModel().setValue("aos_demandate", aos_demandate);
    }

    private void SyncInput(String name) {
        int aos_entryentity = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
        if (name.equals("aos_case"))
            this.getModel().setValue("aos_caseinput", this.getModel().getValue("aos_case"), 0);
        else if (name.equals("aos_caseinput"))
            this.getModel().setValue("aos_case", this.getModel().getValue("aos_caseinput"), aos_entryentity);
    }

    /**
     * 打开来源流程
     **/
    private void aos_open() {
        Object aos_sourceid = this.getModel().getValue("aos_sourceid");
        Object aos_sourcetype = this.getModel().getValue("aos_sourcetype");
        if ("LISTING".equals(aos_sourcetype))
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_req", aos_sourceid);
        else if ("DESIGN".equals(aos_sourcetype))
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designreq", aos_sourceid);
        else if ("VED".equals(aos_sourcetype))
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_photoreq", aos_sourceid);
        else if ("PRODUCT".equals(aos_sourcetype))
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_prodatachan", aos_sourceid);
    }

    /**
     * 初始化事件
     **/
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        StatusControl();
        // 获取当前操作人
        Object CurrentUserId = UserServiceHelper.getCurrentUserId();
        Object aos_sendto = this.getModel().getValue("aos_sendto");
        Object aos_sendate = this.getModel().getValue("aos_sendate");

        if (FndGlobal.IsNull(aos_sendate) && FndGlobal.IsNotNull(aos_sendto)
                && CurrentUserId.toString().equals(((DynamicObject)aos_sendto).getPkValue().toString()))
        {
            this.getModel().setValue("aos_sendate", new Date());
            this.getView().invokeOperation("save");
            this.getView().invokeOperation("refresh");
        }
    }

    /**
     * 新建事件
     **/
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        StatusControl();// 界面控制
    }

    /**
     * 界面关闭事件
     **/
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }

    /**
     * 提交
     **/
    public void aos_submit(DynamicObject dy_main, String type) throws FndError {
        SaveControl(dy_main);// 先做数据校验判断是否可以提交
        String aos_status = dy_main.getString("aos_status");// 根据状态判断当前流程节点
        Object aos_osconfirmlov = dy_main.get("aos_osconfirmlov");
        switch (aos_status) {
            case "编辑确认":
                if ("是".equals(aos_osconfirmlov))
                    SubmitToOsConfirm(dy_main);
                else
                    SubmitForEditor(dy_main);
                fillDesign(dy_main);
                break;
            case "海外确认":
                SubmitForEditor(dy_main);
                break;
            case "申请人":
                SubmitForApply(dy_main);
                break;
        }
        SaveServiceHelper.save(new DynamicObject[]{dy_main});
        setListSonUserOrganizate(dy_main);
        FndHistory.Create(dy_main, "提交", aos_status);
        if (type.equals("A")) {
            this.getView().updateView();
            StatusControl();// 提交完成后做新的界面状态控制
        }
    }

    /**
     * 全局状态控制
     **/
    private void StatusControl() {
        // 数据层
        Object AosStatus = this.getModel().getValue("aos_status");
        Object AosUser = this.getModel().getValue("aos_user");
        String AosUserId = null;
        if (AosUser instanceof String)
            AosUserId = (String) AosUser;
        else if (AosUser instanceof Long)
            AosUserId = AosUser + "";
        else
            AosUserId = ((DynamicObject) AosUser).getString("id");
        Object CurrentUserId = UserServiceHelper.getCurrentUserId();
        // Object CurrentUserName = UserServiceHelper.getUserInfoByID((long)
        // CurrentUserId).get("name");
        // 图片控制
        // InitPic();
        // 锁住需要控制的字段
        this.getView().setVisible(false, "aos_back");
        this.getView().setVisible(true, "bar_save");
        // 当前节点操作人不为当前用户 全锁
        if (!AosUserId.equals(CurrentUserId.toString())) {
            this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
            this.getView().setEnable(false, "bar_save");
            this.getView().setEnable(false, "aos_submit");
            this.getView().setEnable(false, "aos_close");
        }
        // 状态控制
        Map<String, Object> map = new HashMap<>();
        if ("编辑确认".equals(AosStatus)) {
            map.put(ClientProperties.Text, new LocaleString("编辑确认"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
        } else if ("海外确认".equals(AosStatus)) {
            map.put(ClientProperties.Text, new LocaleString("海外确认"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
        } else if ("申请人".equals(AosStatus)) {
            map.put(ClientProperties.Text, new LocaleString("提交"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
        } else if ("结束".equals(AosStatus)) {
            this.getView().setVisible(false, "aos_submit");
            this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(false, "aos_close");
        }
    }

    /**
     * 打开历史记录
     **/
    private void aos_history() throws FndError {
        Cux_Common_Utl.OpenHistory(this.getView());
    }

    /**
     * 编辑退回
     **/
    private void aos_back() {
        String MessageId = null;
        String Message = "Listing优化需求表子表-编辑退回";
        Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
        Object billno = this.getModel().getValue("billno");
        Object aos_requireby = this.getModel().getValue("aos_requireby");
        MessageId = aos_requireby + "";
        this.getModel().setValue("aos_status", "申请人");// 设置单据流程状态
        this.getModel().setValue("aos_user", aos_requireby);// 流转给编辑
        setListSonUserOrganizate(this.getModel().getDataEntity(true));
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");
        MKTCom.SendGlobalMessage(MessageId, "aos_mkt_listing_son", ReqFId + "", billno + "", Message);
        FndHistory.Create(this.getView(), "编辑退回", "编辑退回");
    }
}
