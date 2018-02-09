CREATE TABLE person (
  id bigint,
  first_name varchar(255) not null,
  last_name varchar(255) not null
);

insert into person (id, first_name, last_name) values (1, 'John', 'Doe');