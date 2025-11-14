resource "docker_volume" "mongo" {
  driver = "local"
}

resource "docker_image" "mongo" {
  name         = "mongo:8.0"
  keep_locally = true
}

resource "docker_container" "mongodb" {
  name  = "mongo"
  image = docker_image.mongo.image_id

  ports {
    internal = 27017
    external = 27017
  }

  env = [
    "MONGO_INITDB_ROOT_USERNAME=admin",
    "MONGO_INITDB_ROOT_PASSWORD=admin",
    "MONGO_INITDB_DATABASE=streamlyn",
  ]

  volumes {
    volume_name    = docker_volume.mongo.name
    container_path = "/data/db"
  }

  networks_advanced {
    name = docker_network.streamlyn.id
  }

  entrypoint = [
    "bash",
    "-c",
    <<-EOF
        if [ ! -f /data/security-key ]; then
          openssl rand -base64 756 > /data/security-key
          chmod 400 /data/security-key
          chown 999:999 /data/security-key
        fi
        exec docker-entrypoint.sh "$@"
    EOF
  ]

  command = [
    "mongod",
    "--bind_ip_all",
    "--replSet",
    "rs0",
    "--keyFile",
    "/data/security-key"
  ]
}

resource "docker_container" "mongo_setup" {
  name  = "mongo_setup"
  image = docker_image.mongo.image_id

  env = [
    "MONGO_INITDB_ROOT_USERNAME=admin",
    "MONGO_INITDB_ROOT_PASSWORD=admin",
  ]

  networks_advanced {
    name = docker_network.streamlyn.id
  }

  depends_on = [docker_container.mongodb]

  volumes {
    host_path      = "${path.cwd}/scripts/mongo-init.sh"
    container_path = "/scripts/mongo-init.sh"
  }

  restart = "on-failure"

  entrypoint = [
    "/bin/bash",
    "/scripts/mongo-init.sh"
  ]
}