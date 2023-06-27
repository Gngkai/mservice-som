package mkt.openapi.ListingReq;

import kd.bos.openapi.common.custom.annotation.ApiParam;

import java.io.Serializable;

/**
 * @author create by gk
 * @date 2022/12/9 10:58
 * @action
 */
public class DetailModel implements Serializable {
    @ApiParam("货号")
    private String aos_item;
    @ApiParam("申请人文案要求")
    private String aos_require;
    @ApiParam("申请人图片要求")
    private String aos_requirepic;
    @ApiParam("申请人需求附件")
    private String aos_attribute;

    public String getAos_item() {
        return aos_item;
    }

    public void setAos_item(String aos_item) {
        this.aos_item = aos_item;
    }

    public String getAos_require() {
        return aos_require;
    }

    public void setAos_require(String aos_require) {
        this.aos_require = aos_require;
    }

    public String getAos_requirepic() {
        return aos_requirepic;
    }

    public void setAos_requirepic(String aos_requirepic) {
        this.aos_requirepic = aos_requirepic;
    }

    public String getAos_attribute() {
        return aos_attribute;
    }

    public void setAos_attribute(String aos_attribute) {
        this.aos_attribute = aos_attribute;
    }

    @Override
    public String toString() {
        return "DetailModel{" +
                "aos_item='" + aos_item + '\'' +
                ", aos_require='" + aos_require + '\'' +
                ", aos_requirepic='" + aos_requirepic + '\'' +
                ", aos_attribute='" + aos_attribute + '\'' +
                '}';
    }
}
