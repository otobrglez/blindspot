alias b := docker-build-all
host := `uname -a`
service_version := `cat VERSION`

refresh-version:
   echo "version := \"{{service_version}}\"" > version.sbt & \
    yq e '.images[] |= select(.name == "registry.ogrodje.si/otobrglez/blindspot-ui") .newTag = "'{{service_version}}'"' \
      -i k8s/base/kustomization.yaml & \
          yq e '.images[] |= select(.name == "registry.ogrodje.si/otobrglez/blindspot") .newTag = "'{{service_version}}'"' \
            -i k8s/base/kustomization.yaml

docker-login:
  echo $DOCKER_REGISTRY_PASS | docker login registry.ogrodje.si -u $DOCKER_REGISTRY_USER --password-stdin

docker-build-ui: refresh-version
  docker build \
    --platform linux/amd64 \
    --build-arg blindspot_env=Production \
    --build-arg blindspot_version={{service_version}} \
    --build-arg blindspot_endpoint=https://blindspot-api.pinkstack.com/ \
    -t otobrglez/blindspot-ui \
    -t registry.ogrodje.si/otobrglez/blindspot-ui \
    -t registry.ogrodje.si/otobrglez/blindspot-ui:{{service_version}} \
    blindspot-ui

docker-push-ui: docker-login
  docker push --all-tags registry.ogrodje.si/otobrglez/blindspot-ui

docker-build-backend: refresh-version
  sbt "Docker/publishLocal"

docker-push-backend:
  docker push --all-tags registry.ogrodje.si/otobrglez/blindspot

docker-build-all: docker-build-ui docker-build-backend
docker-push-all: docker-push-backend docker-push-ui

k9s-deploy-all:
  kubectl apply -k k8s/base -n blindspot-prod

deploy-backend: docker-build-backend docker-push-backend
deploy-ui: docker-build-ui docker-push-ui

# It is better idea to deploy backend and then ui
deploy-all: docker-build-all docker-push-all k9s-deploy-all
