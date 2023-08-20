CREATE TABLE user_personal_task(
    id                              UUID                    PRIMARY KEY NOT NULL,
    user_email                      TEXT                    NOT NULL,
    title                           TEXT                    NOT NULL,
    description                     TEXT                    NULL,
    metadata                        BLOB                    NOT NULL,
    created_at                      TIMESTAMP WITH TIMEZONE NOT NULL,
    last_updated_at                 TIMESTAMP WITH TIMEZONE NULL,
    FOREIGN KEY(user_email) REFERENCES user(email)
);

CREATE INDEX user_personal_task_user_email_idx ON user_personal_task(user_email);