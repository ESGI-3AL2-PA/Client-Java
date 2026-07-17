# Plugins — `PluginManager`

L'application supporte un **système de plugins** extensible, chargés au
démarrage via [`java.util.ServiceLoader`][sl]. Deux familles coexistent :

- les plugins **built-in**, compilés dans l'app et inscrits dans
  `META-INF/services` ;
- les plugins **externes**, déposés sous forme de `.jar` dans le dossier
  `./plugins/` et chargés au démarrage via un `URLClassLoader` dédié.

Les plugins ne sont chargés **qu'au démarrage** de l'app — il n'y a pas de
rechargement à chaud. Pour activer un nouveau `.jar` externe, il faut
relancer l'app.

[sl]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html

## Sommaire

- [Interface `Plugin`](#interface-plugin)
- [Plugins built-in](#plugins-built-in)
- [Plugins externes](#plugins-externes)
- [Cycle de vie](#cycle-de-vie)
- [Accès au contexte applicatif](#accès-au-contexte-applicatif)
- [API `PluginManager`](#api-pluginmanager)
- [Créer un plugin — exemple complet](#créer-un-plugin--exemple-complet)
- [Règles & limitations](#règles--limitations)
- [Fichiers sources](#fichiers-sources)

---

## Interface `Plugin`

Tout plugin implémente l'interface
`com.connectedneighbours.plugin.Plugin` (non modifiable) :

```java
public interface Plugin {
    String getName();
    String getVersion();
    void initialize();
    void execute(Object context);
    void shutdown();
}
```

| Méthode        | Rôle                                                          | Appelée par              |
|----------------|---------------------------------------------------------------|--------------------------|
| `getName`      | Identifiant stable du plugin (affiché dans les logs, lookup)  | `PluginManager`          |
| `getVersion`   | Version lisible (ex: `"1.0.0"`)                               | `PluginManager`          |
| `initialize`   | Setup une fois au chargement (ressources, connexions…)        | `loadAll()`              |
| `execute`      | Action à la demande ; reçoit un `Object` (voir ci-dessous)    | `execute()` / `executeAll()` |
| `shutdown`     | Libération des ressources à l'arrêt de l'app                  | `shutdownAll()`          |

---

## Plugins built-in

Un plugin **built-in** est une classe du module `com.connectedneighbours.plugin`
compilée dans le classpath de l'app. Pour l'inscrire, on déclare son FQCN dans
le fichier service de `ServiceLoader` :

**Fichier** :
`src/main/resources/META-INF/services/com.connectedneighbours.plugin.Plugin`

```
com.connectedneighbours.plugin.plugins.HelloPlugin
com.connectedneighbours.plugin.plugins.ExportStatsPlugin
com.connectedneighbours.plugin.plugins.SocialAnalysisPlugin
com.connectedneighbours.plugin.plugins.LocalCalendarPlugin
```

> Un FQCN par ligne. Les lignes vides et celles commençant par `#` sont
> ignorées (comportement standard de `ServiceLoader`).

Au démarrage, `PluginManager.loadAll()` → `loadBuiltin()` itère sur
`ServiceLoader.load(Plugin.class)` (classloader de l'app) et collecte toutes
les implémentations déclarées. Chaque plugin reçoit `initialize()` dans un
bloc try/catch — un plugin qui lève une exception ne casse pas les autres.

### Plugin built-in de validation : `HelloPlugin`

`HelloPlugin` (cf. `plugin/plugins/HelloPlugin.java`) est un plugin **no-op**
qui ne fait que logguer via `java.util.logging` à chaque étape du cycle de
vie. Il prouve que le câblage `ServiceLoader` fonctionne end-to-end et sert de
**modèle minimal** pour écrire un nouveau plugin built-in. À retirer du
fichier service une fois les vrais plugins (tâches 12-14) inscrits.

---

## Plugins externes

Un plugin **externe** est un `.jar` déposé par l'utilisateur dans le dossier
`./plugins/` (relatif au répertoire de travail de l'app, au même niveau que
`./data/` pour la BDD H2 et `./themes/` pour les thèmes perso). C'est le
parallèle exact du dossier des thèmes personnalisés.

### Pré-requis du `.jar`

1. Le jar contient une (ou plusieurs) implémentation(s) de
   `com.connectedneighbours.plugin.Plugin`.
2. Le jar contient **son propre** fichier
   `META-INF/services/com.connectedneighbours.plugin.Plugin` listant les FQCN
   des plugins qu'il fournit — c'est ce que lit `ServiceLoader`.
3. Le jar a été compilé **contre l'API publique de l'app** (au minimum
   l'interface `Plugin` ; voir
   [Créer un plugin externe](#créer-un-plugin--exemple-complet)).

### Chargement

`PluginManager.loadExternal()` :

1. Scan de `./plugins/` pour récupérer tous les fichiers `*.jar`
   (insensible à la casse).
2. Si aucun jar → `externalPlugins = List.of()` et retour immédiat
   (comportement par défaut au 1er lancement : dossier vide, 0 plugin externe).
3. Construction d'un `URLClassLoader` dont le **parent** est le classloader de
   `PluginManager` — ainsi les jars externes voient l'interface `Plugin` et
   `AppContext` définies dans l'app.
4. `ServiceLoader.load(Plugin.class, externalLoader)` récupère les
   implémentations déclarées dans chaque jar externe.
5. Chaque plugin reçu est ajouté à `externalPlugins` ; `initialize()` est
   appelé (try/catch par plugin).

> Un jar corrompu ou une URL invalide ne stoppe pas le chargement des autres
> jars — chaque erreur est logguée via `java.util.logging` et le jar fautif
> est ignoré.

---

## Cycle de vie

Câblage dans `MainApp` :

| Phase                 | Appel `PluginManager`                            | Effet                                   |
|-----------------------|--------------------------------------------------|-----------------------------------------|
| `MainApp.start()`     | `init(appContext)` puis `loadAll()`              | Stocke le contexte + charge tout        |
| `MainApp.stop()`      | `shutdownAll()`                                  | Appelle `shutdown()` en ordre inverse   |

Ordre d'`initialize()` : **built-in d'abord, externes ensuite**.
Ordre de `shutdown()` : **inverse de la liste fusionnée** (les externes
s'arrêtent avant les built-in).

`shutdownAll()` est **idempotente** : un deuxième appel est un no-op (listes
vidées, contexte remis à `null`, classloader externe fermé).

---

## Accès au contexte applicatif

L'interface `Plugin` n'expose pas de contexte à `initialize()` (signature
sans argument). Pour donner aux plugins l'accès à l'app (ApiClient,
repositories, user courant), `PluginManager` stocke l'`AppContext` passé à
`init(...)` et l'expose via `getContext()` :

```java
@Override
public void initialize() {
    AppContext ctx = PluginManager.getContext();
    if (ctx == null) {
        // Pas encore initialisé — log + return
        return;
    }
    ApiClient api = ctx.getApiClient();
    User user = ctx.getCurrentUser();
    // ... préparation des ressources du plugin
}
```

`AppContext` est documenté dans `RESUME.md` comme le **point d'injection
central** de l'app : il expose `getAuthService()`, `getApiClient()`,
`getCurrentUser()`, `isAuthenticated()`, `logout()`. Les plugins peuvent
l'utiliser pour faire des appels HTTP authentifiés, lire l'utilisateur
connecté, etc.

> `execute(Object context)` peut aussi recevoir un objet arbitraire passé par
> l'appelant (`executeAll(input)` ou `execute(name, input)`). Pour les
> exécutions « spontanées » de l'app, l'objet passé est à la discrétion de
> l'appelant — un plugin robuste teste le type avant de caster.

---

## API `PluginManager`

Toutes les méthodes sont `static` (classe `final`, constructeur privé —
parallèle à `ThemeManager`).

| Méthode                                       | Rôle                                                        |
|-----------------------------------------------|-------------------------------------------------------------|
| `init(AppContext ctx)`                        | Stocke le contexte applicatif partagé                       |
| `getContext()`                                | Récupère le contexte (pour les plugins)                     |
| `loadAll()`                                   | Charge built-in + externes, appelle `initialize()`          |
| `getPlugins()`                                | Liste non modifiable (built-in puis externes)               |
| `getPlugin(String name)`                      | Lookup par `getName()` → `Optional<Plugin>`                 |
| `executeAll(Object input)`                    | Appelle `execute(input)` sur chaque plugin                  |
| `execute(String name, Object input)`          | Exécute un plugin nommé (log si introuvable)                |
| `shutdownAll()`                               | Appelle `shutdown()` en ordre inverse, ferme le classloader |
| `getPluginsDir()`                             | Dossier `./plugins/` (auto-créé)                            |

Toutes les méthodes mutatrices (`loadAll`, `shutdownAll`) sont `synchronized`. Les méthodes `safeInitialize`,
`safeExecute`, `safeShutdown` interceptent `Throwable` par plugin afin qu'une
erreur dans un plugin n'impacte pas les autres.

---

## Créer un plugin — exemple complet

### 1. Plugin built-in minimal

```java
package com.connectedneighbours.plugin.plugins;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.plugin.Plugin;
import com.connectedneighbours.plugin.PluginManager;

import java.util.logging.Logger;

public class HelloPlugin implements Plugin {

    private static final Logger LOG = Logger.getLogger(HelloPlugin.class.getName());

    @Override
    public String getName()    { return "HelloPlugin"; }
    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public void initialize() {
        LOG.info("HelloPlugin initialized");
    }

    @Override
    public void execute(Object context) {
        LOG.info("HelloPlugin executed (context="
                + (context == null ? "null" : context.getClass().getSimpleName()) + ")");
    }

    @Override
    public void shutdown() {
        LOG.info("HelloPlugin shutdown");
    }
}
```

Puis inscrire dans `META-INF/services/com.connectedneighbours.plugin.Plugin` :
```
com.connectedneighbours.plugin.plugins.HelloPlugin
```

Recompiler (`mvn -q compile`) et relancer l'app. Les logs `HelloPlugin
initialized` / `HelloPlugin shutdown` confirment le câblage.

### 2. Plugin externe (dans un projet Maven séparé)

Le plugin externe doit compiler contre l'interface `Plugin` (et
`AppContext`/`PluginManager` s'il veut le contexte). Tant que ces classes ne
sont pas publiées dans un module d'API dédié, la solution la plus simple est
d'installer le fat jar de l'app dans le `.m2` local et de le déclarer en
dépendance `provided` :

**`pom.xml` du plugin externe** :
```xml
<dependencies>
    <dependency>
        <groupId>com.connectedneighbours</groupId>
        <artifactId>admin-desktop</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**`src/main/java/com/example/MyPlugin.java`** :
```java
package com.example;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.plugin.Plugin;
import com.connectedneighbours.plugin.PluginManager;

import java.util.logging.Logger;

public class MyPlugin implements Plugin {

    private static final Logger LOG = Logger.getLogger(MyPlugin.class.getName());

    @Override public String getName()    { return "MyPlugin"; }
    @Override public String getVersion() { return "1.0.0"; }

    @Override
    public void initialize() {
        AppContext ctx = PluginManager.getContext();
        LOG.info("MyPlugin initialized, user="
                + (ctx != null && ctx.getCurrentUser() != null
                        ? ctx.getCurrentUser().getEmail()
                        : "<none>"));
    }

    @Override
    public void execute(Object input) {
        LOG.info("MyPlugin executed");
    }

    @Override
    public void shutdown() {
        LOG.info("MyPlugin shutdown");
    }
}
```

**`src/main/resources/META-INF/services/com.connectedneighbours.plugin.Plugin`** :
```
com.example.MyPlugin
```

Build :
```powershell
mvn -q clean package
# produit target/my-plugin-1.0.0.jar
```

Déploiement :
```powershell
cp target/my-plugin-1.0.0.jar ./plugins/
```

Au prochain lancement de l'app, `loadExternal()` trouve le jar, construit le
`URLClassLoader`, et `ServiceLoader` instancie `com.example.MyPlugin`. Les
logs afficheront `Plugin initialisé : MyPlugin v1.0.0`.

> Les plugins ne sont chargés **qu'au démarrage**. Pour activer un nouveau
> `.jar` déposé dans `./plugins/`, il faut relancer l'app (aucun
> rechargement à chaud).

---

## Règles & limitations

- **Dossier** : `./plugins/` (relatif au répertoire de travail). Auto-créé au
  premier appel de `getPluginsDir()`.
- **Format accepté** : uniquement `*.jar` (insensible à la casse). Les
  sous-dossiers et autres extensions sont ignorés.
- **Fichier service requis dans chaque jar externe** : sans
  `META-INF/services/com.connectedneighbours.plugin.Plugin`, le jar est
  chargé mais aucun plugin n'est découvert (silencieusement).
- **Stabilité du nom** : `getName()` doit être stable et unique parmi tous
  les plugins chargés — c'est la clé utilisée par `getPlugin(name)` et
  `execute(name, input)`.
- **Isolation des erreurs** : un plugin qui lève dans `initialize()`,
  `execute()` ou `shutdown()` est loggué (`WARNING`) mais n'empêche pas les
  autres de tourner. En revanche, un plugin qui plante en `initialize()` est
  quand même conservé dans la liste (il peut éventuellement réussir un
  `execute()` ultérieur) — à l'auteur du plugin de gérer son état interne.
- **Sécurité** : aucun sandbox. Un plugin externe a accès à tout le classpath
  de l'app, au système de fichiers, au réseau. Ne déposez un jar dans
  `./plugins/` que si vous en maîtrisez le contenu.
- **API publique** : l'interface `Plugin` et `AppContext` sont les seuls
  contrats stables pour les plugins externes. Tout autre package
  (`service.*`, `repository.*`, `controller.*`) est considéré comme interne
  et peut changer à tout moment.

---

## Fichiers sources

### Java (classpath : `src/main/java/com/connectedneighbours/plugin/`)

| Fichier                                  | Rôle                                                       |
|------------------------------------------|------------------------------------------------------------|
| `Plugin.java`                            | Interface (non modifiable) : getName/getVersion/init/exec/shutdown |
| `PluginManager.java`                     | Chargement hybride (ServiceLoader + URLClassLoader), cycle de vie |
| `plugins/HelloPlugin.java`               | Stub built-in de validation (à retirer)                    |

### Service file

| Fichier (classpath)                                                       | Rôle                                  |
|---------------------------------------------------------------------------|---------------------------------------|
| `src/main/resources/META-INF/services/com.connectedneighbours.plugin.Plugin` | Liste des FQCN des plugins built-in |

### Runtime

| Chemin        | Rôle                                                  |
|---------------|-------------------------------------------------------|
| `./plugins/`  | Dossier des `.jar` externes (auto-créé)               |

### Intégration

- `MainApp.start()` → `PluginManager.init(appContext)` puis
  `PluginManager.loadAll()` juste après `ThemeManager.reloadCustomThemes()`.
- `MainApp.stop()` → `PluginManager.shutdownAll()` juste avant
  `DatabaseManager.close()`.

### Logging

- Aucun framework de logging (SLF4J/Logback retirés du `pom.xml`) — le code
  utilise `java.util.logging` (cf. `PluginManager.LOG`).
- Messages émis : `Plugin initialisé : <name> v<version>`,
  `Plugin arrêté : <name>`, `Plugins chargés : N (built-in=X, externes=Y)`,
  et avertissements `Échec initialize/execute/shutdown() pour le plugin ...`.

### Convention

- Classe `final` + constructeur privé + méthodes `static` + `synchronized`
  sur les mutatrices = convention identique à `ThemeManager`.
- Persistance : aucune (le `PluginManager` est stateless entre lancements ;
  la liste des plugins dépend uniquement du classpath et du dossier
  `./plugins/` au démarrage).
