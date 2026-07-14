# Simply Better Warps — Claude Instructions

## ✅ Migration unified-db (2026-05-10) — DONE (via core)

**Status**: déployé prod. Pas de modif source code dans ce repo — tout passe par `simply-better-core` qui gère la DB (rôle SBS = `sbs_app`, search_path `sbs, core, public`). Voir `../simply-better-core-1.21.1/CLAUDE.md` pour détails.



## Rôle

Mod **Fabric 1.21.1** de la suite Simply Better. Système de warps (points de téléportation publics nommés, gérés par admins).

**Dépend de `simply-better-core`** pour:
- DB access (DatabaseManager partagé via HikariCP)
- Schéma (tables `sb_warps`, `sb_positions`, `sb_players` créés par core)
- Config (sbcore-conf.json)

## DB — pas de code DB local

Pas de `Database.java` ni schema dans ce mod. Consomme:
- `sb_warps` (id, name UNIQUE, created_at, created_by_uuid, position_id)
- `sb_positions` (jointure)
- `sb_players` (FK created_by_uuid)
- `sb_rtp_settings` éventuellement, si /rtp implémenté ici

Queries via `WarpsCrudManager`/`PositionsCrudManager` du core.

## Public release vs unified setup

Identique à core: zéro changement de code pour basculer. Géré par `search_path` du rôle SQL.

## Notes pour Claude

- Pas de pool DB indépendant. Si tu vois un `HikariConfig` ici → erreur d'archi.
- Schéma source: `C:\Users\alsch\Documents\simply-better-1.21.1\simply-better-core-1.21.1\src\main\resources\simplybetter\schema.sql` (table `sb_warps`).
- Plan migration unifiée: `C:\Users\alsch\Documents\minecraft-db-unification\plans\migration.md` §2.4b.
- Présence de `run_driver_test.py` à la racine: script de test au driver (probablement test de teleport via stdin/stdout). Pas lié à la DB.
