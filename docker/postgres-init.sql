-- One database per service (auth, github, analytics each run their own schema).
CREATE DATABASE gitinsight_auth;
CREATE DATABASE gitinsight_github;
CREATE DATABASE gitinsight_analytics;
