CREATE TABLE user(
    email                           TEXT                    PRIMARY KEY NOT NULL,
    first_name                      TEXT                    NOT NULL,
    last_name                       TEXT                    NOT NULL,
    calendar_provider               TEXT                    NOT NULL,
    calendar_connection_status      TEXT                    NOT NULL,
    google_refresh_token            TEXT                    NULL,
    metadata                        BLOB                    NULL,
    created_at                      TIMESTAMP WITH TIMEZONE NOT NULL,
    last_updated_at                 TIMESTAMP WITH TIMEZONE NULL
);
