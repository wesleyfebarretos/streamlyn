resource "docker_image" "redis_insight" {
  name         = "redis/redisinsight:2.70"
  keep_locally = true
}

resource "docker_container" "redis_insight" {
  name  = "redisinsight"
  image = docker_image.redis_insight.image_id

  networks_advanced {
    name = docker_network.streamlyn.id
  }

  ports {
    internal = 5540
    external = 5540
  }

  env = ["RI_REDIS_HOST=${docker_container.redis.name}"]
}
