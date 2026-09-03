package com.gitinsight.githubservice.dto.response;

import lombok.Data;

/**
 * A single entry from the GitHub Contents API directory listing.
 * Used by the Job Matcher's bounded source/build discovery: only the
 * name, type (file/dir) and path are needed to navigate nested modules.
 */
@Data
public class RepositoryContentResponse {

    private String name;
    private String type;
    private String path;
}