
-- sqlite3 hdemo.db
-- .read resources/schema_sqlite.sql
-- .q

create table address (
        id integer primary key autoincrement,
        street text,
        city text,
        postal_code text
);

create index address_ndx1 on address (city,street,postal_code);
