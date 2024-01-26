package mkt.openapi.ListingReq;

import common.Cux_Common_Utl;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.TempFileCache;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.attachment.AttachmentFieldServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.progress.iface.ItemInfoUtil;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.*;

/**
 * @author create by gk
 * @date 2022/12/9 16:17
 * @action
 */
public class utils {
    public static String orgID (String number) {
        if (Cux_Common_Utl.IsNull(number))
            return null;
        QFilter filter_OreNumber = new QFilter("number", "=", number);
        DynamicObject dy = QueryServiceHelper.queryOne("bd_country", "id", new QFilter[]{filter_OreNumber});
        if (dy != null)
            return dy.getString("id");
        return "";
    }
    public static String getUser(String number){
        if (Cux_Common_Utl.IsNull(number)) {
            return null;
        }
        QFilter filter_number = new QFilter("number","=",number);
        DynamicObject dy = QueryServiceHelper.queryOne("bos_user", "id", new QFilter[]{filter_number});
        if (dy!=null)
            return dy.getString("id");
        return null;
    }
    public static DynamicObject getItem(String number){
        QFilter filter = new QFilter("number","=",number);
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("number");
        str.add("name");
        str.add("aos_productno");
        str.add("aos_contryentry.aos_nationality");
        str.add("aos_contryentry.aos_contryentrystatus");
        DynamicObject[] bd_materials = BusinessDataServiceHelper.load("bd_material", str.toString(), new QFilter[]{filter});
        if (bd_materials.length>0)
            return bd_materials[0];
        return null;
    }

