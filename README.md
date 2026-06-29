# 🚀 GitInsight AI

<p align="center">
  <h3 align="center">AI-Powered GitHub Analytics Platform for Developers and Recruiters</h3>
  <p align="center">
    Analyze GitHub profiles, generate developer insights, and empower recruiters with AI-driven analytics.
  </p>
</p>
---

## 📖 About the Project

GitInsight AI is a production-grade SaaS application that analyzes GitHub profiles using the GitHub API and AI to generate meaningful developer insights.

Instead of manually reviewing repositories, commits, and contribution graphs, users receive an interactive dashboard containing developer analytics, repository health, coding consistency, language statistics, AI portfolio reviews, and much more.

This project is being developed following **Software Development Life Cycle (SDLC)** practices similar to real product companies.

---

# 🎯 Vision

To become the leading AI-powered developer intelligence platform that helps developers improve their GitHub portfolios while enabling recruiters to make faster, data-driven hiring decisions.

---

# 💡 Problem Statement

Recruiters spend a significant amount of time manually reviewing GitHub profiles.

Current problems include:

- Manual repository evaluation
- No standardized developer score
- Difficult candidate comparison
- No portfolio review
- Time-consuming hiring process
- Limited visibility into developer activity

Developers also struggle to understand:

- Portfolio quality
- Coding consistency
- Repository health
- Areas for improvement

---

# ✅ Solution

GitInsight AI automatically analyzes GitHub profiles and provides:

- Developer Analytics Dashboard
- Repository Analytics
- Language Usage
- Contribution Trends
- Commit Analytics
- Repository Health Score
- AI Portfolio Review
- Developer Score
- Open Source Activity Score
- Recruiter Dashboard
- Candidate Comparison
- Exportable Reports

---

# 👥 Target Users

### Developers

- Portfolio Analysis
- Coding Consistency
- AI Suggestions
- Developer Score

### Students

- Placement Preparation
- GitHub Portfolio Improvement
- Skill Analysis

### Recruiters

- Candidate Comparison
- Hiring Reports
- Developer Ranking
- Saved Candidates

### Engineering Managers

- Technical Evaluation
- Portfolio Review
- Team Analytics (Future)

---

# 👤 User Roles

## 👀 Guest

No login required.

### Features

- Search any GitHub username
- View profile
- Repository analytics
- Language statistics
- Contribution charts

---

## 👨‍💻 Developer

GitHub OAuth Login

### Features

- Personal Dashboard
- Saved Reports
- AI Portfolio Review
- Repository Health
- Developer Score
- Coding Suggestions
- Analysis History

---

## 🧑‍💼 Recruiter

### Features

- Search Developers
- Compare Candidates
- Save Profiles
- Export Reports
- Candidate Ranking
- Hiring Dashboard

---

## 🛡 Administrator

### Features

- User Management
- Platform Monitoring
- Usage Analytics
- Report Management

---

# 🚀 Core Features

## Version 1.0 (MVP)

- GitHub Username Search
- Public Profile Analysis
- Repository List
- Programming Language Analytics
- Contribution Dashboard
- Charts & Graphs
- GitHub OAuth Login
- JWT Authentication

---

## Version 2.0

- AI Portfolio Review
- Developer Score
- Repository Health Score
- Coding Consistency
- Open Source Score
- Saved Reports

---

## Version 3.0

- Recruiter Dashboard
- Candidate Comparison
- Hiring Lists
- PDF Reports
- Candidate Ranking

---

## Version 4.0

- Resume Analysis
- AI Career Coach
- Team Analytics
- Notifications
- Interview Readiness

---

# 🛣 User Journey

## Guest Flow

```
Home Page
      │
      ▼
Enter GitHub Username
      │
      ▼
Analyze
      │
      ▼
Analytics Dashboard
      │
      ▼
Login for Advanced Features
```

---

## Developer Flow

```
GitHub Login
      │
      ▼
Dashboard
      │
      ▼
My Analytics
      │
      ▼
AI Portfolio Review
      │
      ▼
Saved Reports
```

---

## Recruiter Flow

