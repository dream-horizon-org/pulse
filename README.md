# Pulse - Observability Platform

Open-source observability platform for mobile and web applications.

---

## 📚 Documentation

Component-specific documentation is available in each service directory:

- **Backend Server:** [`backend/server/README.md`](backend/server/README.md)
- **Alerts Cron:** [`backend/pulse-alerts-cron/README.md`](backend/pulse-alerts-cron/README.md)
- **Frontend UI:** [`pulse-ui/README.md`](pulse-ui/README.md)
- **Android SDK:** [`pulse-android-otel/README.md`](pulse-android-otel/README.md)
- **React Native SDK:** [`pulse-react-native-otel/README.md`](pulse-react-native-otel/README.md)
- **Deployment:** [`deploy/README.md`](deploy/README.md)

---

## 🎯 Key Features

- ✅ Complete authentication system with Google OAuth
- ✅ Multi-tenant architecture with project hierarchy
- ✅ OpenFGA-based authorization (RBAC)
- ✅ Per-project ClickHouse data isolation
- ✅ Real-time observability dashboards
- ✅ Mobile SDK instrumentation (Android & React Native)
- ✅ 7 new REST API endpoints
- ✅ Comprehensive documentation

**Status:** 100% Complete - Ready for Deployment

---

## 🏗️ Project Structure

```
pulse/
├── backend/
│   ├── server/              # Java/Vert.x REST API
│   ├── pulse-alerts-cron/   # Alert evaluation service
│   └── ingestion/           # OTEL collector & ClickHouse schema
├── pulse-ui/                # React dashboard
├── pulse-android-otel/      # Android SDK
├── pulse-react-native-otel/ # React Native SDK
├── pulse_ai/                # AI agent (Google ADK)
├── deploy/                  # Docker Compose & scripts
└── README.md                # This file
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

## 📞 Support

For questions or issues, refer to the component-specific README files listed above.

---

## 📄 License

Apache 2.0 - See [LICENSE](LICENSE) for details.
