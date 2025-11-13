resource "docker_image" "minio_image" {
  name         = "quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1"
  keep_locally = true
}

resource "docker_volume" "minio_data" {
  driver = "local"
}

resource "docker_container" "minio" {
  image = docker_image.minio_image.image_id
  name  = "minio"

  networks_advanced {
    name = docker_network.streamlyn.id
  }

  ports {
    internal = 9000
    external = 9000
  }

  ports {
    internal = 9001
    external = 9001
  }

  volumes {
    volume_name    = docker_volume.minio_data.name
    container_path = "/data"
  }

  env = [
    "MINIO_ROOT_USER=streamlyn",
    "MINIO_ROOT_PASSWORD=streamlyn",
    "MINIO_BUCKETS=streamlyn",
  ]

  command = [
    "server",
    "/data",
    "--console-address",
    ":9001"
  ]
}

resource "docker_container" "minio_setup" {
  name = "minio_setup"
  image = docker_image.minio_image.image_id

  depends_on = [docker_container.minio]

  restart = "on-failure"

  networks_advanced {
    name = docker_network.streamlyn.id
  }

  entrypoint = [
    "/bin/sh",
    "-c",
    <<-EOF
      sleep 5;
      /usr/bin/mc alias set dockerminio http://minio:9000 streamlyn streamlyn;
      /usr/bin/mc mb dockerminio/streamlyn;
      exit 0;
    EOF
  ]
}
