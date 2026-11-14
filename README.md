# WebDAV Share - Application Android

## Description
WebDAV Share est une application Android qui permet de partager facilement l'ensemble des fichiers et dossiers de votre téléphone sur un réseau local via le protocole WebDAV. Cela permet d'accéder à vos fichiers depuis n'importe quel ordinateur Windows (ou autre système compatible WebDAV) sans câble USB.

## Fonctionnalités
- Partage de l'ensemble du stockage du téléphone Android via WebDAV
- Configuration simple et intuitive
- Accès facile depuis Windows via le navigateur de fichiers
- Fonctionne en arrière-plan
- Affichage de l'adresse IP et du port pour une connexion facile

## Comment l'utiliser
1. Installez l'application sur votre téléphone Android
2. Accordez les permissions d'accès au stockage
3. Démarrez le serveur WebDAV
4. Sur votre PC Windows, accédez à l'URL WebDAV affichée dans l'application

## Comment se connecter depuis Windows
1. Ouvrez l'explorateur de fichiers Windows
2. Cliquez-droit sur "Ce PC"
3. Sélectionnez "Ajouter un emplacement réseau"
4. Saisissez l'URL WebDAV affichée dans l'application (ex: http://192.168.1.100:8080)

## Permissions requises
- Accès à Internet et réseau
- Accès en lecture/écriture au stockage externe (MANAGE_EXTERNAL_STORAGE pour Android 11+)
- Service en premier plan

## Développement
Cette application est développée en Kotlin et utilise les technologies suivantes:
- androidx.* pour les composants d'interface
- NanoHTTPD pour le serveur HTTP léger
- API File standard pour l'accès au système de fichiers Android

## Licence
Cette application est distribuée sous licence open-source.
