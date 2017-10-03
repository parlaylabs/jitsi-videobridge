import jsonpickle

def to_colibri_string(colibri_iq):
  return jsonpickle.encode(colibri_iq, unpicklable=False)

# We don't have function to deserialize the response json back into colibri,
#  so these are some helpers to work with the plain json

# Return the content json block corresponding to the given content_name, None if not found
def get_resp_content(resp_json, content_name):
  return next((content for content in resp_json["contents"] if content["name"] == content_name), None)

# Return the channel json block corresponding to the given content_name and endpoint_id
# NOTE: assumes there will be only one channel that meets the given criteria (because so far we don't have
#  a use case that requires supporting >1)
def get_resp_channel(resp_json, content_name, endpoint_id):
  content = get_resp_content(resp_json, content_name)
  return next((channel for channel in content["channels"] if channel["endpoint"] == endpoint_id), None) if "channels" in content else None

# Helper method to do a simple translating all members that use 
#  underscores to use dashes instead (which is the json 
#  format, but not allowed in a member variable name in python)
#  member_dict should be a copy of the objects member dictionary
#  (i.e. via self.__dict__.copy())
def translate_to_json(member_dict):
  needs_replacing = {k:v for (k, v) in member_dict.iteritems() if "_" in k}
  for key, value in needs_replacing.iteritems():
    del member_dict[key]
    member_dict[key.replace("_", "-")] = value

  return member_dict

class ChannelSctpConnectionBase(object):
  def __init__(self, client_id):
    # default values
    self.expire = 1000
    self.initiator = True

    self.endpoint = client_id
    self.channel_bundle_id = client_id

  def __getstate__(self):
    state = self.__dict__.copy()

    # Special case fields that don't match the patterns
    #  handled by 'translate_to_json'
    if "colibri_id" in state:
      del state["colibri_id"]
      state["id"] = self.colibri_id

    return translate_to_json(state)

class Channel(ChannelSctpConnectionBase):
  def __init__(self, client_id):
    super(Channel, self).__init__(client_id)
    self.direction = "sendrecv"

  def __getstate__(self):
    state = super(Channel, self).__getstate__()

    return translate_to_json(state)

class SctpConnection(ChannelSctpConnectionBase):
  def __init__(self, client_id):
    super(SctpConnection, self).__init__(client_id)
    self.port = 5000

class Content(object):
  def __init__(self, name):
    self.name = name

  def add_sctp_connection(self, sctp_connection):
    if not hasattr(self, 'sctpconnections'):
      self.sctpconnections = []
    self.sctpconnections.append(sctp_connection)

  def add_channel(self, channel):
    if not hasattr(self, 'channels'):
      self.channels = []
    self.channels.append(channel)

  def get_channel(self, endpoint_id):
    return next(channel for channel in self.channels if channel.endpoint == endpoint_id) if hasattr(self, 'channels') else None

class Transport(object):
  def __init__(self):
    # default values
    self.xmlns = "urn:xmpp:jingle:transports:ice-udp:1"
    self.rtcp_mux = True

  def __getstate__(self):
    # We need to override the string representation of 'rtcp_mux'
    state = self.__dict__.copy()
    return translate_to_json(state)

class ChannelBundle(object):
  def __init__(self, bundle_id):
    self.bundle_id = bundle_id
    self.transport = Transport()

  def __getstate__(self):
    # We need to override the string representation of 'bundle_id' to 'id'
    state = self.__dict__.copy()
    del state["bundle_id"]
    state["id"] = self.bundle_id
    
    return translate_to_json(state)

class ColibriIq(object):
  def __init__(self, bridge_conf_id):
    self.bridge_conf_id = bridge_conf_id
    self.contents = []
    self.channel_bundles = []
  
  def get_content(self, content_name):
    return next((content for content in self.contents if content.name == content_name), None)

  def __getstate__(self):
    # Override representations of 'bridge_conf_id' 
    state = self.__dict__.copy()
    del state["bridge_conf_id"]
    state["id"] = self.bridge_conf_id

    return translate_to_json(state)
