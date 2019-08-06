package org.nexial.service.domain.awsconfig;

import org.nexial.core.aws.AwsS3Helper;
import org.nexial.core.plugins.aws.AwsSettings;
import org.nexial.service.domain.ApplicationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AWSConfiguration {
    @Autowired
    ApplicationProperties properties;

    @Bean
    public AwsS3Helper getAwsS3HelperObject() {
        AwsSettings awsSettings = new AwsSettings(properties.getAccessKey(),
                                                  properties.getSecretKey(),

                                                  (com.amazonaws.regions.Regions.fromName(properties.getRegion())));
        awsSettings.setAssumeRoleArn("");
        awsSettings.setAssumeRoleSession("");
        AwsS3Helper helper = new AwsS3Helper();
        helper.setCredentials(awsSettings);
        helper.setS3PathStyleAccessEnabled(true);
        return helper;
    }
}

