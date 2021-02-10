# Istio distributed tracing example for Thorntail

This example demonstrates distributed tracing of Thorntail applications in Istio with Jaeger.

## Preparing Istio environment

Here's a short howto for getting Istio up and running with mTLS enabled, based on Minishift and Maistra 1.0.
It assumes that Minishift version 1.34.1 is installed and works correctly.
It also assumes that you can run the `oc` binary.

- Verify Minishift and `oc`, and make sure there's no Minishift profile named `istio`:

  ```bash
  oc version
  minishift version
  minishift profile list
  ```

- Create an extra Minishift profile just for Istio and start the VM:

  ```bash
  minishift profile set istio
  minishift config set cpus 4
  minishift config set memory 8GB
  minishift config set disk-size 50g
  minishift config set iso-url centos
  minishift config set vm-driver virtualbox
  minishift addon enable admin-user
  minishift addon enable admissions-webhook
  minishift start
  ```

- Login as an admin:

  ```bash
  oc login -u system:admin
  ```

  Note that if you want to use the OpenShift web console, you can use the `admin` username with an arbitrary password.
  URL of the web console is printed near the end of the `minishift start` command output. 

- Install the Jaeger operator:

  ```bash
  oc new-project observability
  oc create -n observability -f https://raw.githubusercontent.com/jaegertracing/jaeger-operator/v1.13.1/deploy/crds/jaegertracing_v1_jaeger_crd.yaml
  oc create -n observability -f https://raw.githubusercontent.com/jaegertracing/jaeger-operator/v1.13.1/deploy/service_account.yaml
  oc create -n observability -f https://raw.githubusercontent.com/jaegertracing/jaeger-operator/v1.13.1/deploy/role.yaml
  oc create -n observability -f https://raw.githubusercontent.com/jaegertracing/jaeger-operator/v1.13.1/deploy/role_binding.yaml
  oc create -n observability -f https://raw.githubusercontent.com/jaegertracing/jaeger-operator/v1.13.1/deploy/operator.yaml
  ```

- Wait for the Jaeger operator deployment to finish. Run

  ```bash
  oc get pods -n observability -w
  ```

  or open the `observability` project in the OpenShift web console and wait until everything settles down.
  In the end, running `oc get pods -n observability` should show something like this:

  ```
  NAME                              READY     STATUS    RESTARTS   AGE
  jaeger-operator-5bcd7ff5df-59msz   1/1       Running   0          1m
  ```

- Install the Kiali operator:

  ```bash
  export HELM_REPO_CHART_VERSION=v1.29.0
  bash <(curl -L https://kiali.io/getLatestKialiOperator) --operator-watch-namespace '**' --accessible-namespaces '**' --operator-install-kiali false
  ```

  The install script will wait for the operator deployment to finish.

- Install the Istio operator:

  ```bash
  oc new-project istio-operator
  oc apply -n istio-operator -f https://raw.githubusercontent.com/Maistra/istio-operator/maistra-1.0.0/deploy/maistra-operator.yaml
  ```

- Wait for the Istio operator deployment to finish. Run

  ```bash
  oc get pods -n istio-operator -w
  ```

  or open the `istio-operator` project in the OpenShift web console and wait until everything settles down.
  In the end, running `oc get pods -n istio-operator` should show something like this:

  ```
  NAME                              READY     STATUS    RESTARTS   AGE
  istio-operator-6bbff5c77b-nkms6   1/1       Running   0          1m
  ```

- Deploy the Istio control plane.

  ```bash
  oc new-project istio-system
  oc apply -f istio-installation.yaml
  ```

  Notice that the `ServiceMeshMemberRoll` resource defined in `istio-installation.yaml` needs to list all the projects that will be used.
  If you deploy the file without changes, it will only include the `myproject` project.

