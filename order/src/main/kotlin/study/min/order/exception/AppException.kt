package study.min.order.exception

class AppException(code: AppExceptionCode, message: String) : RuntimeException(message)