package study.min.product.exception

class AppException(code: AppExceptionCode, message: String) : RuntimeException(message)