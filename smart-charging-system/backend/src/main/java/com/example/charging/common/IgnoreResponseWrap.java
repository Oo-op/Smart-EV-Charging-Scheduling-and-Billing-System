package com.example.charging.common;

/**
 * 用于标记接口不需要全局响应包装（例如文件下载接口）。
 * 默认所有 Controller 返回值都会被包装为 ApiResponse，
 * 若某个方法需要自定义返回结构，可在方法或类上标注 @IgnoreResponseWrap。
 */
public @interface IgnoreResponseWrap {
}
