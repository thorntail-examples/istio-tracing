# Install Istio command

Ensure Docker is installed and running.

Download the binary for your operating system from https://github.com/openshift-istio/origin/releases

# Start OpenShift/Istio

```
istiooc cluster up --istio
```

# Prepare the Namespace

**This mission assumes that `myproject` namespace is used.**

Create the namespace if one does not exist:
```
oc new-project myproject
```

# Deploy the Application

```
oc login -u developer -p pwd
mvn clean package -pl cute-name-service fabric8:build -Popenshift
mvn clean package -pl greeting-service fabric8:build -Popenshift
oc create -f ./config/application.yaml
```

# Use the Application

Get ingress route (further referred as ${INGRESS_ROUTE}):

```
oc login -u system:admin
oc get route -n istio-system
```

Copy and paste the HOST/PORT value that starts with `istio-ingress-istio-system` into your browser.

You will be presented with the UI. Click on "Invoke" to get the response of "Hello World".

Open a new browser window, copying and pasting the HOST/PORT value that starts with `jaeger-query-istio-system`.
Select `istio-ingress` from the "Service" dropdown and click "Find Traces".
