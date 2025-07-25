set -ex

export NS=blindspot-prod

kubectl delete secret blindspot-secret --namespace=$NS --ignore-not-found

kubectl create secret generic --namespace=$NS \
  blindspot-secret \
  --from-literal=postgres_host=pg-one-postgresql.goo-prod.svc.cluster.local \
  --from-literal=postgres_port=5432 \
  --from-literal=postgres_user=$POSTGRES_PROD_USER \
  --from-literal=postgres_password=$POSTGRES_PROD_PASSWORD \
  --from-literal=postgres_db=$POSTGRES_PROD_DB \
  --from-literal=http_proxy=$PROD_HTTP_PROXY \
  --from-literal=http_proxy_pass=$PROD_HTTP_PROXY_PASS \
  --from-literal=http_proxy_user=$PROD_HTTP_PROXY_USER

kubectl delete secret ssh-private-key --namespace=$NS --ignore-not-found

# kubectl create secret generic --namespace=$NS \
#   ssh-private-key \
#   --from-file=id_rsa_og_events=/Users/oto/.ssh/id_rsa_og_events
