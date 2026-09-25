# DeACoudre

Mini-jeu **Dé à Coudre** multi-arènes pour serveurs **Paper 26.3**, en anglais et en français.

## Gameplay

* Chaque joueur saute à tour de rôle depuis une plateforme dans un bassin d'eau.
* Chaque saut réussi remplace l'eau de la case par **son bloc** (choisi dans le lobby).
* Rater son saut (bloc déjà posé, bord, sol, hors zone, temps écoulé) **fait perdre une vie** ; à 0 vie, le
  joueur est éliminé et devient spectateur.
* Tomber dans un **trou d'eau 1×1 complètement entouré** (un « Dé à Coudre ») rapporte **une vie**.
* Le dernier joueur encore en vie gagne.

## Installation

| Élément   | Version                                  |
|-----------|------------------------------------------|
| Minecraft | Java Edition 26.3                        |
| Serveur   | Paper 26.3                               |
| Java      | 25 (minimum requis par Paper 26.3)       |

1. Compiler le plugin (voir [Compilation](#compilation)) ou récupérer `build/libs/DeACoudre-1.0.0.jar`.
2. Copier le JAR dans le dossier `plugins/` du serveur.
3. Démarrer le serveur : `plugins/DeACoudre/` est créé avec `config.yml`, `messages.yml`, `lang/` et `arenas/`.

Aucune dépendance externe : seule l'API Paper est utilisée (pas de NMS).

## Langues

| Code | Fichier fourni                 |
|------|--------------------------------|
| `en` | `lang/messages_en.yml` (défaut) |
| `fr` | `lang/messages_fr.yml`          |

* `messages.yml` est le fichier utilisé par le serveur. Il est créé à partir de la langue choisie par
  `language` dans `config.yml` ; les clés absentes de `messages.yml` (après une mise à jour du plugin) prennent
  la valeur de cette langue.
* Le dossier `lang/` est régénéré à chaque démarrage : c'est une référence, modifiez `messages.yml`.
* Changer de langue : mettre `language: fr`, supprimer `messages.yml`, puis `/dac reload` (ou copier
  `lang/messages_fr.yml` sur `messages.yml`).
* Les noms de blocs viennent de la section `colors` ; un bloc absent de cette section utilise le nom Minecraft,
  traduit automatiquement dans la langue du client de chaque joueur.

## Commandes

Commande principale `/dac`, alias `/deacoudre`. L'autocomplétion propose les sous-commandes autorisées
et les arènes (`/dac join <TAB>` ne propose que les arènes que l'on peut rejoindre).

| Commande                                | Permission        | Description                                                     |
|-----------------------------------------|-------------------|-----------------------------------------------------------------|
| `/dac help`                             | `deacoudre.play`  | Aide                                                            |
| `/dac join [arène]`                     | `deacoudre.play`  | Rejoint l'arène, ou sans argument l'arène disponible la plus avancée |
| `/dac leave`                            | `deacoudre.play`  | Quitte la partie (compte comme une élimination pendant le jeu)  |
| `/dac block`                            | `deacoudre.play`  | Ouvre le sélecteur de bloc (dans le lobby)                      |
| `/dac list`                             | `deacoudre.play`  | Liste des arènes : `arena1 - 4/12 - En attente`                 |
| `/dac create <arène>`                   | `deacoudre.admin` | Crée une arène (dans le monde de l'exécutant)                   |
| `/dac delete <arène>`                   | `deacoudre.admin` | Arrête la partie éventuelle et supprime l'arène                 |
| `/dac setlobby <arène>`                 | `deacoudre.admin` | Lobby = position actuelle                                       |
| `/dac setjump <arène>`                  | `deacoudre.admin` | Plateforme de saut = position actuelle                          |
| `/dac setspectator <arène>`             | `deacoudre.admin` | Point d'attente / spectateurs = position actuelle               |
| `/dac pos1 <arène>`                     | `deacoudre.admin` | Premier coin du bassin = bloc où se trouvent tes pieds          |
| `/dac pos2 <arène>`                     | `deacoudre.admin` | Second coin du bassin                                           |
| `/dac setplayers <arène> <min> <max>`   | `deacoudre.admin` | Surcharge min/max pour cette arène (`reset` = valeurs globales) |
| `/dac enable <arène>`                   | `deacoudre.admin` | Active l'arène si la configuration est valide, sinon liste ce qui manque |
| `/dac disable <arène>`                  | `deacoudre.admin` | Désactive l'arène (arrête et restaure la partie en cours)       |
| `/dac info <arène>`                     | `deacoudre.admin` | État, monde, joueurs, positions, bassin, partie en cours        |
| `/dac stop <arène>`                     | `deacoudre.admin` | Arrête proprement la partie : tâches, joueurs, bassin restaurés |
| `/dac forcestart <arène>`               | `deacoudre.admin` | Lance la partie dans 3 s, même sous le minimum (pratique pour tester seul) |
| `/dac reload`                           | `deacoudre.admin` | Recharge `config.yml`, `messages.yml` et les arènes non utilisées |

## Permissions

| Permission        | Défaut     | Description                                            |
|-------------------|------------|--------------------------------------------------------|
| `deacoudre.play`  | tout le monde | join, leave, block, list, help                      |
| `deacoudre.admin` | opérateurs | Toutes les commandes d'administration (inclut `deacoudre.play`) |

## Création d'une arène

Construire un bassin rempli d'eau (plusieurs blocs de profondeur possibles), une plateforme en hauteur,
une zone d'attente et un lobby, **dans le même monde**. Puis :

```text
/dac create arena1

Se placer dans le lobby
/dac setlobby arena1

Se placer sur la plateforme
/dac setjump arena1

Se placer à l'endroit spectateur (zone d'attente pendant les tours des autres)
/dac setspectator arena1

Se placer au premier coin du bassin
/dac pos1 arena1

Se placer au second coin (coin opposé)
/dac pos2 arena1

/dac info arena1

/dac enable arena1
```

Notes sur le bassin :

* `pos1`/`pos2` utilisent le bloc des pieds du joueur. La sélection peut englober une couche d'air au-dessus
  de l'eau : la **surface de jeu est la couche la plus haute contenant de l'eau**. Chaque bloc d'eau de cette
  couche est une case jouable.
* La plateforme de saut doit être au moins 2 blocs au-dessus de la surface et hors de la zone du bassin.
* Taille maximale configurable (`pool.max-width`, `max-length`, `max-depth`, 64×64×32 par défaut).
* `/dac enable` vérifie : monde chargé, lobby, jump, spectator, pos1, pos2, dimensions, présence d'eau,
  positions hors du bassin, min ≤ max, et assez de blocs uniques pour le maximum de joueurs.

## Jouer

```text
/dac join arena1
```

Déroulement :

1. **Lobby** : les joueurs sont sauvegardés, soignés, protégés, et choisissent leur bloc
   (voir [Sélection du bloc](#sélection-du-bloc)).
2. **Compte à rebours** quand le minimum est atteint (annulé si on repasse sous le minimum). Les titles
   5 → 1 changent de couleur (vert, vert, jaune, or, rouge) et le son monte d'une note à chaque seconde, puis un
   son distinct marque le départ.
3. **Début** : inscriptions fermées, bloc attribué à ceux qui n'ont pas choisi, ordre de passage mélangé,
   `starting-lives` vies pour chacun.
4. **Chaque tour** : le joueur est téléporté sur la plateforme (immobilisé 0,5 s) et a `turn-duration` secondes
   pour sauter (actionbar, et BossBar si activée). Les autres attendent au point spectateur.
5. **Réussite** : le joueur est mis en sécurité **puis** la case devient son bloc.
6. **Échec** : une vie en moins, retour au point d'attente, le joueur garde sa place dans l'ordre de passage.
   À 0 vie : élimination, mode spectateur au point spectateur (il ne peut ni interagir ni modifier le bassin).
7. **Dé à Coudre** : un saut réussi dans un trou d'eau 1×1 (les 4 cases nord/sud/est/ouest appartiennent au
   bassin et sont déjà remplies ; diagonales ignorées ; une case au bord ne compte jamais) donne +1 vie, sans
   dépasser `max-lives`. La détection est purement logique (état de la grille, jamais le type ou les propriétés
   physiques du bloc) : une case est « remplie » si un joueur l'a remplie **ou** si elle contenait déjà un bloc
   quelconque (verre, laine, pierre, décor…) dans la couche de surface de la sélection au lancement. Le « bord »
   est la limite de la sélection `pos1`/`pos2` (ou une colonne d'air). Le joueur entend un son personnel (lui seul) ; s'il est déjà au maximum, il a le son et
   un message dédié, sans vie en plus.
8. **Fin** : dernier survivant = victoire (feux d'artifice) ; aucun survivant = aucun gagnant ; bassin plein avec
   plusieurs survivants = égalité. Après `ending-duration` secondes, joueurs et bassin sont restaurés.

Le scoreboard affiche l'arène, les joueurs, le compte à rebours et le bloc choisi dans le lobby ; en partie, les
joueurs en vie, le tour, le bloc, le temps et les vies (`❤❤♡`).

## Sélection du bloc

* Dans le lobby, un item **« Choisir mon bloc »** est placé dans la barre d'action (`block-selector.lobby-item`) ;
  clic droit pour ouvrir le menu. `/dac block` ouvre le même menu.
* Le menu affiche les blocs de `block-selector.materials`. Avec `unique-blocks: true` (défaut), un bloc déjà
  choisi par un autre joueur de la partie apparaît « Indisponible » avec son nom, et ne peut pas être pris.
  Le bloc actuel du joueur brille.
* Un clic choisit le bloc, ferme le menu, affiche « Ton bloc : Béton cyan » et joue un petit son au joueur seul.
* Un joueur qui ne choisit rien reçoit un bloc libre au hasard au début de la partie : il ne bloque jamais le
  lancement.
* Le choix reste en mémoire le temps de la partie ; la partie suivante repart de zéro.
* Sécurité : tous les clics / drags sur le menu sont annulés (rien ne peut être pris ni déposé). L'item de lobby
  ne peut être ni jeté, ni déplacé, ni rangé, ni posé ; il disparaît au début de la partie et à la restauration
  de l'inventaire, et il est supprimé à la connexion s'il traînait après un crash.
* Liste blanche : seuls les blocs pleins et solides, sans gravité, sans inventaire ni entité de bloc, et hors
  blocs techniques ou problématiques (TNT, bedrock, barrière, glace, magma, redstone...) sont acceptés. Une
  entrée invalide est ignorée avec un warning explicite dans la console. 54 blocs maximum.
* Si `unique-blocks` est actif et qu'une arène autorise plus de joueurs que de blocs, elle ne peut pas être
  activée (validation) ; si la configuration change entre-temps, la partie ne démarre pas, avec un message clair
  aux joueurs et dans la console.

## Configuration

`config.yml` (commenté) :

| Option | Défaut | Description |
|--------|--------|-------------|
| `language` | `en` | Langue de `messages.yml` (`en`, `fr`) |
| `game.min-players` / `max-players` | 2 / 12 | Joueurs par défaut (surcharge par arène) |
| `game.starting-countdown` / `full-countdown` | 10 / 5 | Compte à rebours, réduit quand l'arène est pleine |
| `game.countdown-chat-seconds` / `countdown-title-seconds` | [60,30,10,5] / [5..1] | Secondes annoncées |
| `game.turn-duration` / `fall-grace` | 15 / 5 | Temps pour sauter, marge si déjà en chute |
| `game.starting-lives` / `max-lives` | 1 / 3 | Vies de départ et maximum (1 à 10) |
| `game.color-assignment` | `RANDOM` | Attribution des blocs non choisis : `RANDOM` ou `ORDERED` |
| `game.restore-inventory` | `true` | Sauvegarde/vidage/restauration de l'inventaire (sinon pas d'item de lobby) |
| `block-selector.enabled` | `true` | Sélecteur de bloc (sinon attribution automatique) |
| `block-selector.unique-blocks` | `true` | Deux joueurs d'une partie ne peuvent pas avoir le même bloc |
| `block-selector.lobby-item` / `lobby-item-material` / `lobby-item-slot` | `true` / `NETHER_STAR` / 4 | Item de lobby |
| `block-selector.materials` | 16 bétons | Liste blanche des blocs |
| `turn-bossbar.enabled` / `color` / `overlay` | `false` / `YELLOW` / `PROGRESS` | BossBar du temps de tour |
| `scoreboard.enabled` / `server-name` | `true` / `play.example.com` | Scoreboard |
| `protection.*` | | Commandes bloquées, distance max, téléportations externes |

**BossBar** : désactivée par défaut, car l'actionbar affiche déjà le temps restant. Activée, elle montre
« Tour de Loan — 7 s » aux seuls joueurs de l'arène, se vide jusqu'à 0 et disparaît à la fin de chaque tour, au
leave / à la déconnexion, au stop, à la fin de partie et au reset.

**Sons** (section `sounds`, clés Minecraft, `enabled`, `volume`, `pitch`) : `join`, `countdown` (avec
`pitch-by-second` : 5 → 0.8, 4 → 0.9, 3 → 1.0, 2 → 1.2, 1 → 1.4), `start`, `turn`, `tick`, `success`,
`perfect_jump` (Dé à Coudre, joueur seul), `block_select` (choix du bloc, joueur seul), `life_lost`,
`elimination`, `victory`, `end`.

**Messages** : `messages.yml`, format [MiniMessage](https://docs.papermc.io/adventure/minimessage/format/).
Placeholders : `%prefix% %player% %arena% %current% %max% %min% %seconds% %alive% %color% %lives% %max_lives%`
(et d'autres indiqués dans le fichier). Un message vide n'est pas envoyé.

**Arènes** : `arenas/<nom>.yml`, un fichier par arène, écrit immédiatement après chaque commande d'administration.

```yaml
enabled: true
world: world
lobby: {x: 0.5, y: 70.0, z: 0.5, yaw: 0.0, pitch: 0.0}
jump: {x: 0.5, y: 100.0, z: 0.5, yaw: 0.0, pitch: 90.0}
spectator: {x: 15.5, y: 80.0, z: 15.5, yaw: 0.0, pitch: 0.0}
pool:
  pos1: {x: -5, y: 60, z: -5}
  pos2: {x: 5, y: 63, z: 5}
settings:          # optionnel
  min-players: 2
  max-players: 8
```

### Migration depuis une version précédente

* `game.colors` est remplacé par `block-selector.materials`. Tant que seul `game.colors` est présent, il est
  encore utilisé (avec un warning) : déplacez la liste.
* Les nouvelles options et les nouveaux sons prennent leur valeur par défaut s'ils sont absents de `config.yml`.
* `messages.yml` : les nouvelles clés sont complétées automatiquement depuis la langue `language`. Un ancien
  fichier en français doit donc aller avec `language: fr`. Les listes du scoreboard et le title du countdown d'un
  ancien fichier sont conservés tels quels : pour avoir le bloc dans le lobby, les couleurs du countdown
  (`%countdown_color%`) et les vies, supprimez `messages.yml` (il sera recréé) ou reprenez ces clés depuis `lang/`.

## Compilation

Aucune installation de Gradle n'est nécessaire (wrapper inclus). Il faut un JDK 25 ; s'il n'est pas trouvé,
le plugin Gradle *foojay-resolver* le télécharge automatiquement.

```bash
./gradlew build
```

Sous Windows : `gradlew.bat build`.

Fichier JAR :

```text
build/libs/DeACoudre-1.0.0.jar
```

## Architecture

```text
fr.fixemy.deacoudre
├── DeACoudrePlugin          point d'entrée, câblage des services
├── arena/                   configuration des arènes
│   ├── Arena, ArenaState, ArenaManager, ArenaRepository (arenas/*.yml)
│   ├── ArenaValidator       validation détaillée avant /dac enable
│   ├── PoolScanner          lecture de la zone du bassin uniquement
│   └── PoolBackup, PoolBackupStore   sauvegarde / restauration des cases (+ reprise après crash)
├── game/                    logique de partie
│   ├── GameManager          sessions, index joueur → session, join/leave/stop
│   ├── GameSession          lobby, sélection des blocs, countdown, vies, éliminations, fin, reset
│   ├── TurnManager, Turn    ordre des tours, timer, BossBar, résolution unique d'un saut
│   ├── JumpDetector         analyse des mouvements (trajectoire échantillonnée, collisions serveur)
│   ├── PoolGrid             cases d'eau libres / occupées, détection du trou 1×1
│   ├── TaskRegistry         toutes les tâches d'une session, annulables d'un coup
│   └── GamePlayer, PlayerStatus, PlayerColor, FailReason, LeaveCause
├── gui/                     BlockSelectorMenu (menu du bloc), BlockSelectorItem (item de lobby)
├── player/                  PlayerManager, PlayerSnapshot, SnapshotStore
├── command/                 DeACoudreCommand (API Brigadier de Paper), DeACoudreTabCompleter, SubCommand
├── listener/                join, quit, move, death, block, damage, interaction, teleport, world, block selector
├── config/                  ConfigManager, Settings, MessageManager, GameSound, SoundEffect
├── scoreboard/              ScoreboardManager, Sidebar (sans flicker)
└── util/                    BlockPos, Cuboid, StoredLocation, LocationSerializer, ColorUtil, Placeholders, AsyncFileWriter
```

Choix techniques principaux :

* **Commandes** enregistrées via `JavaPlugin#registerCommand` (API Brigadier de Paper, `BasicCommand`),
  `plugin.yml` classique pour les permissions (`paper-plugin.yml` est encore expérimental).
* **Détection du saut** : `PlayerMoveEvent` (priorité MONITOR) uniquement pour le joueur actif. Le trajet entre
  deux positions est échantillonné tous les 0,25 bloc : une chute rapide ne peut pas « sauter » la surface, et la
  case retenue est la colonne du premier bloc d'eau rencontré (fiable avec un bassin profond). L'atterrissage est
  détecté côté serveur grâce aux formes de collision des blocs (`Player#isOnGround` est contrôlé par le client et
  déprécié). Les dégâts de chute servent de signal supplémentaire, et le timer vérifie `isInWater()` en filet de
  sécurité. Un tour ne peut être résolu qu'une seule fois (`Turn#resolve`).
* **Sauvegarde du joueur** en mémoire + miroir disque (`data/snapshots/<uuid>.yml`, écrit hors du thread
  principal) : restauration à la déconnexion, et reprise au login suivant après un crash ou un échec.
* **Bassin** : seules les cases d'eau de la surface sont sauvegardées (BlockData). Le backup est aussi écrit dans
  `data/pool-backups/` pendant la partie : après un crash, le bassin est restauré au démarrage (ou au chargement
  du monde).
* **Tâches** : chaque session range ses tâches dans des slots nommés ; programmer un slot annule l'ancienne tâche,
  et la destruction de la session ferme le registre. Toute exception dans la logique de jeu arrête la partie
  proprement (joueurs et bassin restaurés) au lieu de bloquer l'arène.
* **Menus** : l'inventaire du sélecteur est identifié par son `InventoryHolder` (jamais par son titre) ; la
  sélection ne vit qu'en mémoire (aucun accès disque).
* **Sons** : tous passent par `GameSound` / `SoundEffect` et sont joués à une audience précise (le joueur seul
  pour les sons personnels, les joueurs de l'arène sinon), jamais via `world.playSound()`.
* **Adventure / MiniMessage** partout (titles, actionbar, BossBar, noms d'items, scoreboard `customName` +
  `NumberFormat.blank()`).

## Tests manuels

| # | Scénario | Résultat attendu |
|---|----------|------------------|
| 1 | 2 joueurs font `/dac join arena1` | Countdown (chat à 10 et 5 s, titles 5..1), démarrage, blocs, premier tour |
| 2 | Le joueur actif tombe dans l'eau | Téléporté au point spectateur, son bloc posé, joueur suivant |
| 3 | Le joueur actif tombe sur un bloc déjà posé / sur le bord | Perte d'une vie (élimination avec 1 vie) |
| 4 | Le joueur actif se déconnecte | Élimination, tour suivant immédiat, état restauré (ou au prochain login) |
| 5 | Il ne reste qu'un joueur | VICTOIRE, feux d'artifice, puis reset après 8 s |
| 6 | `/dac stop arena1` en pleine partie | Partie arrêtée, joueurs restaurés (inventaire, position, gamemode), bassin restauré |
| 7 | Un joueur quitte le lobby pendant le countdown sous le minimum | Countdown annulé, retour en attente |
| 8 | Le joueur ne saute pas | Perte d'une vie / élimination « n'a pas sauté à temps » |
| 9 | `/dac leave` pendant son tour | Élimination, tour suivant, état restauré |
| 10 | Arrêt du serveur en pleine partie | Joueurs et bassin restaurés dans `onDisable` |
| 11 | Remplir tout le bassin avec 2 survivants (petit bassin) | Égalité, liste des survivants |
| 12 | `/tp` d'un participant par un admin | Téléportation annulée (`external-teleport: CANCEL`) |
| 13 | Un joueur seul + `/dac forcestart` | Partie solo jouable jusqu'à l'échec ou au bassin plein |

### Vies et bonus du trou 1×1

Pour les tests V2 et V6, régler `starting-lives` dans `config.yml` puis `/dac reload`.

| # | Scénario | Résultat attendu |
|---|----------|------------------|
| V1 | Joueur avec 1 vie rate son saut | Éliminé (spectateur), message « Tu n'as plus de vie » |
| V2 | Joueur avec 2 vies rate son saut | Reste en jeu avec 1 vie, « ✖ Saut raté ! Il te reste 1 vie(s). », renvoyé au point d'attente, rejoue à son prochain tour |
| V3 | Saut normal réussi | Bloc posé, aucune vie gagnée |
| V4 | Saut dans un vrai trou 1×1 (4 voisins N/S/E/O remplis) | Bloc posé, +1 vie, title « +1 VIE », cœurs mis à jour |
| V5 | Trou au bord du bassin (3 voisins remplis + le bord) | Saut réussi, aucun bonus |
| V6 | Trou 1×1 avec un joueur déjà à `max-lives` | Saut réussi, son du Dé à Coudre, « Tu as déjà le maximum de vies », pas de vie en plus |
| V7 | Partie où plusieurs vies sont perdues puis dernier survivant | Victoire, reset et restauration comme avant |
| V8 | Trou 1×1 entouré de verre, ou d'un mélange verre / laine / pierre / béton déjà présent dans le bassin | +1 vie |
| V9 | Diagonales encore en eau, 4 voisins orthogonaux remplis | +1 vie |

Tests unitaires automatiques (`./gradlew build`) : `PoolGridTest` (béton, verre, mélange, voisin en eau, bord,
air, diagonales, pas de double déclenchement) et `GamePlayerTest` (plafond `max-lives`).

### Sélection du bloc, countdown, BossBar

| # | Scénario | Résultat attendu |
|---|----------|------------------|
| S1 | Clic droit sur l'item du lobby, puis `/dac block` | Le menu « Choisis ton bloc » s'ouvre |
| S2 | Clic sur un bloc libre | Menu fermé, « Ton bloc : ... », petit son (joueur seul), scoreboard « Bloc: ... » |
| S3 | Clic sur un bloc pris par un autre joueur | Lore « Indisponible », refusé, message « déjà choisi » |
| S4 | Un joueur ne choisit rien | Un bloc libre lui est attribué au début de la partie |
| S5 | Essayer de prendre / déplacer / jeter l'item du lobby ou un bloc du menu (shift-clic, touches 1-9, Q, F) | Impossible |
| S6 | `/dac leave` depuis le lobby | Inventaire d'origine restauré, sans l'item du lobby |
| S7 | Sauts réussis de plusieurs joueurs | Chaque case prend le bloc choisi par le joueur |
| S8 | Dé à Coudre | +1 vie ; le son personnel n'est entendu que par le sauteur |
| S9 | Countdown | Titles 5 et 4 verts, 3 jaune, 2 or, 1 rouge ; pitch montant ; son de départ distinct après 1 |
| S10 | `turn-bossbar.enabled: true` | BossBar « Tour de X — N s » qui se vide, retirée à chaque fin de tour, leave, stop, fin de partie |
| S11 | Joueur éliminé | Spectateur au point spectateur, aucune interaction, restauré en fin de partie |
| S12 | Partie suivante dans la même arène | Blocs, vies et BossBar remis à zéro |

Activer `debug.enabled: true` pour suivre dans la console les changements d'état, tours, chutes, cases
validées, choix de blocs, éliminations et resets.
