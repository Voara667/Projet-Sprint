# Mini-framework MVC en Java

Projet de cours : un petit framework web qui ressemble à Spring MVC, écrit à la main avec les servlets Jakarta. Une seule servlet (le Front Controller) reçoit toutes les requêtes, trouve la méthode du contrôleur qui correspond à l'URL, l'appelle, puis affiche une page JSP ou renvoie du JSON.

Ce n'est pas fait pour la production, c'est surtout pour comprendre comment ça marche à l'intérieur.

## Ce qu'il faut avoir

- un JDK 17 ou plus
- Tomcat 10 ou plus (on utilise `jakarta.servlet`, pas `javax.servlet`)
- les 7 jars Spring 6.2.18 dans `~/.m2/repository/org/springframework/` : `spring-core`, `spring-context`, `spring-beans`, `spring-web`, `spring-expression`, `spring-aop`, `spring-jcl`
- la variable `CATALINA_HOME` qui pointe vers Tomcat, par exemple :

```bash
export CATALINA_HOME=/home/voara/Documents/tomcat/tomcat
```

## Lancer le projet

Il faut un projet web à côté de ce dépôt (par exemple `../test-app`), avec un dossier `WEB-INF` qui contient `classes`, `lib` et `web.xml`. Ensuite, depuis ce dossier :

```bash
./deploy.sh ../test-app
```

Sans argument, le script prend `../test-app`. Il compile le framework, fabrique `framework.jar`, le copie dans `WEB-INF/lib` avec les jars Spring, compile les contrôleurs du projet cible (avec `javac -parameters`, on en reparle plus bas), déploie sur Tomcat et le redémarre.

Le `web.xml` du projet cible doit déclarer ceci :

```xml
<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>/WEB-INF/applicationContext.xml</param-value>
</context-param>
<context-param>
    <param-name>framework.authSessionKey</param-name>
    <param-value>user</param-value>
</context-param>

<listener>
    <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>
<listener>
    <listener-class>framework.core.ControllerScannerListener</listener-class>
</listener>

<servlet>
    <servlet-name>FrontServletController</servlet-name>
    <servlet-class>framework.core.FrontServletController</servlet-class>
    <multipart-config>
        <max-file-size>2097152</max-file-size>
        <max-request-size>10485760</max-request-size>
        <file-size-threshold>0</file-size-threshold>
    </multipart-config>
</servlet>
<servlet-mapping>
    <servlet-name>FrontServletController</servlet-name>
    <url-pattern>/</url-pattern>
</servlet-mapping>
```

Attention au mapping : il faut `/` et pas `/*`. Avec `/*`, le forward vers une JSP repasse par notre servlet et on obtient une erreur 404 au lieu de la page. Le premier listener (Spring) ne sert que si une action demande un `ApplicationContext`.

## Écrire un contrôleur

