package com.ruikao.common.result;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {
    private long total;
    private List<T> records;
    public static <T> PageResult<T> of(long total, List<T> records) { return new PageResult<>(total, records); }
}
