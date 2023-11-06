package mkt.openapi.ListingReq;

import java.io.Serializable;

/**
 * @author create by gk
 * @date 2022/12/12 13:39
 * @action
 */
public class Result implements Serializable {
    private int index;
    private Boolean status;
    private String errors;
    private String OAProcessNo;
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getErrors() {
        return errors;
    }

    public void setErrors(String errors) {
        this.errors = errors;
    }

    public String getOAProcessNo() {
        return OAProcessNo;
    }

    public void setOAProcessNo(String OAProcessNo) {
        this.OAProcessNo = OAProcessNo;
    }
}
