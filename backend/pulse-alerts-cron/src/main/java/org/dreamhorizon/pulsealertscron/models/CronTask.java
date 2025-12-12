package org.dreamhorizon.pulsealertscron.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CronTask {
    private Integer id;
    private String url;
}

