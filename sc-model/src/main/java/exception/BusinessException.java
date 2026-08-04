package exception;

/**
 * 业务异常：携带面向用户的提示语，由各服务的全局异常处理器转成 ResponseDto.error 返回。
 * 用于需要触发事务回滚的业务失败场景（普通 return error 不会回滚已执行的写操作）。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
