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
- **Workflow Git** : une branche par sprint (`sprint0`, `sprint1`, ..., `sprint3bis`, `sprint4`, `sprint5`, `sprint5bis`), créée depuis `main`, fusionnée via Pull Request sur GitHub. Projet en solo, donc auto-revue avant fusion.
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
- **Résolution de vue (`ModelAndView`)** *(depuis le sprint 5)* : une méthode d'action peut retourner soit `void` (mode diagnostic HTML existant, inchangé), soit un objet `framework.core.ModelAndView` (nom logique de vue + `Map<String, Object>` d'attributs). Si `ModelAndView` est retourné, `processRequest` transfère chaque attribut vers la requête (`request.setAttribute`) puis délègue l'affichage via `RequestDispatcher.forward()`. Le chemin réel de la JSP est obtenu par concaténation `viewPrefix + mv.getUrl() + viewSuffix` :
  - `viewPrefix`/`viewSuffix` sont lus comme `init-param` du Servlet (`framework.viewPrefix`/`framework.viewSuffix`) dans `FrontServletController.init(ServletConfig)` — responsabilité du Servlet, pas du Listener, puisque c'est une configuration d'affichage propre au Front Controller.
  - Valeurs par défaut codées en dur si absentes du `web.xml` : `/WEB-INF/views/` (slash initial ET final) et `.jsp`, avec un log explicite dans ce cas.
  - Convention de nommage : le nom de vue passé à `mv.setUrl(...)` ne porte **pas** de slash initial (ex. `emp/list`), pour une concaténation propre sans double slash.
  - `ServletException`/`IOException` levées par `forward(...)` ne sont pas catchées par le framework : elles remontent telles quelles, laissant Tomcat gérer l'affichage d'erreur par défaut (ex. JSP manquante).

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
- **processRequest** : méthode unique appelée par `doGet` et `doPost`. Construit une clé `UrlMethod(path, méthode HTTP de la requête)` et la cherche dans la map des routes mise en cache par `init()` ; si trouvée, **instancie le controller et invoque réellement la méthode d'action** (« prototype », nouvelle instance à chaque appel). **Depuis le sprint 5** : si la méthode retourne un `ModelAndView`, transfère les attributs vers la requête puis délègue via `RequestDispatcher.forward()` ; sinon (retour `void`), affiche le HTML de diagnostic comme avant. Si l'URL existe mais pas avec cette méthode HTTP, déclenche `HttpMethodNotSupportedException` (405) ; si l'URL est totalement inconnue, déclenche `UrlNotFoundException` (404) ; si l'invocation échoue, répond en HTTP 500 avec message.
- **`ModelAndView`** *(nouveau, sprint 5)* : classe du framework (`framework.core`) retournée (optionnellement) par une méthode d'action, encapsulant un nom logique de vue (`String url`, sans slash initial) et un modèle de données (`Map<String, Object>`, instancié directement pour éviter les `NullPointerException`). Méthodes : `setUrl`/`getUrl`, `setAttribute(String, Object)`, `getAttributes()`.
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
| Sprint 4 | Déplacer le scan des controllers de `FrontServletController.init()` vers `ControllerScannerListener.contextInitialized()`, pour que la map de routes soit construite dès le chargement de l'application (T0) et non à la première requête. Partage via attribut `ServletContext` (`"framework.routes"`), `FrameworkStartupException` pour encapsuler les exceptions de démarrage | **Terminé (fusionné dans `main`)** — une régression avait fait disparaître l'invocation réelle des actions pendant le refactor (voir section 6) ; corrigée via un prompt ciblé (`prompt-correction-sprint4.md`) et re-vérifiée intégralement par simulation avant fusion |
| Sprint 5 | Introduire `ModelAndView` (résolution de vue JSP via préfixe/suffixe configurables, transfert du modèle vers la requête, `RequestDispatcher.forward()`), tout en gardant le mode `void` existant (diagnostic HTML) pour les contrôleurs déjà en place | **Implémenté et vérifié intégralement par simulation** (voir section 6) — **statut de fusion dans `main` à confirmer par l'utilisateur** : un `git status` pris pendant la revue montrait `ModelAndView.java` encore non suivi (`git add` manquant) juste avant commit ; à vérifier que le commit réellement poussé inclut bien ce fichier |
| Sprint 5 bis | Démarrer un conteneur Spring (6.x, XML + `<context:component-scan>`) en parallèle du framework maison via `ContextLoaderListener`, sans lui déléguer le cycle de vie des contrôleurs (qui restent gérés par notre framework, mode « prototype »). Une méthode d'action peut désormais déclarer un paramètre `ApplicationContext`, injecté par réflexion (`WebApplicationContextUtils`), pour aller chercher des beans Spring (`@Repository`/`@Service`) via `getBean(...)`. Pas d'auto-injection (`@Autowired`) sur les champs du contrôleur : hors scope | Prompt de démarrage préparé (`prompt-sprint5bis.md`), implémentation à venir |

*(À mettre à jour à la fin de chaque sprint, avant de fusionner la Pull Request correspondante, pour que ce fichier reste fiable d'une session de travail à l'autre.)*

