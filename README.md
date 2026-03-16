# Pulse - Observability Platform

Open-source observability platform for mobile and web applications.

---

## 📚 Documentation

**All documentation has been moved to the [`docs/`](docs/) folder.**

### 🚀 Quick Start

TBA

### 📖 Full Documentation Index

See TBA

---

## 🎯 Recent Updates

### Multi-Tenancy & RBAC Implementation (Feb 2026)
- ✅ Complete authentication system with Google OAuth
- ✅ Tenant & project hierarchy
- ✅ OpenFGA authorization
- ✅ Per-project ClickHouse data isolation
- ✅ 7 new REST API endpoints
- ✅ Comprehensive documentation

**Status:** 100% Complete - Ready for Deployment

---

## 🚀 Quick Commands

### Backend Server
```bash
cd backend/server
mvn clean install
mvn exec:java
```

### Run Migrations
```bash
mvn flyway:migrate
```

### Deploy OpenFGA
```bash
docker run -d --name openfga -p 8080:8080 openfga/openfga:latest run
```

---

## 📋 Prerequisites

- Java 11+
- Maven 3.6+
- MySQL 8.0+
- ClickHouse 22.0+
- Docker (for OpenFGA)

---

## 🔗 Links

- **Documentation:** [`docs/`](docs/)

---

## 📞 Support

For integration questions or issues, refer to:
TBA

---

## ✅ Implementation Status

**Multi-Tenancy & RBAC:** ✅ 100% Complete
