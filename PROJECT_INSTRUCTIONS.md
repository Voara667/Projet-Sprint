# Instructions du projet : Framework Web "Front Controller" maison

Ce fichier est la mémoire durable du projet. Il doit être recopié en entier au début de toute nouvelle discussion portant sur un sprint suivant, pour ne pas avoir à réexpliquer le contexte à chaque fois. Il doit aussi être committé à la racine du dépôt du framework et mis à jour à la fin de chaque sprint, avant la fusion de la Pull Request correspondante.

## 1. Objectif général du projet

Construire, sprint par sprint, un framework web Java fait maison qui reproduit en simplifié le rôle d'un framework comme Spring MVC : un point d'entrée unique (le Front Controller) qui reçoit toutes les requêtes HTTP, les associe à un controller et à une méthode d'action précise, invoque réellement cette méthode, et dont l'initialisation est déclenchée dès le chargement de l'application (pas à la première requête).

Le framework est compilé en `.jar` et déployé dans une application web de test, elle-même déployée sur Tomcat. L'application de test sert uniquement à valider le framework, elle n'a pas vocation à devenir un vrai projet métier.

## 2. Architecture générale

Quatre éléments interviennent :

- **Navigateur** : envoie une requête HTTP, par exemple `http://localhost:8080/test-app/dept/new`.
- **Tomcat / Serveur** : reçoit la requête et la transmet à l'application web déployée sous le contexte `/test-app`.
- **Application de test** : contient le `web.xml`, le dossier `WEB-INF/lib` où est copié le `.jar` du framework, `WEB-INF/classes` où sont compilées ses propres classes controllers.
- **Framework** : compilé en `.jar`, contient la mécanique générique (Front Controller, Listener, scan des controllers, résolution des routes, invocation des actions) indépendante de toute application précise.

Le `web.xml` de l'application de test déclare :

- Un Listener du framework (`ControllerScannerListener`), exécuté au démarrage de l'application, avant tout Servlet. **Depuis le sprint 4**, c'est ce Listener qui porte réellement le scan des controllers (et non plus `FrontServletController.init()`).
- Le Servlet `FrontServletController` du framework, mappé sur toutes les URL de l'application (`/*`), pour intercepter toutes les requêtes au lieu de laisser Tomcat afficher un contenu par défaut (listing de dossier, etc.).

Il n'y a pas de context-param lié à un fichier de mapping XML, et pas de `<load-on-startup>` sur le Servlet : le `web.xml` ne contient que la déclaration du listener et du servlet.

## 3. Décisions techniques figées

Ces choix sont validés et ne doivent pas être remis en question à chaque nouveau sprint sans raison explicite :

