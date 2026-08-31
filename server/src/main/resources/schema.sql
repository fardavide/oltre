-- The whole persistence design, and it is three tables because the save is already a self-contained
-- JSON document that `core` knows how to carry forward. There are no joins, no queries beyond "get
-- by player id", and no migration framework: this file is the schema, it is applied at startup, and
-- every statement in it is `IF NOT EXISTS` so applying it to a database that already has it is a
-- no-op rather than an error. See `#106` §5.4.
--
-- **This is Postgres and it does not pretend otherwise** — Davide, 2026-08-25. `#106` §6 chose
-- Postgres over Firestore partly on the claim that Cloud Run + Neon and a VPS + SQLite would be
-- "the same code modulo a driver"; `jsonb`, `timestamptz` and `ON CONFLICT` are none of them
-- portable, so that sentence is corrected in `decisions.md` rather than the SQL written down to
-- preserve it. The escape hatch is a second implementation of `ColonyRepository`, which is a class
-- rather than a line, and is what the interface exists to make cheap.

-- Who a colony belongs to, and **since `#110` this table is the only thing that says so**. A row is
-- written at sign-in and nowhere else: `PostgresPlayerRepository.resolve` inserts `(provider,
-- subject)` — a verified Apple or Google subject, never an email (`#106` §4) — and hands back `id`,
-- which is a surrogate key and not the subject. `#109` wrote the shape and forged rows from the
-- `X-Oltre-Player` header under the provider name `'header'`; that path survives only where no
-- `SESSION_SIGNING_KEY` is configured, which is the dev loop and nothing that is deployed.
--
-- `UNIQUE (provider, subject)` is what makes a second sign-in find the same colony, and the pair
-- rather than the subject alone because nothing about a subject is globally unique — Apple and
-- Google can both mint `1234` and mean two different people.
--
-- **The two foreign keys below are the whole of account deletion**, which App Review 5.1.1(v)
-- requires and `#110` implements: `DELETE FROM players WHERE id = ?` takes the colony and every
-- spent idempotency key with it, in one transaction, with no ordering for the code to get wrong. And
-- the surrogate key is what makes the *next* sign-in a fresh colony rather than a resurrection —
-- the same subject comes back to a new `id`, which has nothing hanging off it.
CREATE TABLE IF NOT EXISTS players (
    id         text        PRIMARY KEY,
    provider   text        NOT NULL,
    subject    text        NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (provider, subject)
);

-- **The profile, and it is an `ALTER` rather than two more lines in the `CREATE` above.** This file
-- is applied at startup against a database that already has `players`, and `CREATE TABLE IF NOT
-- EXISTS` on an existing table is a no-op — so columns added to that statement would exist on a
-- fresh database and on no deployed one, which is the quietest kind of broken. `IF NOT EXISTS` on
-- the `ALTER` keeps the whole file idempotent, which is the property that lets there be no migration
-- framework at all.
--
-- **Both nullable and neither defaulted.** Null is *"has not chosen"*, which is what every account
-- founded before this slice reads, and what the strip answers with `Strings.playerDefaultName()`.
-- A default here would put a name in the database that no player typed.
--
-- `display_name` and not `name`: `name` is a reserved-ish word in enough dialects and enough ORMs to
-- be worth not finding out about, and the column holds what is *drawn* rather than an identifier —
-- the identifier is `id`, and nothing about this column is unique. Two commanders may share one, by
-- Davide's call on 2026-08-30; see `profile-sheet.md` §1.
--
-- **`mark` is `jsonb` and not three columns**, which is the one shape decision here worth arguing.
-- A mark is a preset *or* a composition of three parts — `PlayerMark` in `:protocol` — so columns
-- would be four of them, three null for every preset, with a cross-column invariant ("a terminus is
-- the end of a path") that no constraint here could state as well as the type already does. As a
-- document it is one column, decoded by the same codec both ends use, and the invariant is enforced
-- where it was written. It is `colonies.snapshot_json`'s argument at one field instead of a save.
--
-- Nothing in this file reads inside it, exactly as nothing reads inside `snapshot_json`.
ALTER TABLE players ADD COLUMN IF NOT EXISTS display_name text;
ALTER TABLE players ADD COLUMN IF NOT EXISTS mark         jsonb;

-- One row per player, and the row **is** the colony.
--
-- `snapshot_json` is `GameSave.encode(snapshot)` verbatim, as `jsonb` rather than `text`: the value
-- is a document Postgres can validate, index and query into if an operator ever needs to, and
-- storing it as a string would throw that away for nothing. Nothing in the server reads a field out
-- of it — the migration ladder in `core` is what reads a save — so the column is a payload here and
-- a diagnostic to anyone holding a `psql` prompt.
--
-- `schema_version` and `last_updated_at` are denormalised out of that document and are deliberately
-- not read by this code either. They are there so that "which colonies are still on schema 15" and
-- "which colonies have not synced in a month" are one query rather than a full scan and a JSON
-- parse — the two questions a deploy actually asks.
--
-- `version` is the compare-and-set. A write asserts the value it read, and a write that asserts a
-- value the row has moved past updates no row at all. That is the entire concurrency design and it
-- is why there is no `SELECT … FOR UPDATE` anywhere in this schema: an optimistic token costs
-- nothing when two devices do not collide, which is almost always, and a lock costs a held
-- connection every time.
CREATE TABLE IF NOT EXISTS colonies (
    player_id       text        PRIMARY KEY REFERENCES players (id) ON DELETE CASCADE,
    schema_version  integer     NOT NULL,
    last_updated_at timestamptz NOT NULL,
    snapshot_json   jsonb       NOT NULL,
    version         bigint      NOT NULL,
    updated_at      timestamptz NOT NULL
);

-- Retry protection, and prunable. A verb whose response was lost on a flaky train connection gets
-- resent, and without a record of what has already been applied that is a double-spend.
--
-- **The primary key is the pair and not the key alone.** Nothing about an idempotency key is
-- globally unique — the wire refuses to check anything but that one was minted — so a key column on
-- its own would let one player's retry silently swallow another player's verb.
--
-- The index on `applied_at` is for the prune and for nothing else: the table is written by player
-- and read by player, and swept by age.
CREATE TABLE IF NOT EXISTS applied_verbs (
    idempotency_key text        NOT NULL,
    player_id       text        NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    applied_at      timestamptz NOT NULL,
    PRIMARY KEY (player_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS applied_verbs_applied_at ON applied_verbs (applied_at);
