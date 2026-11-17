package fr.ledcom.signaire.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/** An Elasticsearch container, shared by the tests that use the same Spring context. */
@TestConfiguration(proxyBeanMethods = false)
public class ElasticsearchTestConfiguration {

    /** Keep in line with compose.yaml and the version of the Elasticsearch client. */
    static final String IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:9.4.8";

    @Bean
    @ServiceConnection
    ElasticsearchContainer elasticsearch() {
        return new ElasticsearchContainer(DockerImageName.parse(IMAGE))
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
    }
}
