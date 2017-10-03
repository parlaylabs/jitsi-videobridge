import requests
import json
import sys
import unittest
from unittest import TestCase
from colibri_helpers import *
from xmlrunner import XMLTestRunner

REST_ENDPOINT = "/colibri/conferences"

# client_id: unique client id for the client these channels are being allocated for
# content_types: a list of desired channel content types (audio, video, data)
def make_channel_allocation(bridge_conf_id, client_id, content_types):
  ciq = ColibriIq(bridge_conf_id)
  channel_bundle = ChannelBundle(client_id)
  ciq.channel_bundles.append(channel_bundle)

  for content_type in content_types:
    content = Content(content_type)
    if content_type == "data":
      content.add_sctp_connection(SctpConnection(client_id))
    else:
      content.add_channel(Channel(client_id))
    ciq.contents.append(content)

  return ciq

class VideobridgeRestApiTest(TestCase):
  BRIDGE_URL=""

  def _allocate_conference(self):
    payload = {}
    resp = requests.post(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT, json=payload)
    resp_json = resp.json()
    return resp_json.get("id", None)

  # Test that setting the direction for channels works correctly
  # NOTE: we can't test this as the direction doesn't get set until the bridge sees that
  #  the stream has 'latched'.  since we don't do any ICE here, that won't happen, so
  #  we can't test the channel direction
  # See the comment/code in RtpChannel::setDirection in the bridge
  '''
  def test_channel_direction_setting(self):
    bridge_conf_id = self._allocate_conference()
    endpoint_id = "random_endpoint_id"
    video_channel_direction = "recvonly"
    audio_channel_direction = "sendonly"
    ciq = make_channel_allocation(bridge_conf_id, endpoint_id, ["audio", "video"])
    # Change the direction of the audio and video channels from the default 'sendrecv'
    video_content = ciq.get_content("video")
    video_content.channels[0].direction = video_channel_direction

    audio_content = ciq.get_content("audio")
    audio_content.channels[0].direction = audio_channel_direction

    allocate_req = to_colibri_string(ciq)

    resp = requests.patch(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT + "/" + bridge_conf_id, json=json.loads(allocate_req))
    self.assertEqual(resp.status_code, 200)
    resp_json = resp.json()
    audio_channel_resp = get_resp_channel(resp_json, "audio", endpoint_id)
    self.assertIsNotNone(audio_channel_resp)
    self.assertEqual(audio_channel_resp["direction"], audio_channel_direction)
  '''

  def test_conference_allocation(self):
    conf_id = self._allocate_conference()
    self.assertIsNotNone(conf_id)

  def test_channel_allocation(self):
    bridge_conf_id = self._allocate_conference()

    ciq = make_channel_allocation(bridge_conf_id, "random_endpoint_id", ["audio", "video", "data"])
    allocate_req = to_colibri_string(ciq)

    resp = requests.patch(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT + "/" + bridge_conf_id, json=json.loads(allocate_req))
    self.assertEqual(resp.status_code, 200)
    resp_json = resp.json()
    self.assertIn("contents", resp_json)
    contents = resp_json["contents"]
    # audio, video, application
    self.assertEqual(len(contents), 3)

  # allocate channels and mark the audio channel to receive mixed audio
  def test_channel_allocation_audio_mixed(self):
    bridge_conf_id = self._allocate_conference()
    ciq = make_channel_allocation(bridge_conf_id, "random_endpoint_id", ["audio", "video", "data"])
    # Change the audio channel to use the 'mixed' rtp-level-relay-type
    audio_content = ciq.get_content("audio")
    audio_content.channels[0].rtp_level_relay_type = "mixer"
    allocate_req = to_colibri_string(ciq)

    resp = requests.patch(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT + "/" + bridge_conf_id, json=json.loads(allocate_req))
    self.assertEqual(resp.status_code, 200)
    resp_json = resp.json()
    self.assertIn("contents", resp_json)
    contents = resp_json["contents"]
    audio = next(content for content in contents if content["name"] == "audio")
    audio_channel = audio["channels"][0]
    self.assertEqual(audio_channel["rtp-level-relay-type"], "mixer")

  # Verify that setting a channel's expiration to '0' immediately expires that channel
  def test_channel_expiration(self):
    bridge_conf_id = self._allocate_conference()
    endpoint_id = "random_endpoint_id"
    ciq = make_channel_allocation(bridge_conf_id, endpoint_id, ["audio"])
    allocate_req = to_colibri_string(ciq)

    resp = requests.patch(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT + "/" + bridge_conf_id, json=json.loads(allocate_req))
    self.assertEqual(resp.status_code, 200)
    resp_json = resp.json()
    # Get the id that was assigned to the channel (we'll need to include it in our next update)
    channel_id = get_resp_channel(resp_json, "audio", endpoint_id).get("id", None)
    # Assign the id and set the expiration to 0 (expire immediately)
    channel = ciq.get_content("audio").get_channel(endpoint_id)
    channel.colibri_id = channel_id
    channel.expire = 0
    allocate_req = to_colibri_string(ciq)
    # Patch the bridge to expire the channel
    resp = requests.patch(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT + "/" + bridge_conf_id, json=json.loads(allocate_req))
    self.assertEqual(resp.status_code, 200)
    resp_json = resp.json()
    # Make sure the channel is gone in the response
    self.assertIsNone(get_resp_channel(resp_json, "audio", endpoint_id))
    # Double check by doing a get to verify the channel is gone
    resp = requests.get(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT + "/" + bridge_conf_id)
    self.assertEqual(resp.status_code, 200)
    resp_json = resp.json()
    self.assertIsNone(get_resp_channel(resp_json, "audio", endpoint_id))

  # Verify that we can manually add an ssrc to a channel so it will be accepted
  # NOTE: this is used in the case of video mute in chrome, where chrome will not
  #  send any video media, but will send rtcp for the media it receives.  This rtcp
  #  is sent using ssrc "1", and since the videbridge channel doesn't auto-add rtcp
  #  ssrcs, we need to add it explicitly
  def test_add_ssrc(self):
    bridge_conf_id = self._allocate_conference()
    endpoint_id = "random_endpoint_id"
    ciq = make_channel_allocation(bridge_conf_id, endpoint_id, ["video"])
    # Add an ssrc to the 'sources' field in the channel
    channel = ciq.get_content("video").get_channel(endpoint_id)
    channel.sources = [1]
    # Patch the conference
    allocate_req = to_colibri_string(ciq)
    resp = requests.patch(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT + "/" + bridge_conf_id, json=json.loads(allocate_req))
    self.assertEqual(resp.status_code, 200)
    resp_json = resp.json()
    channel = get_resp_channel(resp_json, "video", endpoint_id)
    self.assertEqual(len(channel["ssrcs"]), 1)
    self.assertEqual(channel["ssrcs"][0], 1)

  # Test setting the 'last-n' field for a video channel
  def test_video_last_n(self):
    bridge_conf_id = self._allocate_conference()
    endpoint_id = "random_endpoint_id"
    ciq = make_channel_allocation(bridge_conf_id, endpoint_id, ["video"])
    # Set last-n
    channel = ciq.get_content("video").get_channel(endpoint_id)
    channel.last_n = 3
    # Patch the conference
    allocate_req = to_colibri_string(ciq)
    resp = requests.patch(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT + "/" + bridge_conf_id, json=json.loads(allocate_req))
    self.assertEqual(resp.status_code, 200)
    resp_json = resp.json()
    channel = get_resp_channel(resp_json, "video", endpoint_id)
    self.assertIn("last-n", channel)
    self.assertEqual(channel["last-n"], 3)

  # test multi-client joins (verify all the data shows up)
  def test_multiple_clients(self):
    bridge_conf_id = self._allocate_conference()
    # Channels for endpoint one
    endpoint_one_id = "endpoint_one"
    ciq = make_channel_allocation(bridge_conf_id, endpoint_one_id, ["audio", "video"])
    # Patch the conference to add endpoint one
    allocate_req = to_colibri_string(ciq)
    resp = requests.patch(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT + "/" + bridge_conf_id, json=json.loads(allocate_req))
    self.assertEqual(resp.status_code, 200)
    # Channels for endpoint two
    endpoint_two_id = "endpoint_two"
    ciq = make_channel_allocation(bridge_conf_id, endpoint_two_id, ["audio", "video"])
    # Patch the conference to add endpoint two
    allocate_req = to_colibri_string(ciq)
    resp = requests.patch(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT + "/" + bridge_conf_id, json=json.loads(allocate_req))
    self.assertEqual(resp.status_code, 200)
    # Get the total conference state and verify all the channels are there
    resp = requests.get(VideobridgeRestApiTest.BRIDGE_URL + REST_ENDPOINT + "/" + bridge_conf_id)
    self.assertEqual(resp.status_code, 200)
    resp_json = resp.json()
    # print(json.dumps(resp_json))
    audio_content = get_resp_content(resp_json, "audio")
    self.assertEqual(len(audio_content["channels"]), 2)
    video_content = get_resp_content(resp_json, "video")
    self.assertEqual(len(audio_content["channels"]), 2)

if __name__ == '__main__':
  if len(sys.argv) <= 1:
    print("Usage: %s <bridge_url>" % (sys.argv[0]))
    sys.exit(0)
  bridge_url = sys.argv[1]
  print("Testing against bridge: %s" % (bridge_url))
  VideobridgeRestApiTest.BRIDGE_URL = bridge_url

  suite = unittest.TestLoader().loadTestsFromTestCase(VideobridgeRestApiTest)
  res = XMLTestRunner(verbosity=2, output='test-reports').run(suite)
  sys.exit(0 if res.wasSuccessful() else 1)
