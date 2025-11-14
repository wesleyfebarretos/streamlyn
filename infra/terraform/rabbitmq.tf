resource "docker_image" "rabbitmq" {
  name = "rabbitmq:4.2.0-management-alpine"
  keep_locally = true
}

resource "docker_container" "rabbitmq" {
  name = "rabbitmq"
  image = docker_image.rabbitmq.image_id

  networks_advanced {
    name = docker_network.streamlyn.id
  }

  ports {
    internal = 5672
    external = 5672
  }

  ports {
    internal = 15672
    external = 15672
  }

  env = [
    "RABBITMQ_DEFAULT_USER=streamlyn",
    "RABBITMQ_DEFAULT_PASS=streamlyn"
  ]
}