# Istio distributed tracing example for Thorntail

This example demonstrates distributed tracing of Thorntail applications in Istio with Jaeger.

## Preparing Istio environment

Here's a short howto for getting Istio up and running with mTLS enabled, based on Minishift and Maistra 0.10.
It assumes that Minishift version 1.33 is installed and works correctly.
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

- Configure the Minishift virtual machine so that Elasticsearch can run:

  ```bash
  minishift ssh -- sudo sysctl vm.max_map_count=262144
  ```

- Login as an admin:

  ```bash
  oc login -u admin
  ```

  You can use an arbitrary password.
  Note that you can also use the `admin` username to login to the OpenShift web console.
  URL of the web console is printed near the end of the `minishift start` command output. 

- Install the Istio operator:

  ```bash
  oc new-project istio-operator
  oc apply -n istio-operator -f https://raw.githubusercontent.com/Maistra/istio-operator/maistra-0.11.0/deploy/maistra-operator.yaml
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

- Wait for the Istio control plane deployment to finish. Run

  ```bash
  oc get pods -n istio-system -w
  ```

  or open the `istio-system` project in the OpenShift web console and wait until everything settles down.
  In the end, running `oc get pods -n istio-system` should show something like this:

  ```
  NAME                                      READY     STATUS    RESTARTS   AGE
  elasticsearch-0                           1/1       Running   0          7m
  grafana-6c5dfdf5bd-w2lfp                  1/1       Running   0          8m
  istio-citadel-7fcc8975c7-64w7d            1/1       Running   0          12m
  istio-egressgateway-68cb55b699-n7png      1/1       Running   0          8m
  istio-galley-59cb8d654d-cw494             1/1       Running   0          12m
  istio-ingressgateway-6568f7f6f4-f57ph     1/1       Running   0          8m
  istio-pilot-9965d7d9d-r4lbc               2/2       Running   0          10m
  istio-policy-8fdd8b6f8-dlvbt              2/2       Running   0          10m
  istio-sidecar-injector-6d6cbf8877-zc2qz   1/1       Running   0          8m
  istio-telemetry-5d84ffbf8f-sx2zg          2/2       Running   0          10m
  jaeger-agent-d7r2c                        1/1       Running   0          7m
  jaeger-collector-598b9779b9-df64j         1/1       Running   6          7m
  jaeger-query-6d9864755f-wtwz8             1/1       Running   6          7m
  kiali-85c9557b47-gblv5                    1/1       Running   0          3m
  prometheus-5dfcf8dcf9-clxg8               1/1       Running   0          11m
  ```

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

This is the project to which the `anyuid` and `privileged` permissions were added during Istio preparation.

> At this point, it is possible to run automated tests: `mvn clean verify -Popenshift,openshift-it`.
> This command will build and deploy both services, create the Istio gateway, run a test, and finally clean up.

### Build and deploy the application

You can build and deploy the example application using either Fabric8 Maven plugin, or OpenShift templates and S2I.

#### Using Fabric8 Maven plugin

Build and deploy the services directly:

```bash
cd cute-name-service
mvn clean fabric8:deploy -Popenshift
cd ..

cd greeting-service
mvn clean fabric8:deploy -Popenshift
cd ..
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
