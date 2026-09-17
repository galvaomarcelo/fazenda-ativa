# 🌾 Fazenda Ativa

**Mapas profissionais, informações organizadas, gestão financeira e operacional e Inteligência Artificial — tudo em um aplicativo simples, feito para funcionar na realidade do campo, mesmo sem internet.**

**Smart management for rural properties — professional maps, organized information, financial and operational management, and AI in a single application designed for the reality of field work, including offline environments.**

---

## 📖 Overview

**Fazenda Ativa** is a smart management application designed for rural properties.

The project combines **geospatial technology, mobile development, offline-first architecture, financial management, field data collection, OCR, and AI-powered insights** in a single Android application.

The goal is not to bring an office management system into the field or while you are lying in a hammock. Always at you hand an easy to use.

It is to build a tool around the way information is actually collected and used on a farm — often outdoors, with limited connectivity, GPS-based positioning, photos, and very little time for manual data entry.

### Core capabilities

* 🗺️ **Professional, personalized maps** — Interact with your farm as never before. Understand your farm by knowing about topography, fields, distances, infrastructure, pastures, points of interest, incidents and more. always at your hands.
* 📍 **Field data collection** — Record locations and incidents directly from the map or camera while in the field.
* 📋 **Operational management** — Organize livestock, incidents and relevant farm information.
* 💰 **Financial management** — Expenses, contributions, withdrawals, revenues, and cash flow, all safe and well organized. 
* 📷 **OCR-powered data entry** — Take a photo, the IA do the rest. Register receipts, invoices, and handwritten notes by sharing a image.
* 🤖 **AI-powered insights** — Transform structured farm and environmental data into useful, human-readable stories.
* 🌦️ **Weather intelligence** — Combine weather forecasts with farm context to generate operational information.
* 📶 **Offline-first operation** — Essential field workflows continue working when there is no internet connection.

---

## 🎯 The Problem

Managing a rural property presents a very different technical environment from a typical business application.

### Connectivity cannot be assumed

A farmer may spend most of the day in areas with little or no mobile coverage.

An application that requires a permanent connection is therefore unsuitable for many field operations.

### Spatial information matters

Many farm decisions are inherently geographic:

* Where is a particular incident?
* Which pasture is affected?
* Where is a water source?
* Where did an infrastructure problem occur?
* How large is this area?

A conventional list-based management system loses much of this context.

### Data collection happens in the field

Information is frequently captured through:

* photographs
* GPS positions
* receipts
* notes
* observations
* measurements

Entering all of this manually into a conventional business application is slow and error-prone.

**Fazenda Ativa is designed around these constraints rather than treating them as exceptions.**

---

# 🏗️ Technical Architecture

The application follows a **local-first / offline-first approach**.

```text
                 ┌─────────────────────┐
                 │     Android App     │
                 │                     │
                 │ Kotlin + Compose    │
                 └──────────┬──────────┘
                            │
             ┌──────────────┼──────────────┐
             │              │              │
             ▼              ▼              ▼
        Local Data       Geospatial       AI / OCR
        & Offline        Processing       Services
          Queue              │              │
             │               │              │
             └───────────────┼──────────────┘
                             ▼
                        ┌─────────┐
                        │Supabase │
                        │Postgres │
                        │PostGIS  │
                        └─────────┘
```

The architecture separates **field availability** from **cloud availability**.

When connectivity is available, information is synchronized with the backend.

When connectivity is unavailable, field operations can continue and pending information is stored locally for later synchronization.

---

# 🗺️ Geospatial System

The map is one of the central components of Fazenda Ativa.

The application consumes geospatial data from **PostgreSQL/PostGIS** and transforms it into data suitable for mobile visualization.

Spatial layers include:

* Farm perimeter
* Pastures
* Roads and paths
* Water streams
* Water bodies
* Contours
* Infrastructure
* Points of interest
* Field incidents

The application also handles coordinate reference systems and transformations between projected and geographic coordinates.

---

# 📍 Field Data & Points of Interest

Map your farm. POIs can be created directly from the field using two workflows:

### Camera-based positioning

The application captures the user's current location together with a photograph.

### Map-based positioning

The user positions the map and places the POI precisely at the desired location.

This architecture also allows the same spatial infrastructure to support future field features.

---
# 📷 OCR & Intelligent Data Entry

One of the goals of Fazenda Ativa is to minimize manual data entry.

A financial transaction can start with a simple photograph:

```text
Receipt / Invoice
       │
       ▼
      OCR
       │
       ▼
Extracted information
       │
       ▼
Expense / Transaction
```

The application extracts relevant information from documents and uses it to pre-populate the transaction.

This is particularly useful in the field, where manually entering:

* supplier
* amount
* date
* description
* category

can be unnecessarily cumbersome.

> **Take a photo. Let the application do the typing.**

---

# 🤖 AI & Farm Intelligence

AI is used not simply as a chatbot, but as a layer for transforming farm data into information that is easier to understand and act upon.

The application can combine information such as:

* weather forecasts
* incidents
* financial data
* operational records
* farm context

