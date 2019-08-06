#!/usr/bin/groovy
import io.fabric8.Utils
import io.fabric8.Fabric8Commands

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new Fabric8Commands()
    def utils = new Utils()

    def expose = config.exposeApp ?: 'true'
    def requestCPU = config.resourceRequestCPU ?: '0'
    def requestMemory = config.resourceRequestMemory ?: '0'
    def limitCPU = config.resourceLimitCPU ?: '0'
    def limitMemory = config.resourceLimitMemory ?: '0'
    def replicas = config.replicaCount ?: '1'
    def health_uri = config.healthUri ?: '/'
    def health_port = config.healthPort ?: "${config.port}"
    def image_name = config.imageName ?: "${fabric8Registry}${env.KUBERNETES_NAMESPACE}/${env.JOB_NAME}:${config.version}"
    def custom_environment = config.customEnvironment ?: ''
    def yaml

    def isSha = ''

    def fabric8Registry = ''
    if (env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST){
        fabric8Registry = env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST+'/'
    }

    def sha
    def list = """
---
apiVersion: v1
kind: List
items:
"""

def service = """
- apiVersion: v1
  kind: Service
  metadata:
    annotations:
      fabric8.io/iconUrl: ${config.icon}
    labels:
      provider: fabric8
      project: ${env.JOB_NAME}
      expose: '${expose}'
      version: ${config.version}
      group: quickstart
    name: ${env.JOB_NAME}
  spec:
    ports:
    - port: 80
      protocol: TCP
      targetPort: ${config.port}
    selector:
      project: ${env.JOB_NAME}
      provider: fabric8
      group: quickstart
"""

def deployment = """
- apiVersion: extensions/v1beta1
  kind: Deployment
  metadata:
    annotations:
      fabric8.io/iconUrl: ${config.icon}
    labels:
      provider: fabric8
      project: ${env.JOB_NAME}
      version: ${config.version}
      group: quickstart
    name: ${env.JOB_NAME}
  spec:
    replicas: ${replicas}
    selector:
      matchLabels:
        provider: fabric8
        project: ${env.JOB_NAME}
        group: quickstart
    template:
      metadata:
        labels:
          provider: fabric8
          project: ${env.JOB_NAME}
          version: ${config.version}
          group: quickstart
      spec:
        containers:
        - env:
          - name: KUBERNETES_NAMESPACE
            valueFrom:
              fieldRef:
                fieldPath: metadata.namespace
          ${custom_environment}
          image: ${fabric8Registry}${env.KUBERNETES_NAMESPACE}/${env.JOB_NAME}:${config.version}
          imagePullPolicy: IfNotPresent
          name: ${env.JOB_NAME}
          ports:
          - containerPort: ${config.port}
            name: http
          resources:
            limits:
              cpu: ${requestCPU}
              memory: ${requestMemory}
            requests:
              cpu: ${limitCPU}
              memory: ${limitMemory}
          readinessProbe:
            httpGet:
              path: "${health_uri}"
              port: ${health_port}
            initialDelaySeconds: 60
            timeoutSeconds: 5
            failureThreshold: 5
          livenessProbe:
            httpGet:
              path: "${health_uri}"
              port: ${health_port}
            initialDelaySeconds: 180
            timeoutSeconds: 5
            failureThreshold: 5
        terminationGracePeriodSeconds: 2
"""

def deploymentConfig = """
- apiVersion: v1
  kind: DeploymentConfig
  metadata:
    annotations:
      fabric8.io/iconUrl: ${config.icon}
    labels:
      provider: fabric8
      project: ${env.JOB_NAME}
      version: ${config.version}
      group: quickstart
    name: ${env.JOB_NAME}
  spec:
    replicas: ${replicas}
    selector:
      provider: fabric8
      project: ${env.JOB_NAME}
      group: quickstart
    template:
      metadata:
        labels:
          provider: fabric8
          project: ${env.JOB_NAME}
          version: ${config.version}
          group: quickstart
      spec:
        containers:
        - env:
          - name: KUBERNETES_NAMESPACE
            valueFrom:
              fieldRef:
                fieldPath: metadata.namespace
          image: ${env.JOB_NAME}:${config.version}
          imagePullPolicy: IfNotPresent
          name: ${env.JOB_NAME}
          ports:
          - containerPort: ${config.port}
            name: http
          resources:
            limits:
              cpu: ${requestCPU}
              memory: ${requestMemory}
            requests:
              cpu: ${limitCPU}
              memory: ${limitMemory}
          readinessProbe:
            httpGet:
              path: "${health_uri}"
              port: ${health_port}
            initialDelaySeconds: 1
            timeoutSeconds: 5
            failureThreshold: 5
          livenessProbe:
            httpGet:
              path: "${health_uri}"
              port: ${health_port}
            initialDelaySeconds: 180
            timeoutSeconds: 5
            failureThreshold: 5
        terminationGracePeriodSeconds: 2
    triggers:
    - type: ConfigChange
    - imageChangeParams:
        automatic: true
        containerNames:
        - ${env.JOB_NAME}
        from:
          kind: ImageStreamTag
          name: ${env.JOB_NAME}:${config.version}
      type: ImageChange
"""

    def is = """
- apiVersion: v1
  kind: ImageStream
  metadata:
    name: ${env.JOB_NAME}
  spec:
    tags:
    - from:
        kind: ImageStreamImage
        name: ${env.JOB_NAME}@${isSha}
        namespace: ${utils.getNamespace()}
      name: ${config.version}
"""

  yaml = list + service + deployment

  echo 'using resources:\n' + yaml
  return yaml

  }
