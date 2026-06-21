# Projet-Sprint - Framework Web Front Controller

Petit framework web Java fait maison. Objectif : intercepter toutes les requetes via un Front Controller et resoudre une URL vers une classe controleur via les annotations `@Controller` et `@RequestMapping`, sans configuration XML.

Compilation et deploiement

1. Assurez-vous que la variable d'environnement `CATALINA_HOME` pointe vers une installation de Tomcat 10+.
2. Placez le dossier `test-app` comme dossier frere du depot (par exemple `../test-app`).
3. Lancez :

```bash
./deploy.sh
```

Cela compile le framework, genere `framework.jar` et le copie dans `test-app/WEB-INF/lib/`.

Contexte et sprints

Voir `PROJECT_INSTRUCTIONS.md` pour le contexte complet, le workflow Git et le suivi des sprints.
# Projet-Sprint



On va passer au annotation, le but est que l'on a plusieurs class et ils ont des annotation comme mg.itu.annotation.Controller par exemple et le but est que dans la fonction processRequest dans projet-sprint/src/framework/core/FrontServletController.java on a un void(init)  et ce void(init) quand il appelle un controller il dit qui va en premier et dans ce void(inti) on ajoute une attribut list pour ajouter la liste des controller ayant l'annotation et le void(init), disant, lis les classPath présent dans la liste et après ProcessRequest crée la liste pour l'afficher aussi dans le navigateur. Au démarrage alors ça detecte quelle class a l'annotation donc plus besoin de faire la config dans le .xml comme dans sprint0, on va faire la config via les annotations. Donc dans le void(init) on va faire la detection des class qui ont l'annotation et après dans le processRequest on va faire le mapping entre l'url et la class controller qui a l'annotation. Donc plus besoin de faire la config dans le .xml comme dans sprint0, on va faire la config via les annotations.