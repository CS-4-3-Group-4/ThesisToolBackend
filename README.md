# Thesis Tool Backend

This repository contains the backend implementation of the **Emergency Personnel Allocation system** that will be use in our thesis project. The backend exposes an API for running simulations of **Firefly Algorithm (FA)** and **Extended Firefly Algorithm (EFA)** to optimize personnel deployment during flood scenarios. The frontend application can call this API to retrieve metrics, results, and simulation data.

> **Note:** This backend handles all algorithmic computations and exposes endpoints for use by a separate frontend.

## Features

-   **FA (Firefly Algorithm)**: Standard nature-inspired optimization algorithm for emergency allocation.
-   **EFA (Extended Firefly Algorithm)**: Enhanced version with improved convergence and deployment efficiency.
-   **Simulation Engine**: Runs allocation scenarios based on flood maps and personnel data.
-   **Metrics API**: Returns execution time, convergence curves, and allocation efficiency.
-   **CSV Export**: Generates CSV outputs of simulation results.

## 👥 Group Members

-   Abainza, Rendel
-   de Dios, Wendel
-   Osana, Lester
-   Viado, John Paul

## 🚀 Getting Started

### Prerequisites

Make sure you have the following installed:

-   **Java Development Kit (JDK)** (version 17 or higher) [Download JDK](https://www.oracle.com/java/technologies/downloads/#jdk17-windows)
-   **Maven** (for building the application) [Download Maven](https://maven.apache.org/download.cgi)
-   **Git** (to clone the repository) [Download Git](https://git-scm.com/downloads)

---

### 1. Clone the Repository

```bash
git clone https://github.com/Viadsss/EFA-FA-Backend.git
cd EFA-FA-Backend
```

---

### 2. Run the Application

Compile and run the backend in your IDE (e.g., NetBeans or VS Code) by pressing **F5** or using the IDE's run button. The API will start and listen on:

```
http://localhost:8080
```

The frontend can call the endpoints to trigger simulations and retrieve results.

---

### 3. API Endpoints

```
Utility:
  GET  /health                             - Health check

FA Algorithm:
  General:
    GET  /fa/status                        - Get current status
    POST /fa/stop                          - Stop running algorithm
    GET  /fa/results                       - Get results
    GET  /fa/iterations                    - Get iteration history

  Single Run:
    POST /fa/single/run                    - Start single run

  Multiple Runs:
    POST /fa/multiple/run?runs=N           - Start N runs (2-100)

  Data:
    GET  /fa/allocations                   - Get allocation details
    GET  /fa/flows                         - Get flow details

EFA Algorithm:
  General:
    GET  /efa/status                        - Get current status
    POST /efa/stop                          - Stop running algorithm
    GET  /efa/results                       - Get results
    GET  /efa/iterations                    - Get iteration history

  Single Run:
    POST /efa/single/run                     - Start single run

  Multiple Runs:
    POST /efa/multiple/run?runs=N            - Start N runs (2-100)

  Data:
    GET  /efa/allocations                    - Get allocation details
    GET  /efa/flows                          - Get flow details
```

### 4. Run Spotless (Code Formatting)

We use **Spotless** to ensure consistent code style. Run these commands:

-   **Check code formatting:**

```bash
mvn spotless:check
```

-   **Automatically apply formatting:**

```bash
mvn spotless:apply
```

**💡 Best Practice:**
Run `mvn spotless:apply` every time you make changes or before committing. This ensures all code is properly formatted, reduces merge conflicts, and keeps the repository clean.

---

### 5. Using the API with Bruno (Optional)

You can test the API using [Bruno](https://www.usebruno.com/).

-   Download the **Bruno app** if you don’t have it installed.
-   The collection file is already included in this repository — open it in Bruno to start testing the endpoints.
