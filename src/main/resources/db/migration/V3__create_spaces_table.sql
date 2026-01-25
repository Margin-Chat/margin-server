CREATE TABLE spaces
(
    space_id    BIGSERIAL    NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    visibility  SMALLINT     NOT NULL,
    CONSTRAINT pk_spaces PRIMARY KEY (space_id)
);