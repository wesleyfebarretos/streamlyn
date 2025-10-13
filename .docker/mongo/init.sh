#!/bin/bash

openssl rand -base64 756 > ./security-key

m1=mongo
port=${PORT:-27017}
user=${MONGO_INITDB_ROOT_USERNAME:-admin}
password=${MONGO_INITDB_ROOT_PASSWORD:-admin}

echo "###### Waiting for ${m1} instance startup.."
until mongosh --host ${m1}:${port} --eval 'quit(db.runCommand({ ping: 1 }).ok ? 0 : 2)' &>/dev/null; do
  printf '.'
  sleep 1
done
echo "###### Working ${m1} instance found, initiating user setup & initializing rs setup.."

# setup user + pass and initialize replica sets
mongosh --host ${m1}:${port} <<EOF
var rootUser = '${user}';
var rootPassword = '${password}';
var admin = db.getSiblingDB('admin');
admin.auth(rootUser, rootPassword);

var config = {
    "_id": "rs0",
    "version": 1,
    "members": [
        {
            "_id": 1,
            "host": "localhost:${port}",
            "priority": 2
        }
    ]
};
rs.initiate(config, { force: true });
rs.status();
EOF