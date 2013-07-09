drop table if exists users;
create table users (
    id bigint(20) not null auto_increment,
    fid varchar(64) unique not null,
    username varchar(128) not null,
    first_name varchar(256),
    middle_name varchar(256),
    last_name varchar(256),
    primary key (id)
);
