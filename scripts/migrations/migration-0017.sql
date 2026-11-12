ALTER TABLE users
    ALTER description TYPE varchar (10240);

ALTER TABLE community_posts
    ALTER plain_text TYPE varchar (10240);
