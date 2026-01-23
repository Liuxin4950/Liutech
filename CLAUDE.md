# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 🏗️ System Architecture

This is a **full-stack blog platform** with a microservices architecture:

- **Frontend**: Vue 3 + TypeScript applications
  - `Web/` - User-facing blog frontend (port 3000)
  - `Admin/` - Admin management dashboard (port 3001)

- **Backend**: Spring Boot microservices
  - `LiuTech/` - Main backend API service (port 8080)
  - `LiuTech-AI/` - AI chat assistant service (port 8081)

- **Database & Cache**: MySQL 8.0 + optional Redis
- **Reverse Proxy**: Nginx for routing and load balancing
- **Containerization**: Full Docker Compose setup

### Service Dependencies
```
Nginx (80/443)
  ├── Web Frontend (3000)
  ├── Admin Frontend (3001)
  ├── LiuTech Backend (8080)
  └── LiuTech-AI Service (8081)
        └── MySQL (3306)
```

## 🛠️ Common Development Commands

### Backend (Spring Boot)

**Main Backend Service:**
```bash
cd LiuTech
mvn clean compile                    # Compile Java code
mvn spring-boot:run                  # Run in development mode
mvn test                             # Run unit tests
mvn test -Dtest=UserControllerTest   # Run specific test
mvn clean package -DskipTests        # Build JAR for production
java -jar target/liutech-backend-*.jar  # Run compiled JAR
```

**AI Service:**
```bash
cd LiuTech-AI
mvn clean compile
mvn spring-boot:run
mvn test
mvn clean package -DskipTests
java -jar target/liutech-ai-*.jar
```

**Parent Module (all backend services):**
```bash
# Build all modules from project root
mvn clean install -DskipTests        # Build all modules
mvn test                             # Run all tests
mvn clean install                    # Build with tests
```

### Frontend (Vue 3)

**Web Frontend (User Blog):**
```bash
cd Web
npm install                          # Install dependencies
npm run dev                          # Start dev server (port 3000)
npm run build                        # Production build
npm run preview                      # Preview production build
```

**Admin Dashboard:**
```bash
cd Admin
npm install
npm run dev                          # Start dev server (port 3001)
npm run build
npm run preview
```

**Install all frontend deps:**
```bash
cd Web && npm install && cd ../Admin && npm install
```

### Docker Development

**Build all images:**
```bash
./快速打包文件.bat        # Windows build script
```

**Start full stack:**
```bash
docker-compose up -d        # Start all services
docker-compose up -d mysql  # Start only MySQL
docker-compose ps           # Check service status
docker-compose logs -f      # View logs
docker-compose down         # Stop all services
```

**View logs:**
```bash
docker-compose logs -f backend
docker-compose logs -f ai
docker-compose logs -f web
```

### Database

**Initialize databases:**
```sql
CREATE DATABASE liutech CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE liutech_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
mysql -u root -p liutech < sql/sql.sql
mysql -u root -p liutech_ai < sql/ai_chat_tables.sql
```

## 📁 Key Directories & Files

### Backend Structure (LiuTech/)
```
src/main/java/chat/liuxin/liutech/
├── controller/          # REST endpoints
│   ├── admin/          # Admin panel APIs
│   └── web/            # Public APIs
├── service/            # Business logic
├── mapper/             # MyBatis data access
├── model/              # Data models (User, Post, etc.)
├── config/             # Spring configurations
├── common/             # Shared utilities
└── aspect/             # AOP aspects
```

### Frontend Structure (Web/ & Admin/)
```
src/
├── views/              # Page components
├── components/         # Reusable components
├── stores/             # Pinia state management
├── services/           # API service layers
├── router/             # Vue Router configuration
├── composables/        # Vue composables
├── utils/              # Helper functions
└── assets/             # Static assets
```

### Configuration Files
- `pom.xml` - Maven parent module (dependency management)
- `LiuTech/pom.xml` - Main backend dependencies
- `LiuTech-AI/pom.xml` - AI service dependencies
- `Web/package.json` - Frontend dependencies (Web)
- `Admin/package.json` - Frontend dependencies (Admin)
- `docker-compose.yml` - Service orchestration
- `.env` - Environment variables

