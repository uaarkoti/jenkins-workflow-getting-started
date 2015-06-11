package com.cb.web;

class Tomcat implements Serializable {
  String hostname, port, adminUser, adminPassword, protocol = "http"

  def deploy(war, id, proc) {
    proc(war, deployUrl, id)
  }

  def undeploy(id, proc) {
    proc(undeployUrl, id)
  }

  def getHostUrl() {
    "${protocol}://${hostname}:${port}"
  }

  def getHostAccessUrl() {
    "${protocol}://${adminUser}:${adminPassword}@${hostname}:${port}"
  }

  def getManagerUrl() {
    "${hostAccessUrl}/manager"
  }

  def getDeployUrl() {
    "${managerUrl}/deploy"
  }

  def getUndeployUrl() {
    "${managerUrl}/undeploy"
  }

  String toString() {
    "\t** Tomcat : \n\t\thostname = " + hostname +
    "\n\t\tport = " + port +
    "\n\t\tadminUser = " + adminUser +
    "\n\t\tdeployUrl = " + deployUrl
  }
}