- Wait for the Istio control plane deployment to finish. Run

  ```bash
  oc get pods -n istio-system -w
  ```

  or open the `istio-system` project in the OpenShift web console and wait until everything settles down.
  In the end, running `oc get pods -n istio-system` should show something like this:

  ```
  NAME                                      READY     STATUS    RESTARTS   AGE
  grafana-f4dd88dd5-98hnh                   2/2       Running   0          6m
  istio-citadel-9c7f79d9c-7mkjk             1/1       Running   0          10m
  istio-egressgateway-5444c946f8-sdq89      1/1       Running   0          6m
  istio-galley-6494c7f649-7zwgh             1/1       Running   0          8m
  istio-ingressgateway-58bb47b869-zlc59     1/1       Running   0          6m
  istio-pilot-769846cc7-mr9gc               2/2       Running   0          7m
  istio-policy-8694bcd49c-trg5z             2/2       Running   0          7m
  istio-sidecar-injector-7f888f5d68-zc7xm   1/1       Running   0          6m
  istio-telemetry-557d5f9c5c-gfkqh          2/2       Running   0          7m
  jaeger-5c999c56f8-kkj9s                   2/2       Running   0          8m
  prometheus-8d957b748-frxwp                2/2       Running   0          10m
  ```

- Reconfigure Jaeger to allow insecure access:

  ```bash
  oc patch jaeger jaeger --patch '{"spec": {"ingress": {"security": "none"}}}' -n istio-system --type merge
  ```

  This is at the very least required for the automated test present in this repository.

- Add `view` role in the `istio-system` project to all authenticated users.
  This will let them discover URLs of Istio ingress or Jaeger UI.
  It is also a prerequisite for running automated tests (see below).

  ```bash
  oc adm policy add-role-to-group view system:authenticated
  ```

- Apply `anyuid` and `privileged` permissions to the service account of the project you are going to use.
  Typically, it will be the `default` service account in the `myproject` project:

  ```bash
  oc adm policy add-scc-to-user anyuid system:serviceaccount:myproject:default
  oc adm policy add-scc-to-user privileged system:serviceaccount:myproject:default
  ```

## Deploying and testing the application

Move to your namespace/project:

```bash
oc login -u developer
oc project myproject
```

This is the project which is listed in the `ServiceMeshMemberRoll` and to which the `anyuid` and `privileged` permissions were added during Istio preparation.

> At this point, it is possible to run automated tests: `mvn clean verify -Popenshift,openshift-it`.
> This command will build and deploy both services, create the Istio gateway, run a test, and finally clean up.

### Build and deploy the application

You can build and deploy the example application using either JKube OpenShift Maven plugin, or OpenShift templates and S2I.

#### Using JKube OpenShift Maven plugin

Build and deploy the services directly:

```bash
mvn clean oc:deploy -Popenshift
```

#### Using S2I

Run the following commands to import and apply the provided OpenShift templates.
OpenShift will then build and deploy the services:

```bash
oc apply -f ./cute-name-service/.openshiftio/application.yaml
oc new-app --template=thorntail-istio-tracing-cute-name

oc apply -f ./greeting-service/.openshiftio/application.yaml
oc new-app --template=thorntail-istio-tracing-greeting
```

If you want to use a different repository or a branch, you can pass the corresponding parameters to the `oc new-app` command.
For example:

```bash
oc new-app --template=thorntail-istio-tracing-cute-name -p SOURCE_REPOSITORY_URL=https://github.com/thorntail-examples/istio-tracing -p SOURCE_REPOSITORY_REF=master
```

### Use the application and view the traces

1. Create a Gateway and Virtual Service in Istio so that we can access the service within the service mesh:

    ```bash
    oc apply -f istio-gateway.yaml
    ```

1. Retrieve the URL for the Istio ingress gateway route and open it in a web browser:

    ```bash
    xdg-open http://$(oc get route istio-ingressgateway -n istio-system -o jsonpath='{.spec.host}')/thorntail-istio-tracing
    ```

1. On the example application web page, click the "Invoke" button. You should see a "cute" hello message appear in the result box.

1. Follow the instructions in the webpage to access the Jaeger UI to view the application traces.

## Cleaning Istio environment

We created an extra Minishift profile, which means it's an extra virtual machine.
That is, it doesn't interfere with your normal Minishift environment.
Cleaning up is very simple, just remove the new profile:

```bash
minishift stop
minishift profile delete istio --force
```

Minishift will switch back to the default profile automatically.