## 🔧 Configuration

### Environment Variables (.env)
```bash
# Database
DB_ROOT_PASSWORD=123456          # MySQL root password
MYSQL_PORT=3306

# Services
BACKEND_PORT=8080
AI_PORT=8081
WEB_PORT=3000
ADMIN_PORT=3001
NGINX_HTTP=80
NGINX_HTTPS=443

# AI Service
SPRING_AI_OPENAI_API_KEY=your_api_key    # Required for AI service

# JWT (IMPORTANT: Must be shared between backend and AI services)
JWT_SECRET=your_strong_jwt_secret_key_min_32_chars    # Required for production

# File uploads (Docker)
FILE_UPLOAD_BASE_PATH=/app/uploads           # Container path
# Files stored at /liuxin/uploads on host (mounted to /app/uploads in container)

# Server
SERVER_BASE_URL=http://liuxin.chat           # Base URL for the application
```

**Critical Notes:**
- `JWT_SECRET` must be identical for both `backend` and `ai` services for token validation
- For AI service, use SiliconFlow API key: https://www.siliconflow.com/
- File uploads persist at `/liuxin/uploads` on the host (bind-mounted to containers)
- **HTTPS (Production)**: SSL certificates should be placed at `/opt/liutech/nginx/` on the server:
  - `/opt/liutech/nginx/liuxin.chat_bundle.crt` - SSL certificate
  - `/opt/liutech/nginx/liuxin.chat.key` - SSL private key

### Backend Config (application.yml)
Key configurations in `LiuTech/src/main/resources/`:
- `application.yml` - Base configuration
- `application-dev.yml` - Development environment (local MySQL)
- `application-prod.yml` - Production environment (Docker network)
- File upload settings (100MB max)
- JWT configuration (7-day expiration)
- MyBatis-Plus with pagination

### Frontend Config (.env.development)
```bash
VITE_API_BASE_URL=http://127.0.0.1:8080
```

## 🎯 Key Features

### User System
- JWT-based authentication
- Role-based access control (user/admin)
- User registration/login
- Profile management with avatar upload

### Content Management
- Rich text editor (TinyMCE 7.9.1)
- Article CRUD operations
- Categories and tags
- File/image uploads
- Draft and published states

### AI Assistant
- Chat-based AI integration (LiuTech-AI service)
- Context-aware conversations
- Content writing assistance

### Admin Features
- User management
- Content moderation
- System statistics
- Category/tag management

## 🔌 API Architecture

### Base URLs
- Main API: `http://localhost:8080` (backend)
- AI API: `http://localhost:8081` (ai)
- Docker internal: `http://backend:8080`, `http://ai:8081`

### Authentication
All protected routes require JWT token in header:
```
Authorization: Bearer {token}
```
Token expires after 7 days. Both backend and AI services use the same JWT_SECRET for validation.

### Key Endpoints
- `POST /user/login` - User login (returns JWT token)
- `POST /user/register` - User registration
- `GET /posts` - List articles (with pagination)
- `POST /posts` - Create article (auth required)
- `GET /posts/{id}` - Get article details
- `GET /admin/users` - User management (admin only)
- `POST /ai/chat` - AI chat (streaming SSE response)

Full API documentation: `LiuTech/API文档.md` (Chinese)

## 🚀 Deployment

### Quick Start (Recommended)
```bash
./快速打包文件.bat    # Build all components
docker-compose up -d  # Start full stack
```

Access:
- User frontend: http://localhost:3000
- Admin panel: http://localhost:3001
- API: http://localhost:8080
- AI service: http://localhost:8081

### Production Deployment
1. Build locally: `.\快速打包文件.bat`
2. Export images: `.\镜像导出脚本.bat` (optional)
3. Upload to server: `/opt/liutech/`
4. Run: `chmod +x 服务器部署脚本.sh && ./服务器部署脚本.sh`
5. Configure `.env` with JWT_SECRET and SPRING_AI_OPENAI_API_KEY
6. Restart services: `docker compose restart backend ai`

