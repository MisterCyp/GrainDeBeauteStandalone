# Grain de Beauté — Suivi de grains de beauté (local)

Ce projet est né de la volonté de proposer un outil de suivi dermatologique qui garantit la vie privée de l'utilisateur. Pour cette raison, l'application est entièrement locale : aucune donnée ne quitte le téléphone.

L'objectif est de permettre de prendre des photos de grains de beauté et de suivre leur évolution dans le temps, en utilisant une application Android et un petit accessoire à imprimer en 3D.

https://github.com/user-attachments/assets/47d4a93d-1d58-4598-9735-21f1c8d88895

## Fonctionnement

L'application s'appuie sur deux éléments :
1.  **Un embout en plastique (impression 3D) :** Il se place sur le téléphone pour maintenir une distance fixe entre l'appareil photo et la peau. Cela permet d'avoir une échelle de mesure constante.
2.  **Une analyse locale :** L'application calcule la surface (mm²) et la taille (mm) du grain de beauté à partir de la photo.

## Caractéristiques

*   **Données locales :** Les photos et les analyses restent sur le téléphone. Il n'y a pas de serveur et aucune donnée n'est envoyée sur internet.
*   **Aide à la mesure :** L'application utilise Python et OpenCV pour détourer le grain de beauté et extraire ses dimensions.
*   **Historique :** Les mesures sont enregistrées pour pouvoir observer les changements sur un graphique.

## Utilisation

1.  Imprimer l'accessoire 3D (fichiers dans le dossier `hardware/`).
2.  Prendre une photo en posant l'accessoire sur la peau.
3.  L'application enregistre la photo et affiche les mesures de taille et de surface.

## Développement

Le code a été écrit avec l'aide d'outils d'intelligence artificielle (Claude Code, Gemini), ce qui a permis de construire l'application rapidement en se concentrant sur les résultats et la logique métier. 

---

*Note : Cet outil ne donne pas de diagnostic médical. Il sert uniquement à prendre des mesures pour faciliter le suivi avec un dermatologue.*
