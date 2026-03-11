# Pulse - Observability Platform

Open-source observability platform for mobile and web applications.

---

## 📚 Documentation

**All documentation has been moved to the [`docs/`](docs/) folder.**

### 🚀 Quick Start

For the multi-tenancy and RBAC implementation:

1. **[docs/IMPLEMENTATION_COMPLETE.md](docs/IMPLEMENTATION_COMPLETE.md)** - Quick overview
2. **[docs/SETUP_CHECKLIST.md](docs/SETUP_CHECKLIST.md)** - Step-by-step setup
3. **[docs/INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md)** - Detailed integration

### 📖 Full Documentation Index

See **[docs/README.md](docs/README.md)** for the complete documentation index.

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

**Get Started:** See [`docs/SETUP_CHECKLIST.md`](docs/SETUP_CHECKLIST.md)

---

## 🏗️ Project Structure

```
pulse/
├── backend/
│   ├── server/          # Java/Vert.x backend
│   └── ingestion/       # Data ingestion services
├── frontend/            # React frontend (if applicable)
├── docs/               # 📚 All documentation
│   ├── README.md       # Documentation index
│   ├── SETUP_CHECKLIST.md
│   ├── INTEGRATION_GUIDE.md
│   └── ...
└── README.md           # This file
```

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
- **Setup Guide:** [`docs/SETUP_CHECKLIST.md`](docs/SETUP_CHECKLIST.md)
- **Architecture:** [`docs/ARCHITECTURE_DIAGRAMS.md`](docs/ARCHITECTURE_DIAGRAMS.md)

---

## 📞 Support

For integration questions or issues, refer to:
- [`docs/INTEGRATION_GUIDE.md`](docs/INTEGRATION_GUIDE.md) - Troubleshooting section
- [`docs/SETUP_CHECKLIST.md`](docs/SETUP_CHECKLIST.md) - Common issues

---

## ✅ Implementation Status

**Multi-Tenancy & RBAC:** ✅ 100% Complete

See [`docs/FINAL_STATUS.md`](docs/FINAL_STATUS.md) for details.

---

**Ready to start?** Open [`docs/SETUP_CHECKLIST.md`](docs/SETUP_CHECKLIST.md)
