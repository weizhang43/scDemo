package response;

import lombok.Data;

import java.util.List;

@Data
public class ResponseDto<T> {
    private Integer code;

    private String msg;

    private List<T> dataList;

    private Object daoResult;

    public static <T>ResponseDto<T> success(List<T> dataList){
        ResponseDto responseDto = new ResponseDto();
        responseDto.setCode(200);
        responseDto.setDataList(dataList);
        return responseDto;
    }

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
}
