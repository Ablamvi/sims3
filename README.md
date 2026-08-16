# Sims 3D Demo — marche + chaise (APK 32-bit)

## Nouveautés

- Personnage **plus poli** (style CAS / Sims 3)
- **Chaise** (bois + coussin)
- **Animations automatiques** :
  1. Idle
  2. Marche vers la chaise (jambes / bras)
  3. S’assoit
  4. Se relève et repart (boucle)

## Contrôles

- **Glisser** = orbit caméra
- Le Sim **joue tout seul** (pas besoin de toucher pour marcher)

## Build

```bash
chmod +x gradlew
./gradlew assembleDebug
```

Ou GitHub Actions → artefact `Sims3-3D-Demo-32bit`.

## Technique

- Kotlin + OpenGL ES 2.0
- ABI 32-bit : `armeabi-v7a`, `x86`
- minSdk 24 (Android 10 OK)

Ce n’est **pas** le vrai Sims 3 EA — c’est ta base de jeu life-sim 3D.
