def createRegistry(certificateFolder,tlsCertificateFilename, tlsKeyFilename) {
  sh("docker stop registry || true")
  sh("docker rm registry || true")
  sh("docker system prune -f")
  sh("docker run -d --restart=always --name registry -v ${certificateFolder}:/certs -e REGISTRY_HTTP_ADDR=0.0.0.0:443 -e REGISTRY_HTTP_TLS_CERTIFICATE=/certs/${tlsCertificateFilename} -e REGISTRY_HTTP_TLS_KEY=/certs/${tlsKeyFilename} -p 443:443 registry:2")
}

return this
