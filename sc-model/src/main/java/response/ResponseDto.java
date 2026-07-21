package response;

import com.curry.model.Product;
import lombok.Data;

import java.util.List;

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
     * 成功返回列表结果，code=200。
     * @param dataList 数据列表
     */
    public static <T>ResponseDto<T> success(List<T> dataList){
        ResponseDto responseDto = new ResponseDto();
        responseDto.setCode(200);
        responseDto.setDataList(dataList);
        return responseDto;
    }

    /**
     * 成功返回单个对象结果，code=200。
     * @param daoResult 业务对象
     */
    public static <T>ResponseDto<T> success(Object daoResult){
        ResponseDto responseDto = new ResponseDto();
        responseDto.setCode(200);
        responseDto.setDaoResult(daoResult);
        return responseDto;
    }

    /**
     * 失败返回
     * @param msg
     * @return
     * @param <T>
     */
    public static <T>ResponseDto<T> error(String msg){
        ResponseDto responseDto = new ResponseDto();
        responseDto.setCode(500);
        responseDto.setMsg(msg);
        return responseDto;
    }


    public static <T>ResponseDto<T> success(){
        ResponseDto responseDto = new ResponseDto();
        responseDto.setCode(200);
        return responseDto;
    }
}
