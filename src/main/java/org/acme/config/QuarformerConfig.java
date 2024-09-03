package org.acme.config;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "quarformer", phase = ConfigPhase.RUN_TIME)
public class QuarformerConfig {

    /**
     * Use the Cluster injected via Quarkus' CDI for defaultConnection.
     */
    @ConfigItem(name = "enableQluster", defaultValue="false")
    public boolean enableQluster;
}
