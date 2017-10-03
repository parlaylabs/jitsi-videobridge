import os, sys, time
import subprocess
import random
import requests


# Starts up a container from the given image name, passing the given rest and media
#  ports in as configuration values.
# Returns the id of the started container
def start_docker_container(docker_image_name, rest_port, media_port):
  docker_command = ["docker", "run", "-d", "--net=host", "-e", "REST_PORT=%s" % rest_port, "-e", "MEDIA_SINGLE_PORT=%s" % media_port, docker_image_name]
  try:
    docker_container_start_output = subprocess.check_output(docker_command)
  except Exception as e:
    print "error starting container: %s" % (e);
  docker_container_id = docker_container_start_output.rstrip()
  return docker_container_id

def stop_docker_container(docker_container_id):
  print("Stopping docker container %s" % docker_container_id)
  try:
    stopped_container_id = subprocess.check_output(["docker", "stop", docker_container_id]).rstrip()
  except Exception as e:
    print("Error stopping container: %s" % stopped_container_id)
    print(e)
  else:
    if stopped_container_id == docker_container_id:
      print("Successfully stopped container")
    else:
      print("Unknown issue stopping container: %s" % stopped_container_id)

# Returns True if the bridge is up and healthy, False if it's up
#  but not healthy.
# NOTE: right now this will retry forever, should probably put it in
#  a bounder for loop, but sometimes the bridge needs to download a bunch
#  of stuff so it can take some time to start
def wait_for_bridge_up(bridge_url):
  health_url = bridge_url + "/about/health"
  while True:
    print("Checking if bridge is up...")
    try:
      resp = requests.get(health_url, timeout=5)
    except Exception as e:
      print("Timeout: %s" % e)
      time.sleep(3)
      continue
    if resp.status_code == 200:
      print("Bridge is up and healthy")
      return True
    elif resp.status_code == 503:
      print("Bridge still coming up")
    else:
      print("Bridge is up but isn't healthy: %s" % resp.status_code)
      print("See https://github.com/jitsi/jitsi-videobridge/blob/master/doc/health-checks.md")
      return False
    time.sleep(3)

if len(sys.argv) < 2:
  print "Usage: %s [bridge container name]" % sys.argv[0]
  sys.exit(1)

bridge_docker_container_image_name = sys.argv[1]

# Get docker host ip
# if the machine doesn't support docker natively, there will be a DOCKER_HOST
#  env variable of the form "tcp://192.168.59.103:2376", so strip 
#  the first 6 chars and the last 5 chars to get just the ip
# if the environment variable isn't there, assume the host supports docker
#  natively and use 127.0.0.1
if "DOCKER_HOST" in os.environ:
  docker_host_ip = os.environ.get('DOCKER_HOST')[6:][:-5]
else:
  docker_host_ip = "127.0.0.1"
print("Using docker host ip: %s" % docker_host_ip)
# Generate random REST and media ports
# NOTE: should we be smarter about generating these (make sure we don't generate ones already in use?), 
#  and we should probably support passed-in values
rest_port = random.randrange(8000, 8100)
media_port = random.randrange(5000, 6000)
print("Using REST port %d and media port %d" % (rest_port, media_port))
# Build the bridge base REST url
bridge_rest_url = "http://{docker_host_ip}:{rest_port}".format(docker_host_ip=docker_host_ip, rest_port=rest_port)

# Start up videobridge container.
docker_container_id = start_docker_container(bridge_docker_container_image_name, rest_port, media_port)
print("Started docker container: %s" % (docker_container_id))
# Poll the bridge rest api to make sure the bridge is up
bridge_up = wait_for_bridge_up(bridge_rest_url)
if not bridge_up:
  sys.exit(1)

# Start the test (passing the bridge url)
print("Running tests")
print(subprocess.check_output(["pwd"]))
ret = subprocess.call(["python", "./test/test_files/videobridge_rest_api_test.py", bridge_rest_url])
if ret == 0:
  print("All tests passed")
else:
  print("Test failure")

# Kill the docker container
stop_docker_container(docker_container_id)