See 快速部署指南.md for detailed production deployment instructions.

### Production Build
```bash
# Backend
cd LiuTech && mvn clean package -DskipTests
cd LiuTech-AI && mvn clean package -DskipTests

# Frontend
cd Web && npm run build
cd Admin && npm run build

# Deploy with Docker
docker-compose up -d
```

Note: The build script `快速打包文件.bat` handles all of the above automatically.

## 🧪 Testing

### Backend Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=UserServiceTest

# Run with coverage
mvn test jacoco:report
```

### Frontend Testing
Frontend uses Vite - no test framework configured yet.

## 🐛 Debugging

### Backend Logs
```bash
# Development mode
mvn spring-boot:run  # Logs in console

# Docker
docker-compose logs -f backend
docker-compose logs -f ai
docker-compose logs -f mysql
```

### Frontend Dev Tools
```bash
npm run dev  # Vite dev server with HMR
```

### Common Issues
- **AI service returns 406 Not Acceptable**: Nginx configuration issue - ensure `proxy_set_header Accept "text/event-stream";` is NOT present (it breaks non-SSE AI requests)
- **AI service cannot connect to backend**: Check that `BLOG_API_URL=http://backend:8080` in AI service config
- **JWT token validation fails**: Ensure `JWT_SECRET` is identical for both backend and AI services
- **File uploads not persisting**: Check bind mount `/liuxin/uploads:/app/uploads` exists on host
- **HTTPS not working**: Ensure SSL certificates are at `/opt/liutech/nginx/liuxin.chat_bundle.crt` and `liuxin.chat.key`
- **SSE streaming not working**: Nginx must have `proxy_buffering off` and `proxy_read_timeout` set high enough

### Database Access
```bash
# Connect to MySQL in Docker
docker exec -it liutech-mysql mysql -u root -p123456
```

## 📚 Key Technologies

**Backend:**
- Spring Boot (3.5.9 parent, 3.5.6 modules)
- Spring Security + JWT
- MyBatis-Plus (3.5.12)
- MySQL 8.0
- Java 21

**Frontend:**
- Vue 3.5.17 + TypeScript
- Vite 7.1.3
- Pinia (state)
- Vue Router 4.5.1
- Ant Design Vue (Admin)
- TinyMCE (rich text editor)
- Live2D animations

**Infrastructure:**
- Docker + Docker Compose
- Nginx (reverse proxy)
- MySQL 8.0

## 📝 Development Notes

### Adding New Features
1. Backend: Create controller → service → mapper layers
2. Frontend: Add route → view component → API service
3. Database: Add migration to `sql/` directory
4. Tests: Add unit tests for new functionality

### Maven Multi-Module Structure
- Parent `pom.xml` defines `spring-boot-starter-parent` 3.5.9 and manages dependencies
- Child modules (`LiuTech`, `LiuTech-AI`) inherit from parent
- Build from root: `mvn clean install -DskipTests` builds all modules

### Docker Service Communication
Services use container names for internal communication:
- AI service → Backend: `http://backend:8080`
- All services → MySQL: `mysql:3306`
- External access: Use exposed ports (8080, 8081, 3000, 3001)

### Database Migrations
- Main DB: `sql/sql.sql`
- AI DB: `sql/ai_chat_tables.sql`

### Code Style
- Backend: Follow Java conventions (Spring Boot standards)
- Frontend: Vue 3 Composition API + TypeScript
- ESLint/Prettier configured for frontend

## 🔗 Important Resources

- README.md - Full project documentation (Chinese)
- LiuTech/API文档.md - Complete API reference (Chinese)
- 快速部署指南.md - Deployment guide (Chinese)
- docker-compose.yml - Service configuration
- 服务器部署脚本.sh - Server deployment script

## 🚦 Service Ports

| Service | Port | Description |
|---------|------|-------------|
| Web Frontend | 3000 | User-facing blog |
| Admin Frontend | 3001 | Admin dashboard |
| Backend API | 8080 | Main REST API |
| AI Service | 8081 | AI chat assistant |
| MySQL | 3306 | Primary database |
| Nginx | 80/443 | Reverse proxy |