Une classe avec `@Controller`, et des méthodes avec `@RequestMapping` (l'URL commence par `/`, la méthode HTTP est `GET` par défaut) :

```java
@Controller
public class BonjourController {

    @RequestMapping("/bonjour")
    public ModelAndView bonjour(String nom) {
        ModelAndView mv = new ModelAndView();
        mv.setUrl("bonjour");          // pas de / au début
        mv.setAttribute("nom", nom);
        return mv;
    }
}
```

Le framework envoie les attributs vers la requête puis fait un forward vers `/WEB-INF/views/bonjour.jsp`. Le préfixe et le suffixe peuvent se changer avec les paramètres d'init de la servlet `framework.viewPrefix` et `framework.viewSuffix`. Si la méthode retourne `void`, on affiche juste une petite page qui dit quelle route a été trouvée.

### Les paramètres d'une action

Pour chaque paramètre de la méthode, le framework regarde son type :

- `ApplicationContext`, `HttpServletRequest` et `HttpServletResponse` sont donnés tels quels. Pour `ApplicationContext`, on obtient `null` si Spring n'est pas démarré, et c'est à l'action d'appeler `ctx.getBean(...)` elle-même.
- Un type simple (`String`, `int`, `long`, `double`, `boolean`, `char`, les wrappers comme `Integer`, `BigDecimal`, `BigInteger`, `LocalDate`) reçoit la valeur du paramètre de la requête qui a le même nom. S'il n'y en a pas, on a `null` pour un objet et `0` / `false` pour un primitif. Un champ de formulaire laissé vide compte comme absent (sauf pour un `String`, qui reçoit `""`). Si la conversion ne marche pas (par exemple `age=abc`), la réponse est une erreur 400.
- Un objet (comme `Employe`) est créé puis rempli : chaque champ de la classe reçoit le paramètre qui a le même nom que lui. Pour `save(String titre, Employe emp)` avec un formulaire qui envoie `titre`, `nom` et `prenom`, `titre` vient du formulaire et `emp` arrive avec son `nom` et son `prenom`.
- Tout le reste (`List`, `Map`, tableaux...) reçoit `null`.

Pour les objets, il y a quelques règles à connaître :

- la classe doit avoir un constructeur sans argument, sinon on a une erreur 500 ;
- les champs sont remplis avec le setter s'il existe, sinon directement ;
- les champs `static`, `final`, les listes, les tableaux et les objets à l'intérieur de l'objet sont ignorés ;
- l'objet n'est jamais `null`, même si le formulaire est vide : il arrive avec ses valeurs par défaut ;
- les noms sont à plat : c'est `nom` et pas `emp.nom`. Si on a deux objets avec un champ `nom`, les deux reçoivent la même valeur.

Pour que les noms des paramètres soient lisibles à l'exécution, les contrôleurs doivent être compilés avec `javac -parameters`. `deploy.sh` le fait pour le projet cible. Sans ça, une action qui a un paramètre simple répond une erreur 500 qui le rappelle.

### Répondre en JSON

On ajoute `@WebApi` sur la méthode, ou sur la classe pour que toutes ses actions répondent en JSON :

```java
@WebApi
@RequestMapping("/api/bonjour")
public Map<String, Object> bonjour() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("message", "bonjour");
    return m;
}
```

Il n'y a pas de forward dans ce cas. Si l'action retourne un `String`, il est écrit tel quel (on suppose que c'est déjà du JSON). Pour les autres objets, c'est notre convertisseur JSON qui s'en occupe, il n'y a pas de librairie externe. Un `ModelAndView` donne le JSON de ses attributs, un `void` donne un 204, et `null` donne le JSON `null`.

### Les erreurs

- 404 si l'URL n'existe pas, 405 si elle existe mais pas avec cette méthode HTTP (pages HTML qui listent ce qui existe)
- 400 si une valeur ne se convertit pas
- 500 si l'action plante ou si un objet ne peut pas être créé
- pour les routes `@WebApi`, le 400 et le 500 sont en JSON : `{"status":400,"error":"..."}`
- si deux actions ont la même URL avec la même méthode HTTP, l'application ne démarre pas

## Ce qui n'est pas fait

- les contrôleurs sont recréés à chaque requête, il n'y a pas de singleton
- pas de binding pour les listes, les tableaux ni les objets dans les objets
- seulement `GET` et `POST`
- le scan ne lit que `WEB-INF/classes`, pas les jars de `WEB-INF/lib`
- avec le mapping `/`, notre servlet remplace celle de Tomcat qui sert les fichiers statiques, donc les images ou le CSS ne seraient pas servis
- pas de validation des objets, et tout champ simple peut être rempli par la requête, même un champ sensible
- pas de gestion des rôles et des autorisations fines ; c'est prévu au sprint suivant
- l'authentification vérifie seulement qu'une valeur existe dans la session ; le framework ne vérifie ni mot de passe ni identité
- le changement d'id avec `changeSessionId` n'est pas géré

## La session

Une action peut recevoir `framework.core.Session`. Le framework charge les
attributs de la session HTTP avant l'action, puis recopie les changements si
l'action se termine sans erreur :

```java
@RequestMapping("/compteur")
public ModelAndView compteur(Session session) {
    Integer n = session.get("compteur", Integer.class);
    session.set("compteur", n == null ? 1 : n + 1);
    ModelAndView mv = new ModelAndView();
    mv.setUrl("compteur");
    mv.setAttribute("compteur", n == null ? 1 : n + 1);
    return mv;
}
```

`Session` fournit `getId`, `get`, `set`, `remove`, `contains`, `clear`,
`getData` et `invalidate`. `set` renvoie la session pour chaîner les appels ;
mettre une valeur à `null` supprime la clé. `getData()` donne accès directement
à la map. Après `invalidate()`, `set` lève une erreur et la session HTTP est
invalidée à la fin de l'action.

Une JSP peut relire les valeurs recopiées avec
`session.getAttribute("compteur")`. Les actions ne reçoivent pas directement
`HttpSession` : ce paramètre est refusé avec une erreur 500 ; on utilise
`framework.core.Session` à la place. Si deux requêtes du même utilisateur
s'exécutent en même temps, il n'y a pas de garantie d'atomicité et la dernière
écriture peut gagner. Les valeurs devraient être sérialisables si Tomcat doit
persister les sessions.

## Réserver une action aux utilisateurs connectés

On ajoute `@Authenticated` sur une méthode pour protéger une action, ou sur une
classe pour protéger toutes ses actions. Il faut configurer le nom de la clé
utilisée pour reconnaître une connexion dans le `web.xml` :

```xml
<context-param>
    <param-name>framework.authSessionKey</param-name>
    <param-value>user</param-value>
</context-param>
```

Ici, on considère qu'une personne est connectée si la session contient une
valeur non nulle sous la clé `user`. On peut remplir cette clé depuis une action
qui reçoit `Session` :

```java
import framework.core.ModelAndView;
import framework.core.Session;
import framework.core.annotation.Authenticated;
import framework.core.annotation.Controller;
import framework.core.annotation.RequestMapping;

@Controller
public class AuthController {

    @RequestMapping("/login")
    public ModelAndView login(Session session, String nom) {
        session.set("user", nom);
        ModelAndView mv = new ModelAndView();
        mv.setUrl("profil");
        mv.setAttribute("user", nom);
        return mv;
    }

    @Authenticated
    @RequestMapping("/profil")
    public ModelAndView profil(Session session) {
        ModelAndView mv = new ModelAndView();
        mv.setUrl("profil");
        mv.setAttribute("user", session.get("user"));
        return mv;
    }
}
```

On déconnecte la personne avec `session.remove("user")` ou
`session.invalidate()`. Une action protégée répond 401 sans redirection :
`@WebApi` renvoie une erreur JSON, les autres actions une page HTML. Si une
route protégée existe et que `framework.authSessionKey` est absent ou vide,
l'application échoue au démarrage. Le framework vérifie seulement que la clé
est présente ; il ne vérifie ni mot de passe ni identité. La gestion des rôles
et des autorisations est prévue au sprint suivant.

## Envoyer des fichiers

Pour envoyer un fichier, le formulaire doit utiliser `enctype="multipart/form-data"`.
Il faut aussi ajouter, à la fin de l'élément `<servlet>` de `web.xml`, un bloc
`<multipart-config>` :

```xml
<multipart-config>
    <max-file-size>2097152</max-file-size>
    <max-request-size>10485760</max-request-size>
    <file-size-threshold>0</file-size-threshold>
</multipart-config>
```

Sans ce bloc, une requête multipart répond 500 et demande d'ajouter la
configuration. Les limites sont celles du conteneur, pas des constantes du
framework.

On peut recevoir un fichier comme champ d'un objet, comme paramètre direct, ou
dans une map. Par exemple, ce contrôleur compile :

```java
@Controller
@WebApi
public class UploadController {
    @RequestMapping(value = "/upload", method = HttpMethod.POST)
    public Map<String, Object> upload(UploadedFile photo, String titre) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("titre", titre);
        result.put("nom", photo == null ? null : photo.getFileName());
        return result;
    }
}
```

Pour une map, on utilise `Map<String, UploadedFile>`. Pour un objet, un champ
`UploadedFile` ou `byte[]` porte le même nom que l'input file. `UploadedFile`
contient le nom du champ, le nom d'origine nettoyé, l'extension en minuscules,
le type MIME déclaré, la taille et les octets. `getBytes()` renvoie le tableau
en mémoire tel quel.

Un fichier trop gros répond 413. Le framework ne stocke rien sur le disque et
ne vérifie pas l'extension ou le type MIME : le nom et le type déclarés ne sont
pas fiables. Un seul fichier est retenu par nom de champ, et le fichier est
entièrement lu en mémoire. C'est au contrôleur de décider quoi en faire.