    /** 下单国别**/
    public static String getOrderCountry (DynamicObject bd_material){
        DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
        StringJoiner str= new StringJoiner(";");
        // 字符串拼接
        for (DynamicObject aos_contryentry : aos_contryentryS) {
            DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
            String aos_nationalitynumber = aos_nationality.getString("number");
            if ("IE".equals(aos_nationalitynumber))
                continue;
            Object org_id = aos_nationality.get("id"); // ItemId
            int OsQty = ItemInfoUtil.getItemOsQty(org_id, bd_material.get("id"));
            int SafeQty = ItemInfoUtil.getSafeQty(org_id);
            //终止、小于安全库存
            if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
                continue;
            //代卖、小于安全库存
            if ("F".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
                continue;
            // 虚拟上架、小于安全库存
            if ("H".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
                continue;
            
            str.add(aos_nationalitynumber);
        }
        return str.toString();
    }
    /**同产品号 **/
    public static String getBroItem (String item_number,String aos_productno){
        // 获取同产品号物料
        QFilter filter_productno = new QFilter("aos_productno", QCP.equals, aos_productno);
        QFilter[] filters = new QFilter[] { filter_productno };
        String SelectColumn = "number,aos_type";
        StringJoiner aos_broitem = new StringJoiner(";");

        if (!Cux_Common_Utl.IsNull(aos_productno)) {
            DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectColumn, filters);
            for (DynamicObject bd : bd_materialS) {
                if ("B".equals(bd.getString("aos_type")))
                    continue; // 配件不获取
                String number = bd.getString("number");
                if (item_number.equals(number))
                    continue;
                else
                   aos_broitem.add(number);
            }
        }
        return aos_broitem.toString();
    }
    /**
     * @Description: base64转文件.
     * @Param: [strBase64 文件base64字符串, outFile 输入文件路径]
     * @return: boolean
     * @Date: 2020/12/14
     */
    public static boolean base64ToFile(String strBase64, File outFile) {
        try {
            // 解码，然后将字节转换为文件 // 将字符串转换为byte数组
            byte[] bytes = java.util.Base64.getDecoder().decode(strBase64);
            return copyByte2File(bytes, outFile);
        } catch (Exception ioe) {
            ioe.printStackTrace();
            return false;
        }
    }
    /**
     * 将字节码转为文件
     */
    private static boolean copyByte2File(byte[] bytes, File file) {
        FileOutputStream out = null;
        try {
            // 转化为输入流
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            // 写出文件
            byte[] buffer = new byte[1024];
            out = new FileOutputStream(file);
            // 写文件
            int len = 0;
            while ((len = in.read(buffer)) != -1) {
                // 文件写操作
                out.write(buffer, 0, len);
            }
            return true;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return false;
    }
    /**
     * 为单据体附件字段上传附件, 并赋值
     *
     * @param dynamicObject   单据对象
     * @param entry           某一行分录对象
     * @param attachmentField 分录附件字段标识
     * @param entryFileList   文件列表
     */
    public static void addAttachmentForEntry(DynamicObject dynamicObject, DynamicObject entry, String attachmentField, List<File> entryFileList) {
        List<DynamicObject> entryAttaList = uploadFiles(dynamicObject.getDataEntityType().getName(), dynamicObject.getPkValue(), attachmentField, entryFileList);
        // 获取单据体中每一行附件字段的值
        DynamicObjectCollection entryAttaFieldColl = entry.getDynamicObjectCollection(attachmentField);
        for (DynamicObject attaObj : entryAttaList) {
            // 构造单据体中附件字段的数据
            DynamicObject tempAtta = new DynamicObject(entryAttaFieldColl.getDynamicObjectType());
            tempAtta.set("fbasedataId", attaObj);
            entryAttaFieldColl.add(tempAtta);
        }
        // 为单据体每一行的附件字段赋值
        entry.set(attachmentField, entryAttaFieldColl);
    }
    /**
     * 上传附件
     *
     * @param entityNumber 单据标识
     * @param billPkId     单据主键ID
     * @param attaFieldKey 附件字段标识
     * @param files        待上传的文件列表
     */
    public static List<DynamicObject> uploadFiles(String entityNumber, Object billPkId, String attaFieldKey, List<File> files) {
        List<Map<String, Object>> attachmentList = new ArrayList<>();
        for (File file : files) {
            Map<String, Object> attachment = uploadFilesToCache(entityNumber, billPkId, attaFieldKey, file);
            attachmentList.add(attachment);
        }
        // 构造一个虚拟页面pageId
        // 该值先在 AttachmentFieldServiceHelper.saveAttachments 方法中存到表  t_bd_attachment 中,
        // 然后在 AttachmentFieldServiceHelper.saveTempAttachments 方法中被改写为 ""
        String pageId = UUID.randomUUID().toString().replace("-", "");
        // 将附件字段中各附件的相关信息将存储至表  t_bd_attachment (即:附件字段实体bd_attachment)中
        List<DynamicObject> attaList = AttachmentFieldServiceHelper.saveAttachments(null, pageId, attachmentList);
        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("PkId", billPkId);
        paramMap.put("PageId", pageId);
        String paramStr = SerializationUtils.toJsonString(paramMap);
        // 将缓存中的文件流持久化到文件服务器上, 并更新其附件字段信息(bd_attachment)
        AttachmentFieldServiceHelper.saveTempAttachments(paramStr);
        return attaList;
    }
    /**
     * 上传附件至临时文件服务器
     *
     * @param entityNumber 单据主键ID
     * @param billPkId     单据主键ID
     * @param attaFieldKey 附件字段标识
     * @param file         待上传的文件
     */
    public static Map<String, Object> uploadFilesToCache(String entityNumber, Object billPkId, String attaFieldKey, File file) {
        TempFileCache cache = CacheFactory.getCommonCacheFactory().getTempFileCache();
        InputStream in = null;
        String url;
        int size;
        // 构造附件字段各附件信息
        Map<String, Object> attachment = new HashMap<>();
        try {
            in = new FileInputStream(file);
            // 获取文件大小
            size = in.available();
            url = cache.saveAsUrl(file.getName(), in, 7200);
            String prefix = RequestContext.get().getClientFullContextPath();
            if (!prefix.endsWith("/")) {
                prefix = prefix + "/";
            }
            url = prefix + url;
            attachment.put("name", file.getName());
            attachment.put("size", size);
            attachment.put("uid", "rc-upload-" + new Date().getTime() + "-" + (int) (Math.random() * 100));
            attachment.put("url", url);
            attachment.put("status", "success");
            attachment.put("entityNum", entityNumber);
            attachment.put("billPkId", billPkId.toString());
            attachment.put("attKey", attaFieldKey);
            attachment.put("client", "web");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(in);
        }
        return attachment;
    }
}
