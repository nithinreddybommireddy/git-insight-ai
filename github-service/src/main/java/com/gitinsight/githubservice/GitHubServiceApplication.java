package com.gitinsight.githubservice;



import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.gitinsight")
@EnableDiscoveryClient
public class GitHubServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitHubServiceApplication.class, args);
    }
}
