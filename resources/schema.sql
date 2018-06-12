
-- psql template1
-- \i resources/schema.sql
-- \q

create user dbuser with encrypted password 'changethis';
create database hdemo;
alter database hdemo owner to dbuser;

\c hdemo dbuser

create sequence main;

create table address (
	id integer primary key default nextval('main'),
        street text,
        city text,
        postal_code text
);

create index address_ndx1 on address (city,street,postal_code);

