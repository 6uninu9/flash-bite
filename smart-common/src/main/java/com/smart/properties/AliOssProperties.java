package com.smart.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "smart.alioss.oss")
@Data
public class AliOssProperties {

    private String endpoint;
    private String region;
    private String bucketName;
}
