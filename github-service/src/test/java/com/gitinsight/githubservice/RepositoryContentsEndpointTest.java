package com.gitinsight.githubservice;

import com.gitinsight.githubservice.dto.response.RepositoryContentResponse;
import com.gitinsight.githubservice.service.GitHubService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tests for the Job Matcher's repository contents listing
 * ({@code GET /api/github/{owner}/{repo}/contents?path=...&ref=...}).
 * Runs the real controller + Spring MVC stack (H2 profile, no network);
 * only the GitHub boundary is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RepositoryContentsEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubService gitHubService;

    @Test
    void nestedModulePathReturnsContents() throws Exception {
        RepositoryContentResponse pom = new RepositoryContentResponse();
        pom.setName("pom.xml");
        pom.setType("file");
        pom.setPath("api-gateway/pom.xml");

        RepositoryContentResponse src = new RepositoryContentResponse();
        src.setName("src");
        src.setType("dir");
        src.setPath("api-gateway/src");

        when(gitHubService.getContents(eq("devsiddharth"), eq("microservices_v2"),
                eq("api-gateway"), eq("main"))).thenReturn(List.of(pom, src));

        mockMvc.perform(get("/api/github/devsiddharth/microservices_v2/contents")
                        .param("path", "api-gateway")
                        .param("ref", "main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("pom.xml"))
                .andExpect(jsonPath("$.data[0].type").value("file"))
                .andExpect(jsonPath("$.data[1].name").value("src"));
    }

    @Test
    void rootListingWorksWithBlankOrMissingPath() throws Exception {
        when(gitHubService.getContents(eq("devsiddharth"), eq("microservices_v2"),
                eq(""), eq("main"))).thenReturn(List.of());

        mockMvc.perform(get("/api/github/devsiddharth/microservices_v2/contents")
                        .param("ref", "main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void missingPathReturnsNullData() throws Exception {
        when(gitHubService.getContents(eq("devsiddharth"), eq("microservices_v2"),
                eq("does-not-exist"), eq("main"))).thenReturn(null);

        mockMvc.perform(get("/api/github/devsiddharth/microservices_v2/contents")
                        .param("path", "does-not-exist")
                        .param("ref", "main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}