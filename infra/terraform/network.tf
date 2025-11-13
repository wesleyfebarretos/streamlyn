resource "docker_network" "streamlyn" {
  name   = "streamlyn"
  driver = "bridge"
}