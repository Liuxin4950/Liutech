# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LiuTech is a modern full-stack blog system with AI chat capabilities, built with a microservices architecture. The system consists of multiple services including a user frontend, admin dashboard, backend API, and AI chat service.

## Technology Stack

### Backend Services
- **Main Backend**: Spring Boot 3.5.6 + Java 21 (Port 8080)
- **AI Service**: Spring Boot 3.5.6 + Spring AI 1.0.0-M5 (Port 8081)
- **Database**: MySQL 8.0 with MyBatis-Plus 3.5.12
- **Authentication**: Spring Security + JWT (jjwt 0.11.5)
- **Build Tool**: Maven with parent-pom structure

### Frontend Applications
- **User Frontend**: Vue 3.5.17 + TypeScript + Vite 7.1.3 (Port 3000)
- **Admin Dashboard**: Vue 3.5.17 + Ant Design Vue 4.2.6 + TypeScript (Port 3001)
- **State Management**: Pinia 3.0.3 with persistence
- **Rich Text Editor**: TinyMCE 7.9.1
- **Live2D Animation**: Pixi.js 6.5.10

## Common Development Commands

### Backend Development

#### Main Backend Service
```bash
cd LiuTech
mvn spring-boot:run                    # Start in development mode
mvn clean package -DskipTests          # Build JAR file
java -jar target/liutech-backend-*.jar # Run JAR file
```

#### AI Service
```bash
cd LiuTech-AI
mvn spring-boot:run                    # Start in development mode (Port 8081)
mvn clean package -DskipTests          # Build JAR file
```

### Frontend Development

#### User Frontend (Web)
```bash
cd Web
npm install                            # Install dependencies
npm run dev                            # Development server (Port 3000)
npm run build                          # Production build
```

#### Admin Dashboard
```bash
cd Admin
npm install                            # Install dependencies
npm run dev                            # Development server (Port 3001)
npm run build                          # Production build
```

### Full Stack Development

#### Build All Services
```bash
# Windows
build.bat                              # Build all services and Docker images

# Linux/Mac
./build.sh                             # Build all services and Docker images
```

#### Docker Development
```bash
docker-compose up -d                   # Start all services
docker-compose up -d --build          # Rebuild and start all services
docker-compose down                    # Stop all services
docker-compose logs -f [service]       # View logs for specific service
```

### Database Operations
```bash
# Import database schema
mysql -u root -p liutech < sql.sql
mysql -u root -p liutech_ai < ai_chat_tables.sql
```

## Architecture

### Microservices Structure
- **LiuTech/**: Main backend API service (Port 8080)
- **LiuTech-AI/**: AI chat service with Spring AI integration (Port 8081)
- **Web/**: User-facing Vue.js frontend (Port 3000)
- **Admin/**: Administrative Vue.js dashboard (Port 3001)
- **nginx/**: Reverse proxy configuration

### Key Directories
- `src/main/java/chat/liuxin/liutech/`: Main backend source code
- `src/main/java/chat/liuxin/ai/`: AI service source code
- `src/`: Frontend source code (Vue components, stores, services)
- `uploads/`: File upload directory (configurable)

### Database Configuration
- **Main Database**: `liutech` for blog content
- **AI Database**: `liutech_ai` for chat history
- **Connection**: HikariCP connection pooling
- **Migrations**: SQL scripts in project root (`sql.sql`, `ai_chat_tables.sql`)

## Environment Configuration

### Development Environment
- **Database**: `127.0.0.1:3306` (configurable port)
- **API Base URL**: `http://127.0.0.1:8080`
- **File Upload**: Local filesystem

### Production/Docker Environment
- **Database**: `mysql:3306` (Docker network hostname)
- **File Upload**: `/app/uploads` in container
- **Static Files**: Served via Nginx at `/uploads`

### Key Configuration Files
- `LiuTech/src/main/resources/application.yml`: Main backend configuration
- `LiuTech-AI/src/main/resources/application.yml`: AI service configuration
- `Web/.env.development`: Frontend development variables
- `Admin/.env.development`: Admin development variables
- `docker-compose.yml`: Service orchestration

## API Structure

### Main Backend API (Port 8080)
- `/api/auth/*`: Authentication endpoints
- `/api/posts/*`: Article management
- `/api/categories/*`: Category management
- `/api/tags/*`: Tag management
- `/api/users/*`: User management

### AI Service API (Port 8081)
- `/api/ai/*`: AI chat endpoints
- Streaming responses for real-time chat

### Authentication
- JWT Bearer token required for protected endpoints
- Token stored in `Authorization` header with `Bearer` prefix

## Special Features

### AI Integration
- Spring AI 1.0.0-M5 with OpenAI API compatibility
- Streaming chat responses
- Context-aware conversations with history management
- Separate database for chat persistence

### Live2D Animation
- Interactive character animations in user frontend
- Powered by Pixi.js 6.5.10
- Located in `src/assets/live2d/`

### Rich Text Editing
- TinyMCE 7.9.1 integration
- Support for multimedia content
- File upload integration with image handling

## Development Notes

### Code Structure Patterns
- **Controllers**: Handle HTTP requests (`controller/`)
- **Services**: Business logic layer (`service/`)
- **Mappers**: MyBatis data access layer (`mapper/`)
- **Models**: Entity classes (`model/`)
- **Config**: Configuration classes (`config/`)

### Frontend Organization
- **Views**: Page components (`views/`)
- **Components**: Reusable UI components (`components/`)
- **Stores**: Pinia state management (`stores/`)
- **Services**: API service layer (`services/`)
- **Router**: Vue Router configuration (`router/`)

### Testing
- Backend uses Spring Boot Test framework
- No dedicated frontend testing framework currently configured
- Tests skipped during builds (`-DskipTests`)

## Docker Configuration

### Service Ports
- **MySQL**: 3306
- **Main Backend**: 8080
- **AI Service**: 8081
- **Web Frontend**: 3000
- **Admin Dashboard**: 3001
- **Nginx**: 80, 443

### Environment Variables
- `MYSQL_PORT`: MySQL port (default: 3306)
- `BACKEND_PORT`: Backend port (default: 8080)
- `AI_PORT`: AI service port (default: 8081)
- `WEB_PORT`: Web frontend port (default: 3000)
- `ADMIN_PORT`: Admin port (default: 3001)
- `SPRING_AI_OPENAI_API_KEY`: OpenAI API key for AI service

## File Upload Handling

- **Max File Size**: 100MB
- **Max Image Size**: 10MB
- **Upload Path**: Configurable via `FILE_UPLOAD_BASE_PATH`
- **URL Prefix**: `/uploads`
- **Storage**: Local filesystem with Docker volume persistence