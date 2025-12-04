package org.dreamhorizon.pulseserver.dto.v2.request.userinteraction;

import org.dreamhorizon.pulseserver.error.ServiceError;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import jakarta.ws.rs.QueryParam;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetjobStatusRequestDto {
    @QueryParam("jobId")
    public Long jobId;


    void validateParams() {
        if (jobId != null && (!StringUtils.isNumeric(jobId.toString()) || jobId < 0)) {
            log.error("job id is invalid for path");
            throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getException();
        }
    }

    public void validate() {
        validateParams();
    }
}
