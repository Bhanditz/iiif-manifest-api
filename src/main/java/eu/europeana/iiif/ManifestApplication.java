package eu.europeana.iiif;

import eu.europeana.iiif.service.ManifestService;
import eu.europeana.iiif.service.ManifestSettings;
import eu.europeana.iiif.web.ManifestController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.netflix.hystrix.dashboard.EnableHystrixDashboard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Main application and configuration
 *
 * @author Patrick Ehlert
 * Created on 6-12-2017
 */
@SpringBootApplication
@EnableHystrixDashboard
@EnableCircuitBreaker
@PropertySource(value = "classpath:build.properties", ignoreResourceNotFound = true)
public class ManifestApplication extends SpringBootServletInitializer {

    /**
     * Setup CORS for all requests
     * @return
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurerAdapter() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins("*").maxAge(1000)
                        .exposedHeaders("Allow, Vary, ETag, Last-Modified");
            }
        };
    }

    @Bean
    public ManifestSettings manifestSettings() {
        return new ManifestSettings();
    }

    /**
     * Manifest service that does all the 'dirty work'; retrieving records, converting to data, serializing to json-ld
     * @return
     */
    @Bean
    public ManifestService manifestService() {
        return new ManifestService(manifestSettings());
    }

    /**
     * Rest controller that handles manifest requests
     * @return
     */
    @Bean
    public ManifestController manifestController() {
        return new ManifestController(manifestService());
    }

    /**
     * This method is called when starting as a Spring-Boot application (e.g. from your IDE)
     * @param args
     */
    @SuppressWarnings("squid:S2095") // to avoid sonarqube false positive (see https://stackoverflow.com/a/37073154/741249)
    public static void main(String[] args) {
        SpringApplication.run(ManifestApplication.class, args);
    }
    
}
