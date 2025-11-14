resource "docker_volume" "redis" {
  driver = "local"
}

resource "docker_image" "redis" {
  name         = "redis:7.4.6-alpine"
  keep_locally = true
}

resource "docker_container" "redis" {
  image = docker_image.redis.image_id
  name  = "redis"

  networks_advanced {
    name = docker_network.streamlyn.id
  }

  volumes {
    volume_name    = docker_volume.redis.name
    container_path = "/data"
  }

  ports {
    internal = 6379
    external = 6379
  }

  command = ["redis-server", "--appendonly", "yes"]
}
