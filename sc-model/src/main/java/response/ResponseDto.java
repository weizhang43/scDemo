package response;

import lombok.Data;

import java.util.List;

/**
 * 通用响应体：code + msg + 列表结果 / 单对象结果。
 * @param <T> 列表结果元素类型
 */
@Data
public class ResponseDto<T> {
    private Integer code;

    private String msg;

    private List<T> dataList;

    private Object daoResult;

    /**
     * 请求成功状态码
     */
    public static final Integer SUCCESS_CODE = 200;

    /**
     * 请求失败状态码
     */
    public static final Integer ERROR_CODE = 500;

    /**
     * 成功返回列表结果，code=200。
     * @param dataList 数据列表
     */
    public static <T> ResponseDto<T> success(List<T> dataList) {
        ResponseDto<T> responseDto = new ResponseDto<>();
        responseDto.setCode(SUCCESS_CODE);
        responseDto.setDataList(dataList);
        return responseDto;
    }

    /**
     * 成功返回单个对象结果，code=200。
     * @param daoResult 业务对象
     */
    public static <T> ResponseDto<T> success(Object daoResult) {
        ResponseDto<T> responseDto = new ResponseDto<>();
        responseDto.setCode(SUCCESS_CODE);
        responseDto.setDaoResult(daoResult);
        return responseDto;
    }

    /**
     * 失败返回，code=500。
     * @param msg 面向调用方的错误提示
     */
    public static <T> ResponseDto<T> error(String msg) {
        ResponseDto<T> responseDto = new ResponseDto<>();
        responseDto.setCode(ERROR_CODE);
        responseDto.setMsg(msg);
        return responseDto;
    }

    /**
     * 成功返回（无数据体），code=200。
     */
    public static <T> ResponseDto<T> success() {
        ResponseDto<T> responseDto = new ResponseDto<>();
        responseDto.setCode(SUCCESS_CODE);
        return responseDto;
    }
}
