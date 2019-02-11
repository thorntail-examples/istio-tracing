# Istio Distributed Tracing Mission for Thorntail

## Purpose

Showcase Distributed Tracing in Istio with Jaeger in Thorntail applications

## Prerequisites

* Docker installed and running
* OpenShift and Istio environment up and running with mTLS enabled.

See https://github.com/openshift-istio/istio-docs/blob/master/content/rhoar-workflow.adoc for details about the Launcher workflows and setting up the docker insecure registry required to run the istiooc.

Here is the sequence showing how to get up and running with the latest OpenShift 3.11 based istiooc release.

- Download the latest istiooc release, for example:
```
mkdir istiooc && cd istiooc
wget -O oc https://github.com/Maistra/origin/releases/download/v3.11.0%2Bmaistra-0.7.0/istiooc_linux
chmod +x oc
export PATH=/path/to/istiooc:$PATH
```

Note in this case it is based on Maistra 0.7.0 openshift-ansible release:
https://github.com/Maistra/openshift-ansible/releases/tag/maistra-0.7.0

- Start the cluster:
```
oc cluster up
```

Note this will apply the the istio-operator described here:
https://github.com/Maistra/openshift-ansible/blob/maistra-0.7.0/istio/Installation.md#installing-the-istio-operator

- Login as system:admin, create an admin user and relogin as admin:admin
```
oc create user admin
oc adm policy add-cluster-role-to-user cluster-admin admin
oc login
```

- Apply `anyuid` and `privileged` permissions to the service account of the project you are going to use to test the booster services, by default it will be a `default` account in the project `MyProject`:

```
oc adm policy add-scc-to-user anyuid system:serviceaccount:myproject:default
oc adm policy add-scc-to-user privileged system:serviceaccount:myproject:default
```

- Deploy the Istio control plane and the Fabric8 launcher:

```
oc create -f cr-full.yaml
```

where `cr-full.yaml` should look like this:

```
apiVersion: "istio.openshift.com/v1alpha1"
kind: "Installation"
metadata:
  name: "istio-installation"
  namespace: istio-operator
spec:
  deployment_type: openshift
  istio:
    authentication: true
    community: false
    prefix: registry.access.redhat.com/openshift-istio-tech-preview/ 
    version: 0.7.0
  jaeger:
    prefix: registry.access.redhat.com/distributed-tracing-tech-preview/
    version: 1.9.0
    elasticsearch_memory: 1Gi
  launcher:
    openshift:
      user: admin
      password: admin
    github:
      username: YOUR_GIT_ACCOUNT_ID
      token: YOUR_GIT_ACCOUNT_TOKEN
```

where the GIT token should have `public_repo`, `read:org`, and `admin:repo_hook` permissions. 

The more complete version may look like this:
https://github.com/Maistra/openshift-ansible/blob/maistra-0.7.0/istio/cr-full.yaml

but the one above is sufficient for testing the boosters. Note, setting `istio.authentication` to `true` enables MTLS.

- Verify the Istio Control Plane and Launcher deployments:

https://github.com/Maistra/openshift-ansible/blob/maistra-0.7.0/istio/Installation.md#verifying-the-istio-control-plane

Now proceed to testing the booster.

## Launcher Flow Setup

If the Booster is installed through the Launcher and the Continuous Delivery flow, no additional steps are necessary.

Skip to the _Use Cases_ section.

## Local Source to Image Build (S2I)

### Prepare the Namespace

Create a new namespace/project:
```
oc new-project <whatever valid project name you want>
```

### Build and Deploy the Application

#### With Source to Image build (S2I)

Run the following commands to apply and execute the OpenShift templates that will configure and deploy the applications:
```bash
find . | grep openshiftio | grep application | xargs -n 1 oc apply -f

oc new-app --template=thorntail-istio-tracing-greeting-service -p SOURCE_REPOSITORY_URL=https://github.com/thorntail-examples/istio-tracing -p SOURCE_REPOSITORY_REF=master -p SOURCE_REPOSITORY_DIR=greeting-service
oc new-app --template=thorntail-istio-tracing-cute-name-service -p SOURCE_REPOSITORY_URL=https://github.com/thorntail-examples/istio-tracing -p SOURCE_REPOSITORY_REF=master -p SOURCE_REPOSITORY_DIR=cute-name-service
```

## Use Cases

Any steps issuing `oc` commands require the user to have run `oc login` first and switched to the appropriate project with `oc project <project name>`.

### Create and view application traces

1. Create a Gateway and Virtual Service in Istio so that we can access the service within the Mesh:
    ```
    oc apply -f istio-config/gateway.yaml
    ```
2. Retrieve the URL for the Istio Ingress Gateway route, with the below command, and open it in a web browser.
    ```
    echo http://$(oc get route istio-ingressgateway -o jsonpath='{.spec.host}{"\n"}' -n istio-system)/thorntail-istio-tracing
    ```
3. The user will be presented with the web page of the Booster
4. Click the "Invoke" button. You should see a "cute" hello message appear in the result box.
5. Follow the instructions in the webpage to access the Jaeger UI to view the application traces.