- **API Servlet** : Jakarta Servlet (paquet `jakarta.servlet.*`), car Tomcat 10 ou plus est utilisé. Ne jamais utiliser `javax.servlet.*`.
- **Construction** : pas de Maven ni Gradle. Compilation manuelle via `javac`, archivage via `jar`, le tout orchestré par le script `deploy.sh`. Structure imposée du dépôt : `build/`, `src/`, `deploy.sh`, `README.md`, `framework.jar` (généré par `deploy.sh`).
- **Dépôt GitHub** : seul le dépôt du framework est versionné sur GitHub (le dépôt existe déjà). L'application de test reste locale, elle sert uniquement de banc d'essai.
- **Workflow Git** : une branche par sprint (`sprint0`, `sprint1`, ..., `sprint3bis`, `sprint4`), créée depuis `main`, fusionnée via Pull Request sur GitHub. Projet en solo, donc auto-revue avant fusion.
- **Paquetage racine du framework** : `framework.core` (annotations dans `framework.core.annotation`). Paquetage de l'application de test : `testapp.controller`. À garder cohérent sur tout le projet une fois fixé ; modifiable uniquement si une contrainte externe (formateur, énoncé) l'impose.
- **Configuration des routes** : plus de fichier `mapping.xml`. La résolution URL -> controller se fait par annotations :
  - `@Controller` (sur la classe) marque une classe comme controller à détecter. Reste un simple marqueur, sans attribut.
  - `@RequestMapping` : portée sur la méthode (`@Target(ElementType.METHOD)`) depuis le sprint 2, pour permettre à une seule classe controller de regrouper plusieurs actions/URL, comme dans Spring MVC.
  - Attribut `value()` (`String`, URL avec slash initial, ex. `/dept/new`).
  - Attribut `method()` : **depuis le sprint 3 bis**, de type **`HttpMethod`** (enum `framework.core`, valeurs `GET`/`POST`), défaut `HttpMethod.GET`. Remplace le `String` utilisé au sprint 3.
  - Convention d'URL : toujours avec slash initial, ex. `/dept/new`, `/dept/list`.
  - **Depuis le sprint 4** : le scan de `WEB-INF/classes` via `ClassScanner`, et la construction de la map de routes, ont lieu dans `ControllerScannerListener.contextInitialized()` (via la classe utilitaire `RouteLoader`), donc dès le **chargement de l'application** (T0), et non plus à la première requête (comme c'était le cas quand cette logique vivait dans `FrontServletController.init()`, jusqu'au sprint 3 bis inclus).
  - La map de routes (`Map<UrlMethod, RouteInfo>`) est stockée comme attribut du `ServletContext`, sous la clé **`"framework.routes"`**, posée dans `contextInitialized()` et retirée dans `contextDestroyed()` (`removeAttribute`).
  - `FrontServletController.init(ServletConfig)` ne fait plus de scan : il récupère la map une seule fois depuis l'attribut du `ServletContext` et la met en cache dans un champ privé, pour éviter un lookup + cast à chaque requête dans `processRequest`.
  - Une classe `@Controller` sans aucune méthode annotée `@RequestMapping` est ignorée avec un avertissement loggé.
  - La clé de routage est le couple (URL, méthode HTTP), porté par la classe `UrlMethod` (`equals`/`hashCode` surchargés : URL sensible à la casse ; la méthode HTTP étant un enum depuis le sprint 3 bis, sa comparaison est une simple égalité de constante, plus besoin de logique d'insensibilité à la casse).
  - **Depuis le sprint 3 bis** : la map est **`Map<UrlMethod, RouteInfo>`** (une seule route par clé, plus de `List`). Un doublon exact (même URL **et** même méthode HTTP) déclenche désormais une erreur bloquante (`DuplicateRouteException extends ServletException`) au moment de la construction des routes — comportement fail-fast, ce n'est plus toléré comme aux sprints 1 à 3. Deux actions peuvent toujours partager la même URL tant qu'elles diffèrent par la méthode HTTP.
  - **Depuis le sprint 4** : comme `ServletContextListener.contextInitialized()` ne déclare aucun `throws`, toute exception vérifiée levée pendant le scan (dont `DuplicateRouteException`, mais aussi potentiellement des exceptions de réflexion ou d'I/O) est encapsulée dans **`FrameworkStartupException extends RuntimeException`** puis relancée — ce qui fait échouer le déploiement du contexte entier dans Tomcat, visible dans les logs au chargement (T0), sans rattrapage silencieux.
  - Une URL/méthode HTTP demandée qui ne correspond à aucune route connue déclenche une exception : `UrlNotFoundException` (404, URL totalement inconnue) ou `HttpMethodNotSupportedException` (405, URL connue mais méthode HTTP différente, message listant les méthodes HTTP réellement supportées pour cette URL).
  - **Depuis le sprint 3 bis** : une fois la route trouvée, `processRequest` **instancie réellement** le controller (constructeur par défaut) et **invoque** la méthode d'action via réflexion (`method.invoke(instance)`), sans paramètre. Cycle de vie **« prototype »** : une nouvelle instance du controller est créée à chaque requête (le mode singleton est repoussé à un sprint ultérieur). Une erreur d'invocation (`InvocationTargetException` et consorts) déclenche une réponse **HTTP 500** avec message.
  - Limite connue : le scan ne lit que `WEB-INF/classes` (répertoire de fichiers `.class`), pas l'intérieur des `.jar` de `WEB-INF/lib`.

## 4. Glossaire des termes utilisés dans ce projet

- **Front Controller** : Servlet unique qui reçoit toutes les requêtes HTTP de l'application et décide du traitement à appliquer. Classe : `FrontServletController`.
- **Listener (`ControllerScannerListener`)** : classe qui s'exécute automatiquement au démarrage (et à l'arrêt) de l'application web, avant l'initialisation des Servlets. **Depuis le sprint 4**, porte réellement le scan des controllers (via `RouteLoader`) dans `contextInitialized()`, et pose la map de routes comme attribut du `ServletContext` (`"framework.routes"`) ; retire cet attribut dans `contextDestroyed()`. Le nom de la classe est enfin cohérent avec son rôle.
- **`RouteLoader`** *(nouveau, sprint 4)* : classe utilitaire du framework (`framework.core`) qui contient la logique de scan et de construction de la map de routes (`Map<UrlMethod, RouteInfo>`), extraite de l'ancien `FrontServletController.init()`. Appelée depuis `ControllerScannerListener.contextInitialized()`. Lève les exceptions liées au scan (dont `DuplicateRouteException`), à charge du Listener de les encapsuler en `FrameworkStartupException`.
- **ClassScanner** : classe utilitaire du framework qui parcourt récursivement un répertoire de classes compilées (typiquement `WEB-INF/classes`) et renvoie la liste des `Class<?>` annotées `@Controller`.
- **`@Controller`** : annotation de classe (`framework.core.annotation`), marqueur pur, sans attribut, qui indique qu'une classe doit être scannée pour y chercher des méthodes d'action.
- **`@RequestMapping`** : annotation portée sur les méthodes (`@Target(ElementType.METHOD)`), qui associe une URL (avec slash initial) et une méthode HTTP (`HttpMethod`, défaut `GET`) à une méthode d'action précise d'une classe controller.
- **`HttpMethod`** *(nouveau, sprint 3 bis)* : enum `framework.core` avec les constantes `GET` et `POST` (pas d'autre méthode HTTP pour l'instant). Remplace le `String` utilisé comme type de l'attribut `method()` de `@RequestMapping` au sprint 3.
- **Route / `RouteInfo`** : association entre une URL et un couple (classe controller, méthode action), construite dynamiquement au démarrage par scan des annotations.
- **`UrlMethod`** : classe clé de la map de routes, encapsulant le couple (URL, méthode HTTP typée `HttpMethod`). `equals()`/`hashCode()` surchargés : comparaison de l'URL sensible à la casse, comparaison de la méthode HTTP par égalité d'enum.
- **Action** : méthode d'une classe `@Controller`, annotée `@RequestMapping`, représentant une opération associée à une URL et une méthode HTTP. **Depuis le sprint 3 bis**, ces méthodes sont réellement invoquées (et non plus seulement détectées/affichées).
- **processRequest** : méthode unique appelée par `doGet` et `doPost`. Construit une clé `UrlMethod(path, méthode HTTP de la requête)` et la cherche dans la map des routes mise en cache par `init()` ; si trouvée, **instancie le controller et invoque réellement la méthode d'action** (« prototype », nouvelle instance à chaque appel), puis affiche le HTML de diagnostic ; si l'URL existe mais pas avec cette méthode HTTP, déclenche `HttpMethodNotSupportedException` (405) ; si l'URL est totalement inconnue, déclenche `UrlNotFoundException` (404) ; si l'invocation échoue, répond en HTTP 500 avec message.
- **`UrlNotFoundException`** : exception (`extends ServletException`) levée quand l'URL demandée ne correspond à aucune route connue, quelle que soit la méthode HTTP. Traitée en HTTP 404.
- **`HttpMethodNotSupportedException`** : exception (`extends ServletException`) levée quand l'URL demandée est connue mais pas avec la méthode HTTP utilisée. Traitée en HTTP 405.
- **`DuplicateRouteException`** *(nouveau, sprint 3 bis)* : exception (`extends ServletException`) levée lors de la construction des routes (désormais dans `RouteLoader`, appelé par le Listener) quand deux méthodes déclarent exactement la même URL et la même méthode HTTP. Comportement fail-fast : plus de tolérance/accumulation comme aux sprints 1 à 3.
- **`FrameworkStartupException`** *(nouveau, sprint 4)* : exception **non vérifiée** (`extends RuntimeException`), utilisée dans `ControllerScannerListener.contextInitialized()` pour encapsuler toute exception vérifiée levée pendant le scan (dont `DuplicateRouteException`), puisque la signature de `contextInitialized` ne permet aucun `throws`. Une instance non catchée fait échouer le déploiement du contexte entier dans Tomcat.
- **ServletContext** : objet partagé pour toute l'application web. **Depuis le sprint 4**, porte concrètement la map de routes sous l'attribut `"framework.routes"`, posée par le Listener au chargement et lue une seule fois par le Servlet dans son `init()`.

## 5. Journal des sprints

| Sprint | Objectif | Statut |
|---|---|---|
| Sprint 0 | Mettre en place le Front Controller, le Listener, un mapping URL -> controller via `mapping.xml`, et afficher dans le navigateur le nom de la classe controller associée à une URL demandée | Terminé (fusionné dans `main`) |
| Sprint 1 | Remplacer le mapping XML par une détection des controllers via annotations `@Controller` / `@RequestMapping` (niveau classe), scan dynamique de `WEB-INF/classes` au démarrage, routes construites dynamiquement, gestion du cas "plusieurs controllers pour la même URL" | Terminé (fusionné dans `main`) |
| Sprint 2 | Déplacer `@RequestMapping` de la classe vers la méthode, pour permettre plusieurs actions/URL par classe controller. Construire une map URL -> (classe, méthode). Ne pas encore invoquer les méthodes | Terminé (fusionné dans `main`) |
| Sprint 3 | Ajouter la méthode HTTP comme second critère de routage (`method()` sur `@RequestMapping`, en `String`, défaut `GET`). Introduire `UrlMethod`. Distinguer 404 / 405. Toujours pas d'invocation | Terminé (fusionné dans `main`) |
| Sprint 3 bis | Invocation réelle des actions via réflexion (cycle « prototype »), `HttpMethod` en enum, `Map<UrlMethod, RouteInfo>` (doublon exact devenu bloquant via `DuplicateRouteException`), gestion 500 sur échec d'invocation | **Terminé (fusionné dans `main`)** — nécessitait un nettoyage préalable des anciennes classes de test de doublons (`DuplicateAaaController`, `DuplicateBbbController`, `DuplicateCccController`, `testCccController`), devenues des doublons stricts bloquant le démarrage sous la nouvelle règle ; nettoyage effectué avant fusion |
| Sprint 4 | Déplacer le scan des controllers de `FrontServletController.init()` vers `ControllerScannerListener.contextInitialized()`, pour que la map de routes soit construite dès le chargement de l'application (T0) et non à la première requête. Partage via attribut `ServletContext` (`"framework.routes"`), `FrameworkStartupException` pour encapsuler les exceptions de démarrage | **En cours — régression identifiée, correction en cours avant fusion** (voir section 7) |

*(À mettre à jour à la fin de chaque sprint, avant de fusionner la Pull Request correspondante, pour que ce fichier reste fiable d'une session de travail à l'autre.)*

## 6. État technique à la fin du sprint 3 bis (référence stable, fusionnée dans `main`)

À garder en tête pour ne pas re-découvrir le code à chaque session — ceci décrit l'état de `main` **avant** le sprint 4, qui reste la référence de non-régression :

- `FrontServletController` construisait (à l'époque, dans son propre `init()`) la map `Map<UrlMethod, RouteInfo>`, puis dans `processRequest` : route trouvée → instancie le controller et invoque réellement l'action via réflexion (`method.invoke(instance)`), cycle « prototype » (nouvelle instance à chaque requête) ; URL connue mais méthode HTTP différente → `HttpMethodNotSupportedException` (405) ; URL inconnue → `UrlNotFoundException` (404) ; échec d'invocation → HTTP 500 avec message (`writeServerError`).
- `TestController` (`testapp.controller`) : `testGet()` (`/test`, GET) contient deux `System.out.println` (confirmation d'invocation + `this.hashCode()` pour valider le comportement « prototype ») ; `testPost()` (`/test`, POST) confirme l'invocation par un println dédié.
- `DuplicateTestController` : classe de test dédiée (`/test` en GET, doublon strict de `TestController.testGet()`), créée pour valider que `DuplicateRouteException` est bien levée au démarrage — à ne garder que le temps du test, sinon elle bloque en permanence le démarrage de l'application.
- Les anciennes classes de test de doublons des sprints 1 à 3 (`DuplicateAaaController`, `DuplicateBbbController`, `DuplicateCccController`, `testCccController`) ont été **retirées** de `test-app`, car elles étaient devenues des doublons stricts sous la nouvelle règle, bloquant systématiquement le démarrage.
- Le contenu des réponses HTML passe par un `escapeHtml()` interne avant affichage.
- Cet état a été vérifié par simulation (harnais de test par réflexion contre `init()`/`doGet`/`doPost`, sans Tomcat réel) : invocation réelle confirmée, `hashCode()` différent à chaque appel, 404/405/500 tous corrects, `DuplicateRouteException` levée avec le bon message pour un doublon isolé.

## 7. État en cours du sprint 4 — régression identifiée, correction en cours

### Ce qui a été vérifié comme correct (objectif principal du sprint atteint)
- `RouteLoader.loadRoutes(ServletContext)` : extrait la logique de scan (auparavant dans `FrontServletController.init()`), construit `Map<UrlMethod, RouteInfo>`, lève `DuplicateRouteException` en cas de doublon strict, logge le détail des routes détectées et le nombre total.
- `ControllerScannerListener.contextInitialized()` : appelle `RouteLoader.loadRoutes()`, pose le résultat sur `ServletContext` sous la clé `"framework.routes"`, encapsule toute exception dans `FrameworkStartupException` (`extends RuntimeException`) en cas d'échec.
- `ControllerScannerListener.contextDestroyed()` : retire l'attribut `"framework.routes"`.
- **Vérifié par simulation** (Listener puis Servlet appelés séparément, sans aucun appel d'URL entre les deux) : les logs `[FRAMEWORK] --- DÉBUT DU SCAN DES CONTROLEURS (T0) ---` puis `--- SCAN TERMINÉ : X ROUTES CHARGÉES ---` apparaissent bien **avant** toute requête HTTP — l'objectif du sprint (scan à T0, pas à la première requête) est donc atteint.
- `web.xml` inchangé, pas de `<load-on-startup>` ajouté.

### Régression identifiée (à corriger avant fusion)
Lors du refactor, `FrontServletController.processRequest()` a perdu l'invocation réelle de la méthode d'action acquise au sprint 3 bis (section 6 ci-dessus) : la route trouvée n'est plus qu'affichée en HTML, sans `method.invoke(...)`. Conséquences constatées par simulation :
- Plus aucun `System.out.println` de `TestController` ne s'affiche (l'action n'est jamais exécutée).
- Le comportement « prototype » n'est plus vérifiable (le controller n'est même plus instancié).
- Plus de gestion HTTP 500 sur échec d'invocation (`doGet`/`doPost` ne catchent plus que `UrlNotFoundException`/`HttpMethodNotSupportedException`).

Un prompt de correction ciblée (`prompt-correction-sprint4.md`) a été préparé : il fournit le code actuel de `FrontServletController.java` comme base, la logique d'invocation exacte à réintégrer (reprise du sprint 3 bis), et une section « Ne pas régresser » listant explicitement ce qui fonctionne déjà (`RouteLoader`, Listener, cache de la map dans `init()`) et ne doit pas être retouché.

**Ce fichier devra être mis à jour une fois la correction appliquée et re-vérifiée**, pour repasser le statut du sprint 4 à "Terminé" en section 5, et fusionner le contenu de cette section 7 dans une nouvelle section 6 consolidée (état de fin de sprint 4).

### Leçon méthodologique retenue pour les prompts de sprint suivants
Un prompt de sprint qui ne fournit que la description du changement (sans le code source réel du fichier à modifier) risque d'être traité comme une reconstruction du fichier plutôt qu'une modification ciblée, et de faire disparaître silencieusement des comportements acquis lors de sprints précédents non répétés dans le prompt. **À partir du sprint 5**, chaque prompt de démarrage doit :
1. inclure le code source actuel des fichiers principaux à modifier (pas seulement leur description dans ce fichier) ;
2. comporter une section explicite « Ne pas régresser » listant les comportements acquis hors scope du sprint courant.

## 8. Workflow Git / GitHub

1. Se placer sur `main` et synchroniser : `git checkout main` puis `git pull`.
2. Créer la branche du sprint : `git checkout -b sprintX` (X = numéro du sprint, ex. `sprint2`, `sprint3`, `sprint3bis`, `sprint4`).
3. Travailler par petits commits clairs et atomiques (un commit = une étape logique, pas un seul gros commit en fin de sprint).
4. Pousser la branche : `git push -u origin sprintX`.
5. Ouvrir une Pull Request sur GitHub, de `sprintX` vers `main`.
6. Relire son propre diff avant de fusionner : vérifier qu'aucun fichier compilé du dossier `build/` n'a été ajouté par erreur, que la description de la PR résume bien le sprint et reprend le plan de test manuel. **Comparer explicitement les fichiers modifiés avec leur version mergée du sprint précédent** (`git diff sprintPrécédent..sprintX -- chemin/du/fichier`) pour repérer toute régression silencieuse, en particulier sur les fichiers qui existaient déjà avant le sprint courant.
7. Fusionner la Pull Request (squash and merge recommandé, pour garder un historique propre sur `main` tout en gardant le détail des commits visible dans la PR fermée).
8. Revenir sur `main`, `git pull`, supprimer la branche locale et distante si elle n'est plus utile.
9. Mettre à jour le journal des sprints (section 5) et l'état technique (section 6/7) avec le statut "Terminé" et les nouveaux points à retenir.

## 9. Comment réutiliser ce fichier pour démarrer un nouveau sprint

Dans une nouvelle discussion, copier-coller l'intégralité de ce fichier, puis indiquer le numéro du sprint visé et ce qui est attendu pour ce sprint. Pour tout sprint qui modifie un fichier existant (et non un sprint purement additif), coller aussi le code source actuel du ou des fichiers concernés en plus de ce fichier, pour limiter le risque de régression silencieuse (voir section 7, leçon méthodologique).