and generate short **story cards** for the user.

For example:

```text
🌧️ Weather

Rain is expected over the next few days, with the
highest probability on Sunday. Consider checking
field access and drainage around the affected areas.
```

The intention is to move from:

**data → information → context → insight**

rather than simply presenting raw database records.

---

# 🌦️ Weather Intelligence

Weather information is retrieved using **Open-Meteo** and integrated into the application.

The system collects:

* Current temperature
* Precipitation
* Rain
* Cloud cover
* Wind speed
* Wind direction
* Weather code
* Multi-day forecasts

The structured forecast can then be passed to the AI layer to generate contextual weather stories and alerts.

This creates a foundation for future features such as:

* severe weather alerts
* rainfall analysis
* field-operation recommendations
* pasture conditions
* historical weather analysis

---

# 💰 Financial Management

The financial subsystem is designed around the actual flow of money within the property.

It supports:

* Expenses
* Revenues
* Partner contributions
* Withdrawals
* Running cash balance
* Expense categories
* Suppliers
* Receipts

Financial aggregation is performed at the database level using PostgreSQL views and RPC functions.

This allows the application to request already-aggregated information for dashboards and visualizations instead of transferring and processing the complete transaction history on the mobile device.

---

# 📊 Data Visualization

The application is being extended with interactive charts for management analysis.

Planned visualizations include:

### Expenses by category

Identify where farm resources are being spent.

### Monthly expenses

Analyze how expenditure changes over time.

### Partner contributions vs. revenue

Compare financial participation and generated revenue among partners.

### Cattle evolution

Track herd evolution by animal type over time.

The chart layer is designed around reusable data structures so the same visualization components can be used for different farm datasets.

---

# 🛠️ Technology Stack

### Mobile

* **Kotlin**
* **Jetpack Compose**
* **Material 3**
* Android

### Geospatial

* **Google Maps**
* **PostGIS**
* GeoJSON
* Coordinate reference systems and transformations
* Spatial data processing

### Backend

* **Supabase**
* **PostgreSQL**
* **PostGIS**
* PostgreSQL RPC functions
* Supabase Storage

### Networking

* **Ktor**
* OkHttp

### AI & Computer Vision

* OCR
* Image processing
* Generative AI
* Structured JSON extraction

### Weather

* **Open-Meteo API**

### Visualization

* **Vico Charts**

---

# ⚙️ Technical Challenges

Fazenda Ativa is also an exploration of several engineering problems that are particularly relevant to mobile geospatial applications.

### Offline-first synchronization

Designing workflows that remain usable without connectivity requires separating:

**local state → pending operations → synchronization → cloud state**

rather than simply handling a network error.

### Geospatial data on mobile

Farm data can contain complex geometries and multiple coordinate reference systems.

The system therefore needs to handle:

* CRS transformations
* GeoJSON serialization
* geometry simplification
* spatial accuracy
* mobile rendering performance

### GPS uncertainty

A GPS position is not always an exact point.

The application incorporates positioning accuracy into field workflows rather than assuming every coordinate represents a precise measurement.

### Field-oriented UX

The application is designed for use outdoors and potentially under:

* sunlight
* unstable connectivity
* limited time
* imprecise touch interaction
* changing GPS accuracy

This makes simplicity and feedback particularly important.

### Reducing manual data entry

OCR and AI are used to transform unstructured information such as photographs and documents into structured farm data.

This introduces another challenge:

**automating data entry without hiding uncertainty from the user.**

---

# 🚀 Project Direction

Fazenda Ativa is evolving from a farm management application into a broader **geospatial intelligence platform for rural properties**.

Future development includes:

* 🤖 More AI-generated operational insights
* 📊 Interactive management dashboards
* 🐄 Cattle analytics
* 🌦️ Weather-based alerts
* 🛰️ Drone imagery integration
* 👁️ Computer vision for cattle and infrastructure
* 🗺️ Advanced spatial analysis
* 📡 Improved offline synchronization
* 📈 Historical farm analytics

The long-term objective is to combine **geospatial data, field observations, computer vision, AI, and operational data** into a single decision-support system for rural properties.

---

# 📸 Demos

The application is under active development. Screenshots and video demonstrations are available on my personal portfolio:

[View Fazenda Ativa demos](https://galvaomarcelo.github.io/my-page/?utm_source=chatgpt.com#projects)

---

# 👨‍💻 About the Developer

**Marcelo Galvão**
Computer Scientist · Ph.D. in Geoinformatics

Fazenda Ativa is also a practical project combining my background in:

* Geoinformatics
* Software Engineering
* Computational Geometry
* GIS
* Spatial Databases
* Mobile Development
* Artificial Intelligence
* Computer Vision

with a real-world rural application.

[Personal website](https://galvaomarcelo.github.io/my-page/?utm_source=chatgpt.com) · [LinkedIn](https://www.linkedin.com/in/marcelogalvaophd/?utm_source=chatgpt.com) · [email2marcelogalvao@gmail.com](mailto:email2marcelogalvao@gmail.com)

