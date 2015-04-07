import sys
from resource_management import *
from subprocess import call
 
class StoreClient(Script):
  def install(self, env):
    print 'Install the client';
  def configure(self, env):
    print 'Configure the client';
  def restart_ambari(self, env):
    print 'Restart Ambari'
    call(["ambari-server", "restart"])

if __name__ == "__main__":
  StoreClient().execute()
