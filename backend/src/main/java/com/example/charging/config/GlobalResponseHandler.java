package com.example.charging.config;

import com.example.charging.common.ApiResponse;
import com.example.charging.common.IgnoreResponseWrap;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局统一响应包装器。
 * 所有 Controller 返回值都会被自动包装为：
 * { "code": 0, "message": "success", "data": ... }
 *
 * - 如果返回值已经是 ApiResponse，原样返回；
 * - 如果方法/类标注了 @IgnoreResponseWrap，则不包装；
 * - 其余情况统一包装。
 */
@ControllerAdvice(basePackages = "com.example.charging.controller")
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        if (returnType.getMethodAnnotation(IgnoreResponseWrap.class) != null) {
            return false;
        }
        if (returnType.getContainingClass().isAnnotationPresent(IgnoreResponseWrap.class)) {
            return false;
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body == null) {
            return ApiResponse.success();
        }
        if (body instanceof ApiResponse) {
            return body;
        }
        return ApiResponse.success(body);
    }
}
