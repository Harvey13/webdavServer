# SftpServer (application Android indépendante)

Cette application Gradle est autonome et ne modifie pas l'application WebDAV existante du dépôt.

## Ouvrir et compiler

Depuis la racine du dépôt, compiler avec `./gradlew -p SftpServer assembleDebug` (Windows : `gradlew.bat -p SftpServer assembleDebug`). Il est également possible d’ouvrir le dossier `SftpServer` dans Android Studio, laisser Gradle synchroniser les dépendances, puis lancer la configuration `app` sur un téléphone Android 11 ou plus récent.

APK debug : `SftpServer/app/build/outputs/apk/debug/app-debug.apk`.

## Démarrage

1. Installer et ouvrir SftpServer.
2. Autoriser « Accès à tous les fichiers » dans les paramètres Android.
3. Appuyer sur « Démarrer le serveur SFTP ».
4. Garder le téléphone et le PC sur le même réseau local.
5. Dans FoldersCopy, utiliser l'adresse IP affichée, port `2222`, utilisateur `backup` et le mot de passe affiché.
6. Le stockage partagé du téléphone est présenté sous `/` (par exemple `/DCIM/Camera`).

Le mot de passe est généré aléatoirement à la première activation et conservé dans les préférences privées de l'application. La clé d'hôte SSH est conservée dans le stockage privé de l'application afin de rester stable entre les démarrages.

## Restrictions de sécurité

- SFTP est limité au stockage partagé Android.
- Les chemins sortant de la racine et les liens symboliques sont refusés.
- Les opérations d'écriture, création, renommage, copie et suppression sont refusées par le serveur.
- Le serveur écoute sur le réseau local, port 2222, avec authentification par mot de passe.
- Ne pas exposer ce port sur Internet.

## Notes de validation

Le code source est ajouté dans une branche de fonctionnalité. Il doit encore être compilé et testé sur un appareil Android réel avant d'être considéré comme prêt à installer. Les permissions de stockage et les restrictions de service au premier plan varient selon la version Android et le constructeur.
