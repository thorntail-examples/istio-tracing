# Istio distributed tracing example for Thorntail

## Purpose

Showcase distributed tracing of Thorntail applications in Istio with Jaeger

## Preparing Istio environment

Here's a short howto for getting Istio up and running with mTLS enabled, based on OpenShift 3.11 on Linux using `istiooc`.
It assumes Docker is installed and running.

- Download the latest `istiooc` release:

  ```bash
  cd ~/bin   # should be on PATH
  wget -O istiooc https://github.com/Maistra/origin/releases/download/v3.11.0%2Bmaistra-0.9.0/istiooc_linux
  chmod +x istiooc
  ```

  Note that this one is based on Maistra 0.9.0 `openshift-ansible` release: https://github.com/Maistra/openshift-ansible/releases/tag/maistra-0.9.0

- Start the single-node OpenShift cluster:

  ```bash
  istiooc cluster up
  ```

  Note this will apply the the istio-operator described here: https://github.com/Maistra/openshift-ansible/blob/maistra-0.10.0/istio/Installation.md#installing-the-istio-operator

- Login as system:admin:

  ```bash
  oc login -u system:admin
  ```

  Note: at this point, you can create a cluster admin user that can log into the console. Run

  ```bash
  oc create user admin
  oc adm policy add-cluster-role-to-user cluster-admin admin
  ```

  and use the `admin` username afterwards.

- Apply `anyuid` and `privileged` permissions to the service account of the project you are going to use.
Typically, it will be the `default` service account in the `myproject` project:

  ```bash
  oc adm policy add-scc-to-user anyuid system:serviceaccount:myproject:default
  oc adm policy add-scc-to-user privileged system:serviceaccount:myproject:default
  ```

- Deploy the Istio control plane.
  If you also want to install the Fabric8 Launcher, edit the `istio-installation.yaml` file.

  ```bash
  oc apply -f istio-installation.yaml
  ```

- Wait for the Istio control plane deployment to finish. Run

  ```bash
  oc get pods -n istio-system -w
  ```

  or open the OpenShift web console and wait until everything settles down.
  In the end, running `oc get pods -n istio-system` should show something like this:

  ```
  NAME                                          READY     STATUS      RESTARTS   AGE
  elasticsearch-0                               1/1       Running     0          1m
  grafana-74b5796d94-796tc                      1/1       Running     0          1m
  istio-citadel-5d595fd7d7-w9lqd                1/1       Running     0          2m
  istio-egressgateway-594684895-tp9w7           1/1       Running     0          2m
  istio-galley-699f48cb-xbxf4                   1/1       Running     0          2m
  istio-ingressgateway-568cd96f58-gb2fm         1/1       Running     0          2m
  istio-pilot-99fc457ff-h59d5                   2/2       Running     0          2m
  istio-policy-85d8ffdb98-nn8kw                 2/2       Running     3          2m
  istio-sidecar-injector-5d5bf88c78-c2hks       1/1       Running     0          2m
  istio-telemetry-5c75d844cf-s76x9              2/2       Running     3          2m
  jaeger-agent-lbcnv                            1/1       Running     0          1m
  jaeger-collector-86d57594d5-7lktp             1/1       Running     1          1m
  jaeger-query-f96b97b75-bm2sw                  1/1       Running     1          1m
  kiali-59fd54974c-4c9xz                        1/1       Running     0          1m
  openshift-ansible-istio-installer-job-ddvdr   0/1       Completed   0          4m
  prometheus-75b849445c-8n8rv                   1/1       Running     0          2m
  ```

- Remember the URL of the Istio ingress gateway:

  ```bash
  ISTIO_INGRESS=$(oc get route istio-ingressgateway -n istio-system -o jsonpath='{.spec.host}')
  ```
## Deploy the application

### Launcher flow

If you installed Fabric8 Launcher, you can deploy the example application using its Continuous Delivery flow.
In such case, no manual deployment steps are necessary.

Go to the _Verifying use cases_ section.

### Manual build

#### Prepare the namespace

Move to your namespace/project:

```bash
oc login -u developer
oc project myproject
```

This is the project to which the `anyuid` and `privileged` permissions were added during Istio preparation.

#### Build and deploy the application

You can build and deploy the example application using either Fabric8 Maven plugin, or OpenShift templates and S2I.

##### Using Fabric8 Maven plugin

Build and deploy the services directly:

```bash
cd cute-name-service
mvn clean fabric8:deploy -Popenshift
cd ..

cd greeting-service
mvn clean fabric8:deploy -Popenshift
cd ..
```

##### Using S2I

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

## Verifying use cases

Any steps issuing `oc` commands require the user to have run `oc login` first and switched to the appropriate project with `oc project <project name>`.

### Create and view application traces

1. Create a Gateway and Virtual Service in Istio so that we can access the service within the service mesh:

    ```bash
    oc apply -f istio-gateway.yaml
    ```

1. Retrieve the URL for the Istio ingress gateway route, with the below command, and open it in a web browser.

    ```bash
    echo http://$ISTIO_INGRESS/thorntail-istio-tracing
    ```

1. On the example application web page, click the "Invoke" button. You should see a "cute" hello message appear in the result box.

1. Follow the instructions in the webpage to access the Jaeger UI to view the application traces.
