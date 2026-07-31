# 🗄 Database Design (PostgreSQL)

Two databases — one per owning service (JPA `ddl-auto: update`).

## auth-service → `gitinsight_auth`

### `users`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| email | VARCHAR(255) UNIQUE NOT NULL | login identifier |
| password | VARCHAR(255) NOT NULL | BCrypt hash |
| name | VARCHAR(255) | |
| avatar_url | VARCHAR(500) | |
| github_username | VARCHAR(255) | optional GitHub handle |
| role | VARCHAR(20) | `USER` / `RECRUITER` / `ADMIN` (default `USER`) |
| enabled | BOOLEAN | |
| created_at / updated_at | TIMESTAMP | |

### `saved_candidates`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| recruiter_id | FK → users.id | owner |
| candidate_username | VARCHAR(255) | GitHub username |
| candidate_name / candidate_avatar_url | | snapshot |
| candidate_github_id | BIGINT | |
| candidate_score | INT | developer score snapshot |
| candidate_level | VARCHAR(50) | |
| candidate_languages | VARCHAR(500) | comma-separated |
| bookmarked | BOOLEAN | |
| notes | TEXT | legacy column (notes live in `recruiter_notes`) |
| created_at / updated_at | TIMESTAMP | |

Unique on (recruiter_id, candidate_username).

### `recruiter_notes`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| recruiter_id | FK → users.id | owner |
| candidate_username | VARCHAR(255) | |
| title | VARCHAR(200) | optional |
| content | VARCHAR(5000) NOT NULL | note body |
| created_at / updated_at | TIMESTAMP | |

## github-service → `gitinsight_github`

### `score_history`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| username | VARCHAR(100) NOT NULL | indexed |
| display_name | VARCHAR(200) | |
| overall_score | INT NOT NULL | 0–100 |
| level | VARCHAR(50) | e.g. `Expert 🏅` |
| contribution_recency … maintenance | INT | the 10 metric scores |
| total_stars / total_forks / total_repositories / language_count | INT | |
| languages | VARCHAR(500) | comma-separated |
| created_at | TIMESTAMP NOT NULL | auto-set |

Indexes: `idx_score_username` (username), `idx_score_username_created` (username, created_at).

## Notes

- `ddl-auto: update` creates/updates tables automatically on service startup.
- There is intentionally **no cross-service foreign key** between auth-service and github-service tables — they communicate via REST.
- Secrets (JWT secret, GITHUB_TOKEN, GEMINI_API_KEY) are environment variables, never stored in the database.
