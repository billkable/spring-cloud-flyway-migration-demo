CREATE TABLE address (
  id bigint,
  address1 varchar(255) not null,
  address2 varchar(255),
  city varchar(255) not null,
  state varchar(255) not null,
  zipcode varchar(255) not null
);

insert into address (id, address1, address2, city, state, zipcode)
values (1, '555 Main St', '', 'San Francisco','CA','12345');