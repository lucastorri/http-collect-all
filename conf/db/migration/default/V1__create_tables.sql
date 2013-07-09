drop table if exists users;
create table users (
    id bigint(20) not null auto_increment,
    username varchar(64) not null,
    primary key (id)
);
