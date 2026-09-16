package com.desktopcat.server.record.dto;

import java.util.List;

/**
 * 文章分页接口返回的数据。
 *
 * <p>{@code record} 适合这种只承载数据的对象：Java 会自动生成全参数构造方法、
 * {@code items()} 等访问方法，以及 {@code equals}、{@code hashCode} 和
 * {@code toString}。各字段引用不能重新赋值，但集合内容是否可变仍取决于传入的 List；
 * 当前 Service 使用 {@code List.copyOf} 创建不可增删的列表。</p>
 *
 * @param items 当前页文章，不包含其他页数据
 * @param page 当前页码，从 1 开始
 * @param pageSize 每页数量
 * @param total 满足查询条件的文章总数
 * @param totalPages 按 pageSize 计算出的总页数；没有数据时为 0
 */
public record PersonalRecordPageDto(
        List<PersonalRecordListItemDto> items,
        int page,
        int pageSize,
        long total,
        long totalPages) {
}
