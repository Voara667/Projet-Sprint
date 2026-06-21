# Projet-Sprint - Framework Web Front Controller

Petit framework web Java fait maison. Objectif : intercepter toutes les requetes via un Front Controller, resoudre une URL vers une classe controleur via un fichier `mapping.xml`, et deleguer le traitement.

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