```
Login
      │
      ▼
Search Developer
      │
      ▼
Compare Developers
      │
      ▼
Generate Reports
      │
      ▼
Export PDF
```

---

# 🛠 Technology Stack

## Frontend

- React
- TypeScript
- Tailwind CSS
- React Router
- React Query
- Axios
- Recharts

## Backend

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- JWT Authentication
- OAuth2 (GitHub)

## Database

- PostgreSQL

## Cache

- Redis

## AI

- Gemini API

## DevOps

- Docker
- GitHub Actions
- Render
- Vercel

---

# 🏛 High-Level Architecture

```
                   React Frontend
                         │
                         ▼
               Spring Boot REST API
                         │
          ┌──────────────┴──────────────┐
          │                             │
          ▼                             ▼
     PostgreSQL                     Redis Cache
          │
          ▼
      GitHub API
          │
          ▼
      Gemini API
```

---

# 📂 Project Structure

```
GitInsight-AI/
│
├── backend/
│
├── frontend/
│
├── docs/
│   ├── PRD.md
│   ├── SRS.md
│   ├── USER-STORIES.md
│   ├── API-SPEC.md
│   ├── DATABASE.md
│   ├── WIREFRAMES.md
│   ├── ROADMAP.md
│
├── architecture/
│
├── database/
│
├── docker/
│
├── postman/
│
├── screenshots/
│
└── README.md
```

---

# 📚 Documentation

| Document | Status |
|----------|--------|
| Product Requirements Document | ✅ Planned |
| Software Requirements Specification | ✅ Planned |
| User Stories | ✅ Planned |
| Wireframes | ✅ Planned |
| Database Design | ✅ Planned |
| API Specification | ✅ Planned |
| System Architecture | ✅ Planned |
| Testing Strategy | ⏳ Upcoming |
| Deployment Guide | ⏳ Upcoming |

---

# 🗓 Development Roadmap

## 📌 Phase 0 – Product Discovery

- Product Vision
- PRD
- SRS
- User Stories
- Wireframes
- Database Design
- API Design

---

## 📌 Phase 1 – Backend Foundation

- Spring Boot Setup
- PostgreSQL
- GitHub API Integration
- REST APIs

---

## 📌 Phase 2 – Frontend Development

- Landing Page
- Dashboard
- Charts
- Search
- Analytics

---

## 📌 Phase 3 – Authentication

- GitHub OAuth
- JWT Authentication
- User Dashboard
- Saved History

---

## 📌 Phase 4 – AI Integration

- AI Portfolio Review
- Developer Score
- Repository Health
- Project Suggestions

---

## 📌 Phase 5 – Recruiter Module

- Candidate Comparison
- Hiring Dashboard
- Export Reports
- Recruiter Analytics

---

## 📌 Phase 6 – Deployment

- Docker
- GitHub Actions
- Render Deployment
- Vercel Deployment

---

# 🌳 Git Workflow

```
main
│
develop
│
├── feature/backend
├── feature/frontend
├── feature/authentication
├── feature/github-api
├── feature/analytics
├── feature/ai
└── feature/recruiter-dashboard
```

---

# 📝 Commit Convention

```
feat: add GitHub profile analysis

fix: resolve authentication issue

docs: update README

refactor: improve analytics service

test: add unit tests

chore: configure Docker
```

---

# 🚀 Future Enhancements

- GitLab Support
- Bitbucket Support
- Chrome Extension
- Mobile Application
- Resume Parser
- AI Career Coach
- Enterprise Dashboard
- Team Analytics
- Coding Streak Prediction
- Developer Leaderboard

---

# ⭐ Support

If you like this project, please consider giving it a **⭐ Star** on GitHub.

It motivates continued development and helps others discover the project.
---

# 🚧 Project Status

> **Currently in Product Planning & Active Development**

This project follows a professional SDLC approach, including:

- ✅ Product Planning
- ✅ Documentation
- 🔄 System Design
- 🔄 Database Design
- ⏳ Backend Development
- ⏳ Frontend Development
- ⏳ AI Integration
- ⏳ Testing
- ⏳ Deployment
