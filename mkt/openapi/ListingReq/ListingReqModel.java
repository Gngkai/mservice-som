package mkt.openapi.ListingReq;

import kd.bos.openapi.common.custom.annotation.ApiModel;
import kd.bos.openapi.common.custom.annotation.ApiParam;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * @author create by gk
 * @date 2022/12/7 17:19
 * @action
 */
@ApiModel
public class ListingReqModel implements Serializable {

    private static final long serialVersionUID = -2287273230483521474L;
    @ApiParam("国别编码")
    private String aos_org;
    @ApiParam("申请人")
    private String aos_user;
    @ApiParam("任务类型")
    private String aos_type;
    @ApiParam("紧急程度")
    private String aos_importance;
    @ApiParam("设计师工号")
    private String aos_designer;
    @ApiParam("编辑师")
    private String aos_editor;
    @ApiParam("明细行")
    private String detail;
    public ListingReqModel() {
    }

    public String getAos_org() {
        return aos_org;
    }

    public void setAos_org(String aos_org) {
        this.aos_org = aos_org;
    }

    public String getAos_type() {
        return aos_type;
    }

    public void setAos_type(String aos_type) {
        this.aos_type = aos_type;
    }

    public String getAos_importance() {
        return aos_importance;
    }

    public void setAos_importance(String aos_importance) {
        this.aos_importance = aos_importance;
    }

    public String getAos_designer() {
        return aos_designer;
    }

    public void setAos_designer(String aos_designer) {
        this.aos_designer = aos_designer;
    }

    public String getAos_editor() {
        return aos_editor;
    }

    public void setAos_editor(String aos_editor) {
        this.aos_editor = aos_editor;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getAos_user() {
        return aos_user;
    }
    public void setAos_user(String aos_user) {
        this.aos_user = aos_user;
    }

    @Override
    public String toString() {
        return "ListingReqModel{" +
                "aos_org='" + aos_org + '\'' +
                ", aos_type='" + aos_type + '\'' +
                ", aos_importance='" + aos_importance + '\'' +
                ", aos_designer='" + aos_designer + '\'' +
                ", aos_editor='" + aos_editor + '\'' +
                ", detail='" + detail + '\'' +
                '}';
    }




}