## 6. État technique à la fin du sprint 5 (implémenté et vérifié par simulation — fusion dans `main` à confirmer)

À garder en tête pour ne pas re-découvrir le code à chaque session — ceci décrit l'état du code livré pour le sprint 5, base de référence de non-régression pour le sprint 5 bis :

### Acquis du sprint 4 (toujours valables, non retouchés au sprint 5)
- **Scan à T0** : `RouteLoader.loadRoutes(ServletContext)` construit `Map<UrlMethod, RouteInfo>`, lève `DuplicateRouteException` en cas de doublon strict. Appelée depuis `ControllerScannerListener.contextInitialized()`, qui pose le résultat sur le `ServletContext` sous la clé `"framework.routes"` et encapsule toute exception vérifiée dans `FrameworkStartupException`. `contextDestroyed()` retire cet attribut.
- `FrontServletController.init(ServletConfig)` : récupère la map une seule fois depuis l'attribut du `ServletContext`, la met en cache.
- **Invocation réelle** : route trouvée → instancie le controller (constructeur par défaut) et invoque réellement l'action via réflexion, cycle « prototype » (nouvelle instance à chaque requête).
- `TestController`, `AaaController`, `BbbController`, `CccController`, `DeptController` : contrôleurs `void` de non-régression, toujours fonctionnels tels quels.
- 404 (`UrlNotFoundException`), 405 (`HttpMethodNotSupportedException`), 500 sur échec d'invocation (`InvocationTargetException`/`ReflectiveOperationException`) : inchangés.

### Nouveautés du sprint 5
- **`ModelAndView`** (`framework.core`) : `String url` + `Map<String, Object> data` (instanciée directement). `setUrl`/`getUrl`, `setAttribute`, `getAttributes`.
- **`FrontServletController.init()`** lit désormais aussi `framework.viewPrefix`/`framework.viewSuffix` (`init-param` du Servlet), avec valeurs par défaut `/WEB-INF/views/` et `.jsp` si absents, et log explicite dans ce cas.
- **`processRequest`** : après invocation, si le résultat est un `ModelAndView`, boucle sur `getAttributes()` pour poser chaque entrée sur la requête (`request.setAttribute`), puis délègue via `request.getRequestDispatcher(viewPrefix + mv.getUrl() + viewSuffix).forward(req, resp)`. Sinon (retour `void`/`null`), comportement HTML de diagnostic inchangé. Les exceptions de `forward()` ne sont pas catchées par le framework.
- **`EmpController`** (`testapp.controller`) : méthode `list()` mappée sur `/emp/list` (`GET`), retourne un `ModelAndView` avec des données `Employe` codées en dur.
- **`Employe`** (`testapp.model`) : POJO simple (`nom`, `prenom`), utilisé pour prouver que le transfert de modèle gère des objets complexes, pas seulement des chaînes.
- **`test-app/WEB-INF/views/emp/list.jsp`** : JSP de test avec scriptlets classiques (pas de JSTL), affiche `message` et boucle sur `employees`.

### Vérification effectuée
Vérifié intégralement par simulation (harnais de test par réflexion contre `contextInitialized()`/`init()`/`doGet`, sans Tomcat réel, avec un faux `RequestDispatcher` capturant le chemin de `forward()` et les attributs posés avant l'appel) :
- `GET /emp/list` → `forward()` appelé exactement vers `/WEB-INF/views/emp/list.jsp`, attributs `employees`/`message` bien présents sur la requête avant le forward.
- Log de valeur par défaut du préfixe/suffixe déclenché uniquement quand les `init-param` sont absents du `web.xml`.
- Non-régression totale confirmée sur `GET`/`POST /test` (mode `void`, invocation réelle, comportement « prototype »).

### Point de vigilance non résolu (à l'utilisateur de trancher)
- Au moment de la revue, `git status` sur le dépôt local montrait `src/framework/core/ModelAndView.java` comme fichier **non suivi** (`??`), alors que `FrontServletController.java` en dépend directement. Si le commit poussé sur GitHub a été fait sans `git add` préalable sur ce fichier, la compilation cassera pour quiconque récupère la branche/PR. À vérifier avant de considérer le sprint 5 comme réellement clos.
- L'état réel de `main` sur GitHub (sprints 2 à 5 effectivement fusionnés ou non) n'a pas pu être confirmé par l'assistant (dépôt privé, pas d'accès direct) ; l'utilisateur a indiqué vouloir vérifier cela plus tard (voir section 7, note Git).

## 7. Leçons méthodologiques retenues

Une régression s'est glissée au sprint 4 : `FrontServletController.processRequest()` avait perdu l'invocation réelle des actions (acquise au sprint 3 bis) pendant le déplacement du scan vers le Listener, probablement parce que le prompt de sprint décrivait le changement attendu sans fournir le code source réel du fichier à modifier — la session a reconstruit le fichier depuis la description plutôt que de le modifier ciblé, et un comportement non répété dans le prompt a disparu silencieusement. Corrigée via un prompt dédié (`prompt-correction-sprint4.md`) et re-vérifiée par simulation avant fusion.

