# Connected Neighbours — Client Java (Admin Desktop)

Application desktop **JavaFX** d'administration pour le projet **Connected Neighbours**, une plateforme web/mobile de
quartier avec une API Node.js. Ce client Java est l'outil utilisé par les **modérateurs et administrateurs** pour gérer
les incidents, consulter les statistiques et superviser l'activité de la communauté.

## Sommaire

- [Fonctionnalites principales](#fonctionnalités-principales)
- [Prerequis](#prérequis)
- [Installation et lancement](#installation-et-lancement)
- [Architecture du projet](#architecture-du-projet)
- [Stack technique](#stack-technique)
- [Authentification (SSO)](#authentification-sso)
- [Mode offline-first](#mode-offline-first)
- [Synchronisation avec l'API](#synchronisation-avec-lapi)
- [Systeme de themes](#système-de-thèmes)
- [Systeme de plugins](#système-de-plugins)
- [Internationalisation (i18n)](#internationalisation-i18n)
- [Mode console](#mode-console)
- [Tests](#tests)
- [Etat d'avancement](#état-davancement)

---

## Fonctionnalites principales

- **Dashboard** : vue d'ensemble avec cartes de statistiques (incidents ouverts, en cours, resolus, non synchronises),
  tableau des incidents recents, panneau d'alertes et barre de statut de synchronisation.
- **Gestion des incidents** : liste filtrable (statut, categorie), creation, edition et suppression d'incidents via des
  dialogues modales, avec historique des modifications.
- **Statistiques** : fenêtre modale avec cartes de metriques (utilisateurs, annonces, evenements, votes, incidents) et
  graphiques PieChart (incidents par statut et par categorie).
- **Synchronisation automatique** : push/pull periodique avec l'API Node.js, resolution de conflits Last-Write-Wins,
  fonctionnement hors-ligne complet.
- **SSO / JWT** : authentification partagee avec l'application web via navigateur et callback loopback.
- **Themes** : clair, sombre et themes personnalises (CSS deposables dans `./themes/`). Tous les FXML utilisent des
  `styleClass` au lieu de styles inline.
- **Plugins extensibles** : architecture `ServiceLoader` avec chargement hybride (built-in + JARs externes dans
  `./plugins/`). Plugin de validation HelloPlugin fourni.
- **Mode console** : alternative texte au GUI via l'argument `--console`.
- **Internationalisation** : support FR / EN / ES via `ResourceBundle`.

---

## Prerequis

| Outil     | Version requise         |
|-----------|-------------------------|
| **JDK**   | 21+                     |
| **Maven** | 3.8+                    |
| **OS**    | Windows / macOS / Linux |

> Le projet utilise JavaFX 21. Aucune installation separee de JavaFX n'est necessaire : les dependances sont gerees par
> Maven.

---

## Installation et lancement

```bash
# Cloner le depot
git clone <url-du-repo>
cd Client-Java

# Compiler le projet
mvn compile

# Lancer l'application (mode GUI)
mvn javafx:run

# Lancer en mode console
mvn javafx:run -Djavafx.args="--console"

# Lancer les tests
mvn test

# Generer le fat JAR (toutes dependances incluses)
mvn clean package

# Executer le fat JAR
java -jar target/admin-desktop-1.0.0.jar            # mode GUI
java -jar target/admin-desktop-1.0.0.jar --console   # mode console
```

> **Note** : assurez-vous que `JAVA_HOME` pointe vers un JDK 21.

---

## Architecture du projet

Le projet suit le pattern **MVC** (Model-View-Controller) avec une couche service et repository.

```
com.connectedneighbours/
├── Launcher.java                    # Point d'entree : choisit GUI ou console (--console)
├── MainApp.java                     # Point d'entree JavaFX (login SSO ou demarrage offline)
├── ConsoleApp.java                  # Point d'entree mode console (menu texte)
├── AppContext.java                  # Contexte global (auth, API, user courant)
│
├── controller/                      # Controleurs FXML (MVC)
│   ├── BaseController.java          # Classe parente (sync bar, header, helpers)
│   ├── Page.java                    # Enum des pages pour la navigation
│   ├── DashboardController.java     # Dashboard principal (stats, tableau, alertes, sync)
│   ├── IncidentController.java      # Ecran incidents (TableView, filtres, CRUD, historique)
│   ├── StatisticsController.java    # Ecran statistiques (PieChart, cartes metriques)
│   └── SettingsController.java      # Parametres API (scheme, hote, port, test connexion)
│
├── model/                           # Entites metier
│   ├── Incident.java                # Incident (statut, priorite, localisation, flag synced)
│   ├── IncidentHistory.java         # Historique des modifications d'un incident
│   ├── Alert.java                   # Alerte de quartier
│   ├── District.java                # District (id, name)
│   ├── Statistic.java               # Statistiques agregees
│   ├── SyncLog.java                 # Log de synchronisation
│   └── User.java                    # Utilisateur (profil, TOTP, balance, etc.)
│
├── service/                         # Logique metier
│   ├── SyncService.java             # Push/pull auto, scheduler, resolution de conflits
│   ├── IncidentService.java         # CRUD incidents, validation, gestion du flag synced
│   ├── UpdateService.java           # Auto-update (comparaison version locale/serveur)
│   ├── ConnectivityChecker.java     # Interface de detection reseau
│   ├── SyncStatus.java              # Enum (OFFLINE, SYNCING, SUCCESS, ERROR, AUTH_REQUIRED)
│   ├── SyncStatusListener.java      # Listener pour mise a jour de l'UI
│   └── UiExecutor.java              # Interface pour executer sur le thread JavaFX
│
├── repository/                      # Acces BDD locale H2 + HTTP
│   ├── ApiClient.java               # Client HTTP OkHttp (GET/POST/PUT/DELETE avec JWT)
│   ├── ApiException.java            # Exception HTTP (404, etc.)
│   ├── DatabaseManager.java         # Schema SQL H2, initialisation connexion
│   ├── DistrictRepository.java      # CRUD districts
│   ├── IncidentRepository.java      # CRUD incidents (insert, findAll, findUnsynced, etc.)
│   ├── AlertRepository.java         # Requetes alertes (findRecent)
│   ├── StatisticRepository.java     # CRUD statistiques
│   ├── SyncLogRepository.java       # CRUD logs de synchronisation
│   └── UserRepository.java          # CRUD utilisateurs
│
├── plugin/                          # Systeme de plugins (ServiceLoader)
│   ├── Plugin.java                  # Interface (init, execute, shutdown)
│   ├── PluginManager.java           # Chargement hybride (built-in + JARs externes)
│   └── plugins/
│       ├── HelloPlugin.java         # Plugin de validation ServiceLoader
│       ├── ExportStatsPlugin.java   # Export CSV/PDF (echafaude — fichier vide)
│       ├── SocialAnalysisPlugin.java# Analyse interactions (implante, UI JavaFX + API)
│       └── LocalCalendarPlugin.java # Calendrier quartier (implante, UI JavaFX + API)
│
├── theme/                           # Gestion des themes CSS
│   ├── Theme.java                   # Classe immuable (id, displayName, cssUrl, builtin)
│   └── ThemeManager.java            # Persistance, scan ./themes/, applyTheme(Scene)
│
├── i18n/                            # Internationalisation
│   └── I18nManager.java             # Gestion ResourceBundle
│
├── config/                          # Configuration
│   ├── JacksonConfig.java           # ObjectMapper Jackson (dates ISO-8601)
│   ├── ApiConfig.java               # Config API persistee (scheme, host, port)
│   ├── AuthConfig.java              # Config auth-service URL (port 3001 par defaut)
│   └── SessionConfig.java           # Dernier user connecte (demarrage offline-first)
│
├── auth/                            # SSO / JWT
│   ├── SsoAuthService.java          # Flow SSO : navigateur -> callback loopback -> JWT
│   ├── CallbackServer.java          # Mini serveur HTTP loopback pour reception du token
│   ├── JwtVerifier.java             # Verification JWT via JWKS RSA
│   └── exception/
│       └── TokenUnavailableException.java
│
└── util/                            # Utilitaires
    ├── DatabaseUtil.java            # Utilitaires BDD
    └── ViewLoader.java              # Chargement de vues FXML
```

### Ressources

```
src/main/resources/
├── com/connectedneighbours/
│   ├── fxml/                        # Vues FXML de l'application
│   │   ├── dashboard.fxml           # Dashboard principal (151 lignes)
│   │   ├── header.fxml              # Header de navigation (41 lignes)
│   │   ├── incidents.fxml           # Ecran incidents (96 lignes)
│   │   ├── settings.fxml            # Parametres API (125 lignes)
│   │   └── statistics.fxml          # Ecran statistiques (83 lignes)
│   ├── css/                         # Themes CSS
│   │   ├── theme-light.css          # Theme clair (450 lignes, par defaut)
│   │   ├── theme-dark.css           # Theme sombre (471 lignes)
│   │   └── theme-template.css       # Modele commente pour themes personnalises (199 lignes)
│   └── images/                      # Icones et ressources visuelles
├── i18n/                            # Fichiers de traduction
│   ├── messages_fr.properties       # Francais (vide)
│   ├── messages_en.properties       # Anglais (vide)
│   └── messages_es.properties       # Espagnol (vide)
├── META-INF/
│   └── services/                    # Enregistrement ServiceLoader pour les plugins
└── logback.xml                      # Configuration logging
```

---

## Stack technique

### Dependances principales

| Dependance       | Version      | Role                                                    |
|------------------|--------------|---------------------------------------------------------|
| JavaFX           | 21.0.11-ea+4 | UI Desktop (controls, fxml, web, swing)                 |
| H2 Database      | 2.4.240      | BDD embarquee offline-first (`jdbc:h2:./data/admin_db`) |
| OkHttp           | 4.12.0       | Client HTTP pour les appels vers l'API Node.js          |
| Jackson Databind | 2.22.0       | Serialisation / deserialisation JSON                    |
| Jackson JSR310   | 2.22.0       | Support `java.time` (LocalDateTime, etc.)               |
| Auth0 java-jwt   | 4.5.2        | Decodage et verification JWT                            |
| Auth0 jwks-rsa   | 0.22.1       | Fetch des cles JWKS RSA pour la signature JWT           |
| JUnit 5          | 5.14.4       | Framework de tests unitaires                            |
| Mockito          | 5.23.0       | Mocking pour les tests                                  |

### Plugins Maven

| Plugin                  | Version | Role                                      |
|-------------------------|---------|-------------------------------------------|
| `javafx-maven-plugin`   | 0.0.8   | `mvn javafx:run` — lancement de l'app GUI |
| `maven-shade-plugin`    | 3.6.2   | Fat JAR avec toutes les dependances       |
| `maven-surefire-plugin` | 3.5.6   | Execution des tests unitaires             |
| `maven-compiler-plugin` | 3.15.0  | Compilation Java 21                       |

### Coordonnees Maven

```xml

<groupId>com.connectedneighbours</groupId>
<artifactId>admin-desktop</artifactId>
<version>1.0.0</version>
```

---

## Authentification (SSO)

L'application utilise un systeme **Single Sign-On** (SSO) partage avec l'application web Connected Neighbours :

1. **Premier lancement** : le navigateur du systeme s'ouvre sur la page de login de l'auth-service (port 3001 par
   defaut).
2. **Callback loopback** : apres authentification, le serveur redirige vers un mini serveur HTTP local (
   `CallbackServer`) qui capture le JWT.
3. **Token JWT** : l'access token est conserve **en memoire uniquement** (jamais persiste sur disque). Il est joint a
   chaque requete HTTP via le header `Authorization: Bearer`.
4. **Verification** : le token est verifie localement via les cles JWKS RSA exposees par l'auth-service (`JwtVerifier`).

> L'URL de l'auth-service est configurable dans l'ecran **Parametres** (`AuthConfig`, persiste via `java.util.prefs`).

---

## Mode offline-first

L'application est concue pour fonctionner **integralement hors-ligne** :

- Toutes les lectures et ecritures passent d'abord par la base H2 locale (`./data/admin_db`).
- Un flag `synced` sur chaque entite marque ce qui reste a synchroniser avec le serveur.
- Le **demarrage offline** est automatique : si un utilisateur a deja ete connecte (`SessionConfig`) et que la base H2
  contient des donnees, le login SSO est skippe et le dashboard s'affiche directement.

### Comportement au lancement (GUI)

| Scenario                                              | Comportement                                                     |
|-------------------------------------------------------|------------------------------------------------------------------|
| 1er lancement (pas de user memorise ou H2 vide)       | Login SSO via navigateur, puis memorisation du user              |
| Lancements suivants (user memorise + donnees locales) | Dashboard direct, hors-ligne, sans ouvrir le navigateur          |
| Bouton "Deconnexion"                                  | Efface le user memorise ; prochain demarrage exigera un re-login |
| Sync sans token (session restauree)                   | `AUTH_REQUIRED` : re-login navigateur automatique                |

---

## Synchronisation avec l'API

La synchronisation est geree par `SyncService` :

- **Scheduler** : un `ScheduledExecutorService` execute la synchronisation a intervalle regulier.
- **Detection reseau** : verifie la connectivite avant chaque cycle via `ConnectivityChecker`.
- **Push** : envoie les entites locales non synchronisees (`synced = false`) vers l'API.
- **Pull** : recupere les donnees du serveur et met a jour la base locale.
- **Resolution de conflits** : strategie **Last-Write-Wins** basee sur le champ `updated_at`. Les conflits sont
  journalises dans la table `sync_log` via `SyncLogRepository`.

### Etats de synchronisation (`SyncStatus`)

| Etat            | Description                                        |
|-----------------|----------------------------------------------------|
| `OFFLINE`       | Pas de connexion reseau                            |
| `SYNCING`       | Synchronisation en cours                           |
| `SUCCESS`       | Derniere synchronisation reussie                   |
| `ERROR`         | Erreur reseau ou serveur                           |
| `AUTH_REQUIRED` | Token JWT absent ou expire, reconnexion necessaire |

---

## Systeme de themes

L'application supporte des themes CSS interchangeables :

- **Themes integres** : `light` (clair, par defaut) et `dark` (sombre).
- **Themes personnalises** : deposer un fichier `.css` dans le dossier `./themes/` a la racine du projet. Le nom du
  fichier (sans `.css`) devient l'identifiant et le libelle affiche.
- **Persistance** : le theme selectionne est sauvegarde via `java.util.prefs` (cle `theme.id`).
- **Application** : dans les parametres, selectionner le theme puis cliquer sur "Recharger" pour appliquer et re-scanner
  le dossier.
- **Template** : le fichier `theme-template.css` dans les ressources sert de modele commente pour creer de nouveaux
  themes.

> Tous les FXML de l'application (`dashboard.fxml`, `header.fxml`, `incidents.fxml`, `settings.fxml`,
> `statistics.fxml`) utilisent des `styleClass` plutot que des styles inline. Le vocabulaire CSS partage inclut :
> `.card`, `.header`, `.nav-button`, `.button-primary`, etc.

---

## Systeme de plugins

L'architecture de plugins repose sur `java.util.ServiceLoader` avec un chargement hybride :

1. **Interface `Plugin`** : definit les methodes `getName()`, `getVersion()`, `initialize()`, `execute(Object)`,
   `shutdown()`.
2. **`PluginManager`** : charge les plugins built-in declares dans
   `META-INF/services/com.connectedneighbours.plugin.Plugin`, plus les plugins externes
   (JARs deposes dans le dossier `./plugins/`).
3. **Plugins fournis** :
    - `HelloPlugin` : plugin de validation du cycle de vie `ServiceLoader`.
    - `ExportStatsPlugin` : export des statistiques en CSV ou PDF (echafaude — fichier vide).
    - `SocialAnalysisPlugin` : analyse des interactions sociales (implante — UI JavaFX avec
      onglets, TableView et PieChart, appels API via `ApiClient`).
    - `LocalCalendarPlugin` : calendrier des evenements du quartier (implante — UI JavaFX avec
      TableView + filtre, donnees API en asynchrone).

---

## Internationalisation (i18n)

L'application supporte trois langues via `ResourceBundle` :

- Francais (`messages_fr.properties`)
- Anglais (`messages_en.properties`)
- Espagnol (`messages_es.properties`)

Le changement de langue se fait a chaud depuis l'ecran des parametres via une `ComboBox`.

---

## Mode console

L'application peut etre lancee en mode texte via l'argument `--console` :

```bash
java -jar admin-desktop-1.0.0.jar --console
```

Le `Launcher.java` detecte l'argument et redirige vers `ConsoleApp.java` au lieu de `MainApp.java`. Le mode console
permet de :

- Consulter la liste des incidents
- Consulter les statistiques
- Lancer une synchronisation manuelle

> Le mode console n'utilise pas le SSO et n'est pas concerne par le flow d'authentification navigateur.

---

## Tests

Les tests utilisent **JUnit 5** et **Mockito** :

```bash
mvn test
```

| Fichier de test               | Couverture                 |
|-------------------------------|----------------------------|
| `ApiClientTest.java`          | Client HTTP (OkHttp)       |
| `SyncServiceTest.java`        | Service de synchronisation |
| `IncidentRepositoryTest.java` | CRUD incidents en H2       |
| `IncidentServiceTest.java`    | Service metier incidents   |

---

## Etat d'avancement

### Implemente

- Base de donnees H2 + `DatabaseManager` (schema complet)
- Modeles : `Incident`, `Alert`, `Statistic`, `User`, `District`, `IncidentHistory`, `SyncLog`
- Repositories : `IncidentRepository`, `AlertRepository`, `StatisticRepository`, `SyncLogRepository`,
  `UserRepository`, `DistrictRepository`, `ApiClient` (OkHttp GET/POST/PUT/DELETE avec JWT)
- `IncidentService` (CRUD, validation, gestion du flag synced)
- `SyncService` (push/pull, scheduler, detection connexion, resolution de conflits Last-Write-Wins)
- Dashboard (`dashboard.fxml` + `DashboardController`) — cartes stats, tableau incidents, alertes, barre de sync
- Ecran Incidents (`incidents.fxml` + `IncidentController`, 609 lignes) — `TableView` complete avec 10 colonnes,
  filtres (statut, categorie), double-clic edition, creation, historique
- Ecran Statistiques (`statistics.fxml` + `StatisticsController`, 129 lignes) — 5 cartes metriques (utilisateurs,
  annonces, evenements, votes, incidents), 2 PieCharts (incidents par statut et par categorie), `StatisticRepository`
  consomme
- Parametres API (`settings.fxml` + `SettingsController`, 393 lignes) — config API, test de connexion,
  themes, langue
- `BaseController` — classe parente avec barre de sync mutualisee, hook `onSyncSuccess()`, re-login automatique
- SSO complet (`SsoAuthService`, `CallbackServer`, `JwtVerifier`)
- `ThemeManager` + themes clair/sombre + support themes personnalises via dossier `./themes/`
- Tous les FXML refactores : styles inline → `styleClass` du vocabulaire CSS partage
- `PluginManager` (210 lignes) — chargement hybride (built-in `ServiceLoader` + JARs externes `./plugins/`),
  cycle de vie complet (init/execute/shutdown), gestion d'erreurs isolee par plugin
- `HelloPlugin` — plugin de validation du cycle de vie
- `SocialAnalysisPlugin` (414 lignes) — UI JavaFX (TabPane, TableView, PieChart) + appels API
  `/api/social/...` (interactions, top contributeurs)
- `LocalCalendarPlugin` (212 lignes) — UI JavaFX (TableView + ComboBox de filtre) + appels API
  `/api/events` en asynchrone (`CompletableFuture`)
- Mode console (`Launcher` + `ConsoleApp`)
- Demarrage offline-first (`SessionConfig`)
- `AppContext` (contexte global partage, injection dans les controleurs)

### Echafaude (structure prete, logique a completer)

- `ExportStatsPlugin` — export CSV/PDF des statistiques (fichier vide, seul plugin restant)
- `I18nManager` + fichiers de traduction (FR/EN/ES, vide)
- `UpdateService` (auto-update)
- `ViewLoader` (utilitaire FXML)

### Pas encore commence

- MFA pour actions sensibles (verification TOTP locale)
- Desinstallation depuis l'UI
- Fat JAR packaging (config Maven Shade presente, non testee en build reel)

> Pour le suivi detaille des taches, voir `TODO.md`. Pour le contexte technique complet, voir `RESUME.md`.

---

## Conventions du projet

- **Repositories** : SQL brut via `DatabaseUtil.executeQuery/executeUpdate` (voir `StatisticRepository.java` comme
  reference).
- **Configuration persistee** : `java.util.prefs.Preferences` (voir `ApiConfig`, `AuthConfig`).
- **Logging** : `java.util.logging` ou `e.printStackTrace()` (SLF4J/Logback retires du pom.xml).
- **Injection** : `AppContext` est le point d'injection central — les controleurs le recoivent en parametre.
- **Pattern MVC** : vues en FXML, logique dans les controleurs, donnees dans les modeles.
- **Styles CSS** : utiliser des `styleClass` dans les FXML, jamais de styles inline.

