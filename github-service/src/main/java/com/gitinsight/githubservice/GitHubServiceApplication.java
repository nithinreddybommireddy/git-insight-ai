package com.gitinsight.githubservice;



import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.gitinsight")
@EnableDiscoveryClient
@EnableScheduling  // activates GitHubCacheService's @Scheduled cleanup
public class GitHubServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitHubServiceApplication.class, args);
    }
}