Pour éviter que ça se reproduise, chaque prompt de démarrage de sprint doit désormais :
1. inclure le code source actuel des fichiers principaux à modifier (pas seulement leur description dans ce fichier) ;
2. comporter une section explicite « Ne pas régresser » listant les comportements acquis hors scope du sprint courant ;
3. s'appuyer, quand c'est possible, sur un harnais de test réutilisable (simulateur par réflexion contre `init()`/`contextInitialized()`/`doGet`/`doPost`, sans Tomcat réel) conservé et enrichi d'un sprint à l'autre plutôt que reconstruit à chaque revue, pour détecter ce type de régression immédiatement, avant même la revue manuelle ;
4. être suivi, avant fusion de la PR, d'un `git diff` ciblé sur les fichiers modifiés par rapport à leur version mergée du sprint précédent (déjà recommandé en section 8, point 6) — c'est ce diff qui aurait rendu visible, ligne par ligne, la disparition de `method.invoke(...)` dans `FrontServletController.processRequest()` au sprint 4.

Le prompt du sprint 5 (`prompt-sprint5.md`) et celui du sprint 5 bis (`prompt-sprint5bis.md`) appliquent déjà les points 1 et 2 ci-dessus.

### Leçon Git additionnelle (sprint 4/5)
Deux points de vigilance identifiés en marge des revues de code, indépendants du code lui-même :
1. **Toujours vérifier `git status` avant de committer**, en particulier pour les nouveaux fichiers non suivis (`??`). Un fichier oublié (`git add` manquant) casse la compilation pour quiconque récupère la branche/PR, même si tout fonctionne en local. Constaté sur `ModelAndView.java` au sprint 5 (voir section 6).
2. **Squash-and-merge et vérification d'ancêtres Git** : avec le workflow squash-and-merge (choisi pour ce projet), une branche de sprint fusionnée n'apparaît **jamais** comme ancêtre Git du commit résultant sur `main` (squash crée un nouveau commit indépendant). Un test du type `git merge-base --is-ancestor sprintX sprintY` ne prouve donc rien sur le fait qu'une PR ait été fusionnée ou non — ce n'est pas un signe d'erreur en soi. Pour vérifier l'état réel de fusion, la seule méthode fiable est de comparer le **contenu** des fichiers (`git diff`) après un `git fetch origin main` récent, pas la généalogie des commits. Par ailleurs, une branche `origin/main` locale non rafraîchie depuis longtemps (`git fetch`/`git pull` non fait) peut donner une fausse impression que des sprints n'ont pas été mergés alors qu'ils le sont bien sur GitHub — toujours vérifier la date de dernière synchronisation avant de tirer une conclusion sur l'état de `main`.

## 8. Workflow Git / GitHub

1. Se placer sur `main` et synchroniser : `git checkout main` puis `git pull`. **Faire un `git fetch origin main` frais avant toute vérification de l'état de `main`** (une branche `origin/main` locale non rafraîchie depuis longtemps donne une image fausse de ce qui est réellement fusionné sur GitHub, cf. section 7).
2. Créer la branche du sprint : `git checkout -b sprintX` (X = numéro du sprint, ex. `sprint2`, `sprint3`, `sprint3bis`, `sprint4`, `sprint5`, `sprint5bis`), **toujours depuis un `main` fraîchement pullé, jamais depuis une autre branche de sprint**.
3. Travailler par petits commits clairs et atomiques (un commit = une étape logique, pas un seul gros commit en fin de sprint).
4. Pousser la branche : `git push -u origin sprintX`.
5. Ouvrir une Pull Request sur GitHub, de `sprintX` vers `main`.
6. Relire son propre diff avant de fusionner : vérifier qu'aucun fichier compilé du dossier `build/` n'a été ajouté par erreur, que la description de la PR résume bien le sprint et reprend le plan de test manuel. **Comparer explicitement les fichiers modifiés avec leur version mergée du sprint précédent** (`git diff sprintPrécédent..sprintX -- chemin/du/fichier`) pour repérer toute régression silencieuse, en particulier sur les fichiers qui existaient déjà avant le sprint courant.
7. Fusionner la Pull Request (squash and merge recommandé, pour garder un historique propre sur `main` tout en gardant le détail des commits visible dans la PR fermée).
8. Revenir sur `main`, `git pull`, supprimer la branche locale et distante si elle n'est plus utile.
9. Mettre à jour le journal des sprints (section 5) et l'état technique (section 6/7) avec le statut "Terminé" et les nouveaux points à retenir.

## 9. Comment réutiliser ce fichier pour démarrer un nouveau sprint

Dans une nouvelle discussion, copier-coller l'intégralité de ce fichier, puis indiquer le numéro du sprint visé et ce qui est attendu pour ce sprint. Pour tout sprint qui modifie un fichier existant (et non un sprint purement additif), coller aussi le code source actuel du ou des fichiers concernés en plus de ce fichier, pour limiter le risque de régression silencieuse (voir section 7, leçon méthodologique).