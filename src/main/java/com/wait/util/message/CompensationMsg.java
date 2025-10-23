package com.wait.util.message;

import com.wait.entity.CacheSyncParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompensationMsg<T> {

    CacheSyncParam<T> originalParam;
    String failReason;
    long failTime;
}
