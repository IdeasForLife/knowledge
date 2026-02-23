package com.mark.knowledge.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mark.knowledge.k8s.KubernetesUtil;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Kubernetes Agent 服务 - 提供与 Kubernetes 集群交互的工具方法
 *
 * 使用方式：
 * 1. 先启动 kubectl proxy: kubectl proxy --port=8001
 * 2. 然后就可以通过 LLM 调用这些工具来查询和管理 K8s 集群
 *
 * 功能示例：
 * - "查看 knowledge 命名空间的所有 Pod"
 * - "获取 Pod xxx 的日志"
 * - "分析 Pod 为什么启动失败"
 * - "查看最近的集群事件"
 *
 * @author mark
 */
@Component
public class KubernetesService {

    private static final Logger log = LoggerFactory.getLogger(KubernetesService.class);

    private final KubernetesUtil k8sUtil;

    public KubernetesService(KubernetesUtil k8sUtil) {
        this.k8sUtil = k8sUtil;
    }

    // ==================== Pod 相关工具 ====================

    /**
     * 测试 Kubernetes 连接
     */
    @Tool("测试与 Kubernetes 集群的连接，列出所有命名空间")
    public String testKubernetesConnection() {
        try {
            JsonNode namespaces = k8sUtil.getNamespaces();
            if (namespaces == null || !namespaces.has("items")) {
                return "❌ 连接失败：无法获取命名空间列表，请检查 kubectl proxy 是否正常运行";
            }

            JsonNode items = namespaces.get("items");
            StringBuilder sb = new StringBuilder();
            sb.append("✅ Kubernetes 连接成功！\n");
            sb.append("找到 ").append(items.size()).append(" 个命名空间:\n");
            for (JsonNode ns : items) {
                String name = getText(ns, "metadata.name");
                sb.append("  - ").append(name).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "❌ 连接失败: " + e.getMessage() + "\n请确保:\n1. kubectl proxy --port=8001 正在运行\n2. KUBECONFIG 配置正确";
        }
    }

    /**
     * 列出指定命名空间的所有 Pod
     */
    @Tool("列出指定命名空间的所有 Pod，包括状态、IP 等信息")
    public String listPods(String namespace) {
        try {
            JsonNode podList = k8sUtil.getPods(namespace);
            if (podList == null || !podList.has("items") || podList.get("items").isEmpty()) {
                return "命名空间 " + namespace + " 中没有找到 Pod";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("命名空间 ").append(namespace).append(" 的 Pod 列表:\n\n");

            for (JsonNode pod : podList.get("items")) {
                String name = getText(pod, "metadata.name");
                String phase = getText(pod, "status.phase");
                String podIP = getText(pod, "status.podIP");
                String nodeName = getText(pod, "spec.nodeName");

                sb.append("📦 Pod: ").append(name).append("\n");
                sb.append("   状态: ").append(phase).append("\n");
                sb.append("   IP: ").append(podIP).append("\n");
                sb.append("   节点: ").append(nodeName).append("\n");

                // 获取容器状态
                JsonNode containerStatuses = pod.at("/status/containerStatuses");
                if (containerStatuses.isArray() && containerStatuses.size() > 0) {
                    int readyCount = 0;
                    int restarts = 0;
                    for (JsonNode cs : containerStatuses) {
                        if (cs.has("ready") && cs.get("ready").asBoolean()) {
                            readyCount++;
                        }
                        if (cs.has("restartCount")) {
                            restarts += cs.get("restartCount").asInt();
                        }
                    }
                    sb.append("   就绪: ").append(readyCount).append("/").append(containerStatuses.size()).append("\n");
                    sb.append("   重启: ").append(restarts).append(" 次\n");
                }
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 获取 Pod 列表失败: " + e.getMessage();
        }
    }

    /**
     * 获取 Pod 详细信息
     */
    @Tool("获取指定 Pod 的详细信息，包括配置、状态、容器信息等")
    public String getPodDetails(String namespace, String podName) {
        try {
            JsonNode pod = k8sUtil.getPod(namespace, podName);
            if (pod == null || !pod.has("metadata")) {
                return "❌ 未找到 Pod: " + namespace + "/" + podName;
            }

            return describePod(namespace, podName, pod);
        } catch (Exception e) {
            return "❌ 获取 Pod 详情失败: " + e.getMessage();
        }
    }

    /**
     * 获取 Pod 日志
     */
    @Tool("获取指定 Pod 的日志，可以查看容器输出和错误信息")
    public String getPodLogs(String namespace, String podName) {
        try {
            return k8sUtil.getPodLogs(namespace, podName, 200);
        } catch (Exception e) {
            return "❌ 获取 Pod 日志失败: " + e.getMessage();
        }
    }

    /**
     * 获取 Pod 日志（指定行数）
     */
    @Tool("获取指定 Pod 的最近 N 行日志，用于快速查看最新日志")
    public String getPodLogsWithLines(String namespace, String podName, int lines) {
        try {
            return k8sUtil.getPodLogs(namespace, podName, lines);
        } catch (Exception e) {
            return "❌ 获取 Pod 日志失败: " + e.getMessage();
        }
    }

    /**
     * 分析 Pod 问题
     */
    @Tool("分析 Pod 的状态、事件和日志，诊断问题原因")
    public String analyzePodIssue(String namespace, String podName) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("🔍 分析 Pod: ").append(namespace).append("/").append(podName).append("\n\n");

            JsonNode pod = k8sUtil.getPod(namespace, podName);
            if (pod == null || !pod.has("metadata")) {
                return "❌ 未找到 Pod: " + namespace + "/" + podName;
            }

            // 1. 检查 Pod 状态
            sb.append("📊 Pod 状态:\n");
            String phase = getText(pod, "status.phase");
            String podIP = getText(pod, "status.podIP");
            String nodeName = getText(pod, "spec.nodeName");
            sb.append("   Phase: ").append(phase).append("\n");
            sb.append("   Pod IP: ").append(podIP).append("\n");
            sb.append("   节点: ").append(nodeName).append("\n");

            // 2. 检查容器状态
            sb.append("\n📦 容器状态:\n");
            JsonNode containerStatuses = pod.at("/status/containerStatuses");
            if (containerStatuses.isArray()) {
                for (JsonNode cs : containerStatuses) {
                    String name = getText(cs, "name");
                    boolean ready = cs.has("ready") && cs.get("ready").asBoolean();
                    int restartCount = cs.has("restartCount") ? cs.get("restartCount").asInt() : 0;

                    sb.append("   ").append(name).append(":\n");
                    sb.append("     就绪: ").append(ready).append("\n");
                    sb.append("     重启: ").append(restartCount).append(" 次\n");

                    // 检查状态
                    JsonNode state = cs.get("state");
                    if (state != null) {
                        if (state.has("waiting")) {
                            JsonNode waiting = state.get("waiting");
                            String reason = getText(waiting, "reason");
                            String message = getText(waiting, "message");
                            sb.append("     ⚠️ 等待中: ").append(reason)
                                    .append(" - ").append(message).append("\n");
                        } else if (state.has("terminated")) {
                            JsonNode terminated = state.get("terminated");
                            String reason = getText(terminated, "reason");
                            int exitCode = terminated.has("exitCode") ? terminated.get("exitCode").asInt() : -1;
                            sb.append("     ❌ 已终止: ").append(reason)
                                    .append(" (exit code: ").append(exitCode).append(")\n");
                        }
                    }
                }
            }

            // 3. 检查事件
            sb.append("\n📝 最近事件:\n");
            try {
                JsonNode events = k8sUtil.getEvents(namespace);
                if (events != null && events.has("items")) {
                    String podNameLower = podName.toLowerCase();
                    int count = 0;
                    // 获取最近 5 个与该 Pod 相关的事件
                    for (int i = events.get("items").size() - 1; i >= 0 && count < 5; i--) {
                        JsonNode event = events.get("items").get(i);
                        JsonNode involvedObj = event.get("involvedObject");
                        if (involvedObj != null) {
                            String objName = getText(involvedObj, "name");
                            if (objName.toLowerCase().equals(podNameLower)) {
                                String type = getText(event, "type");
                                String reason = getText(event, "reason");
                                String message = getText(event, "message");
                                String icon = "Warning".equalsIgnoreCase(type) ? "⚠️" : "ℹ️";
                                sb.append("   ").append(icon).append(" [").append(type).append("] ")
                                        .append(reason).append(": ").append(message).append("\n");
                                count++;
                            }
                        }
                    }
                    if (count == 0) {
                        sb.append("   无最近事件\n");
                    }
                }
            } catch (Exception ignored) {
                sb.append("   无法获取事件\n");
            }

            // 4. 诊断建议
            sb.append("\n💡 诊断建议:\n");
            if ("Pending".equalsIgnoreCase(phase)) {
                sb.append("   Pod 处于 Pending 状态，可能原因:\n");
                sb.append("   - 资源不足，检查节点是否有足够的 CPU 和内存\n");
                sb.append("   - 镜像拉取失败，检查镜像名称和仓库访问权限\n");
                sb.append("   - 存储卷挂载失败，检查 PVC 是否已绑定\n");
            } else if ("Failed".equalsIgnoreCase(phase)) {
                sb.append("   Pod 处于 Failed 状态，可能原因:\n");
                sb.append("   - 应用启动失败，查看日志获取详细错误\n");
                sb.append("   - 健康检查失败，检查 liveness 和 readiness probe 配置\n");
                sb.append("   - OOMKilled，内存限制过低\n");
            } else if ("Running".equalsIgnoreCase(phase)) {
                sb.append("   Pod 正在运行\n");
                if (containerStatuses.isArray()) {
                    for (JsonNode cs : containerStatuses) {
                        boolean ready = cs.has("ready") && cs.get("ready").asBoolean();
                        int restartCount = cs.has("restartCount") ? cs.get("restartCount").asInt() : 0;
                        if (!ready) {
                            sb.append("   - 容器 ").append(getText(cs, "name")).append(" 未就绪，可能仍在启动中\n");
                        }
                        if (restartCount > 0) {
                            sb.append("   - 容器 ").append(getText(cs, "name")).append(" 已重启 ")
                                    .append(restartCount).append(" 次\n");
                        }
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 分析 Pod 失败: " + e.getMessage();
        }
    }

    // ==================== Node 相关工具 ====================

    /**
     * 列出所有节点
     */
    @Tool("列出 Kubernetes 集群的所有节点及其状态")
    public String listNodes() {
        try {
            JsonNode nodeList = k8sUtil.getNodes();
            if (nodeList == null || !nodeList.has("items") || nodeList.get("items").isEmpty()) {
                return "❌ 未找到任何节点";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Kubernetes 集群节点:\n\n");

            for (JsonNode node : nodeList.get("items")) {
                String name = getText(node, "metadata.name");
                sb.append("🖥️  节点: ").append(name).append("\n");
                sb.append("   状态: ");

                // 检查节点 Ready 状态
                JsonNode conditions = node.get("status").get("conditions");
                if (conditions != null && conditions.isArray()) {
                    for (JsonNode condition : conditions) {
                        if ("Ready".equals(getText(condition, "type"))) {
                            String status = getText(condition, "status");
                            sb.append("True".equals(status) ? "✅ Ready" : "❌ NotReady").append("\n");
                            break;
                        }
                    }
                }

                String kernelVersion = getText(node, "status.nodeInfo.kernelVersion");
                String osImage = getText(node, "status.nodeInfo.osImage");
                String containerRuntimeVersion = getText(node, "status.nodeInfo.containerRuntimeVersion");

                sb.append("   内核: ").append(kernelVersion).append("\n");
                sb.append("   OS: ").append(osImage).append("\n");
                sb.append("   容器运行时: ").append(containerRuntimeVersion).append("\n");

                // 资源
                String cpu = getText(node, "status.capacity.cpu");
                String memory = getText(node, "status.capacity.memory");
                String pods = getText(node, "status.capacity.pods");
                sb.append("   CPU: ").append(cpu).append("\n");
                sb.append("   内存: ").append(memory).append("\n");
                sb.append("   Pod: ").append(pods).append("\n");
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 获取节点列表失败: " + e.getMessage();
        }
    }

    /**
     * 获取节点详细信息
     */
    @Tool("获取指定节点的详细信息，包括资源、条件、地址等")
    public String getNodeDetails(String nodeName) {
        try {
            JsonNode node = k8sUtil.getNode(nodeName);
            if (node == null || !node.has("metadata")) {
                return "❌ 未找到节点: " + nodeName;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("节点详情: ").append(nodeName).append("\n\n");

            sb.append("📋 基本信息:\n");
            sb.append("   UID: ").append(getText(node, "metadata.uid")).append("\n");
            sb.append("   创建时间: ").append(getText(node, "metadata.creationTimestamp")).append("\n");
            sb.append("   Pod CIDR: ").append(getText(node, "spec.podCIDR")).append("\n\n");

            sb.append("💻 系统信息:\n");
            sb.append("   OS: ").append(getText(node, "status.nodeInfo.osImage")).append("\n");
            sb.append("   内核版本: ").append(getText(node, "status.nodeInfo.kernelVersion")).append("\n");
            sb.append("   容器运行时: ").append(getText(node, "status.nodeInfo.containerRuntimeVersion")).append("\n\n");

            sb.append("📊 资源容量:\n");
            sb.append("   CPU: ").append(getText(node, "status.capacity.cpu")).append("\n");
            sb.append("   内存: ").append(getText(node, "status.capacity.memory")).append("\n\n");

            sb.append("🔍 节点条件:\n");
            JsonNode conditions = node.get("status").get("conditions");
            if (conditions != null && conditions.isArray()) {
                for (JsonNode condition : conditions) {
                    String type = getText(condition, "type");
                    String status = getText(condition, "status");
                    String icon = "True".equals(status) ? "✅" : "❌";
                    sb.append("   ").append(icon).append(" ").append(type)
                            .append(": ").append(status).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 获取节点详情失败: " + e.getMessage();
        }
    }

    // ==================== Service 相关工具 ====================

    /**
     * 列出指定命名空间的所有 Service
     */
    @Tool("列出指定命名空间的所有 Service，包括类型、端口等信息")
    public String listServices(String namespace) {
        try {
            JsonNode serviceList = k8sUtil.getServices(namespace);
            if (serviceList == null || !serviceList.has("items") || serviceList.get("items").isEmpty()) {
                return "命名空间 " + namespace + " 中没有找到 Service";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("命名空间 ").append(namespace).append(" 的 Service 列表:\n\n");

            for (JsonNode svc : serviceList.get("items")) {
                String name = getText(svc, "metadata.name");
                String type = getText(svc, "spec.type");
                String clusterIP = getText(svc, "spec.clusterIP");

                sb.append("🔌 Service: ").append(name).append("\n");
                sb.append("   类型: ").append(type).append("\n");
                sb.append("   Cluster IP: ").append(clusterIP).append("\n");

                JsonNode ports = svc.get("spec").get("ports");
                if (ports != null && ports.isArray()) {
                    sb.append("   端口:\n");
                    for (JsonNode port : ports) {
                        String portName = getText(port, "name");
                        int portNum = port.has("port") ? port.get("port").asInt() : 0;
                        int targetPort = port.has("targetPort") ? port.get("targetPort").asInt() : 0;
                        String protocol = getText(port, "protocol");
                        sb.append("     - ").append(portName)
                                .append(": ").append(portNum)
                                .append(" -> ").append(targetPort)
                                .append("/").append(protocol).append("\n");
                    }
                }
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 获取 Service 列表失败: " + e.getMessage();
        }
    }

    /**
     * 获取 Service 详细信息
     */
    @Tool("获取指定 Service 的详细信息，包括端口、选择器、端点等")
    public String getServiceDetails(String namespace, String serviceName) {
        try {
            JsonNode svc = k8sUtil.getService(namespace, serviceName);
            if (svc == null || !svc.has("metadata")) {
                return "❌ 未找到 Service: " + namespace + "/" + serviceName;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Service 详情: ").append(namespace).append("/").append(serviceName).append("\n\n");
            sb.append("📋 基本信息:\n");
            sb.append("   类型: ").append(getText(svc, "spec.type")).append("\n");
            sb.append("   Cluster IP: ").append(getText(svc, "spec.clusterIP")).append("\n\n");

            sb.append("🔌 端口配置:\n");
            JsonNode ports = svc.get("spec").get("ports");
            if (ports != null && ports.isArray()) {
                for (JsonNode port : ports) {
                    String name = getText(port, "name");
                    int portNum = port.has("port") ? port.get("port").asInt() : 0;
                    int targetPort = port.has("targetPort") ? port.get("targetPort").asInt() : 0;
                    String protocol = getText(port, "protocol");
                    sb.append("   ").append(name)
                            .append(": ").append(portNum)
                            .append(" -> ").append(targetPort)
                            .append("/").append(protocol).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 获取 Service 详情失败: " + e.getMessage();
        }
    }

    // ==================== Deployment 相关工具 ====================

    /**
     * 列出指定命名空间的所有 Deployment
     */
    @Tool("列出指定命名空间的所有 Deployment，包括副本数、镜像等信息")
    public String listDeployments(String namespace) {
        try {
            JsonNode deployList = k8sUtil.getDeployments(namespace);
            if (deployList == null || !deployList.has("items") || deployList.get("items").isEmpty()) {
                return "命名空间 " + namespace + " 中没有找到 Deployment";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("命名空间 ").append(namespace).append(" 的 Deployment 列表:\n\n");

            for (JsonNode deploy : deployList.get("items")) {
                String name = getText(deploy, "metadata.name");
                int replicas = deploy.at("/spec/replicas").asInt(0);
                int readyReplicas = deploy.at("/status/readyReplicas").asInt(0);
                int updatedReplicas = deploy.at("/status/updatedReplicas").asInt(0);

                sb.append("🚀 Deployment: ").append(name).append("\n");
                sb.append("   副本数: ").append(replicas).append("\n");
                sb.append("   状态: ").append(readyReplicas).append("/").append(replicas).append(" 就绪\n");
                sb.append("   更新: ").append(updatedReplicas).append(" 个副本已更新\n");

                // 显示容器镜像
                JsonNode containers = deploy.at("/spec/template/spec/containers");
                if (containers.isArray()) {
                    sb.append("   容器:\n");
                    for (JsonNode container : containers) {
                        String containerName = getText(container, "name");
                        String image = getText(container, "image");
                        sb.append("     - ").append(containerName)
                                .append(": ").append(image).append("\n");
                    }
                }
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 获取 Deployment 列表失败: " + e.getMessage();
        }
    }

    /**
     * 获取 Deployment 详细信息
     */
    @Tool("获取指定 Deployment 的详细信息，包括副本状态、更新策略、容器配置等")
    public String getDeploymentDetails(String namespace, String deploymentName) {
        try {
            JsonNode deploy = k8sUtil.getDeployment(namespace, deploymentName);
            if (deploy == null || !deploy.has("metadata")) {
                return "❌ 未找到 Deployment: " + namespace + "/" + deploymentName;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Deployment 详情: ").append(namespace).append("/").append(deploymentName).append("\n\n");

            sb.append("📋 基本信息:\n");
            int replicas = deploy.at("/spec/replicas").asInt(0);
            sb.append("   副本数: ").append(replicas).append("\n\n");

            sb.append("📊 状态:\n");
            int updatedReplicas = deploy.at("/status/updatedReplicas").asInt(0);
            int readyReplicas = deploy.at("/status/readyReplicas").asInt(0);
            int availableReplicas = deploy.at("/status/availableReplicas").asInt(0);
            int unavailableReplicas = deploy.at("/status/unavailableReplicas").asInt(0);

            sb.append("   期望副本: ").append(replicas).append("\n");
            sb.append("   当前副本: ").append(updatedReplicas).append("\n");
            sb.append("   就绪副本: ").append(readyReplicas).append("\n");
            sb.append("   可用副本: ").append(availableReplicas).append("\n");
            if (unavailableReplicas > 0) {
                sb.append("   ⚠️ 不可用副本: ").append(unavailableReplicas).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 获取 Deployment 详情失败: " + e.getMessage();
        }
    }

    // ==================== Event 相关工具 ====================

    /**
     * 列出指定命名空间的事件
     */
    @Tool("列出指定命名空间的最近事件，用于排查问题")
    public String listEvents(String namespace) {
        try {
            JsonNode eventList = k8sUtil.getEvents(namespace);
            if (eventList == null || !eventList.has("items") || eventList.get("items").isEmpty()) {
                return "命名空间 " + namespace + " 中没有找到事件";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("命名空间 ").append(namespace).append(" 的最近事件:\n\n");

            JsonNode items = eventList.get("items");
            int start = Math.max(0, items.size() - 10);
            for (int i = start; i < items.size(); i++) {
                JsonNode event = items.get(i);
                String type = getText(event, "type");
                String reason = getText(event, "reason");
                String message = getText(event, "message");
                String lastTimestamp = getText(event, "lastTimestamp");
                int count = event.has("count") ? event.get("count").asInt() : 1;

                String icon = "Warning".equalsIgnoreCase(type) ? "⚠️" : "ℹ️";
                sb.append(icon).append(" [").append(type).append("] ")
                        .append(reason).append("\n");
                sb.append("   消息: ").append(message).append("\n");
                sb.append("   时间: ").append(lastTimestamp).append("\n");
                if (count > 1) {
                    sb.append("   次数: ").append(count).append("\n");
                }
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 获取事件列表失败: " + e.getMessage();
        }
    }

    /**
     * 获取命名空间列表
     */
    @Tool("列出 Kubernetes 集群的所有命名空间")
    public String listNamespaces() {
        try {
            JsonNode nsList = k8sUtil.getNamespaces();
            if (nsList == null || !nsList.has("items") || nsList.get("items").isEmpty()) {
                return "❌ 未找到任何命名空间";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Kubernetes 命名空间列表:\n\n");

            for (JsonNode ns : nsList.get("items")) {
                String name = getText(ns, "metadata.name");
                String phase = getText(ns, "status.phase");
                sb.append("📁 ").append(name);
                if (phase != null && !phase.isEmpty()) {
                    sb.append(" (").append(phase).append(")");
                }
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 获取命名空间列表失败: " + e.getMessage();
        }
    }

    /**
     * 健康检查 - 检查集群整体健康状态
     */
    @Tool("检查 Kubernetes 集群的整体健康状态，包括节点、Pod、事件等")
    public String healthCheck() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("🏥 Kubernetes 集群健康检查\n\n");

            // 1. 检查节点
            sb.append("🖥️  节点状态:\n");
            JsonNode nodes = k8sUtil.getNodes();
            if (nodes != null && nodes.has("items")) {
                int totalNodes = nodes.get("items").size();
                int readyNodes = 0;
                for (JsonNode node : nodes.get("items")) {
                    JsonNode conditions = node.get("status").get("conditions");
                    if (conditions != null && conditions.isArray()) {
                        for (JsonNode condition : conditions) {
                            if ("Ready".equals(getText(condition, "type")) &&
                                    "True".equals(getText(condition, "status"))) {
                                readyNodes++;
                                break;
                            }
                        }
                    }
                }
                sb.append("   就绪: ").append(readyNodes).append("/").append(totalNodes).append("\n");
                if (readyNodes < totalNodes) {
                    sb.append("   ⚠️ 部分节点未就绪！\n");
                } else {
                    sb.append("   ✅ 所有节点就绪\n");
                }
            }

            // 2. 检查系统命名空间的 Pod
            sb.append("\n📦 系统 Pod 状态:\n");
            JsonNode systemPods = k8sUtil.getPods("kube-system");
            if (systemPods != null && systemPods.has("items")) {
                int totalPods = systemPods.get("items").size();
                int runningPods = 0;
                for (JsonNode pod : systemPods.get("items")) {
                    if ("Running".equals(getText(pod, "status.phase"))) {
                        runningPods++;
                    }
                }
                sb.append("   运行中: ").append(runningPods).append("/").append(totalPods).append("\n");
            }

            sb.append("\n💡 建议:\n");
            sb.append("   定期执行健康检查，确保集群稳定运行\n");
            sb.append("   关注节点资源使用率和 Pod 重启次数\n");

            return sb.toString();
        } catch (Exception e) {
            return "❌ 健康检查失败: " + e.getMessage();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取 JsonNode 中的文本值
     */
    private String getText(JsonNode node, String path) {
        try {
            JsonNode target = node.at("/" + path.replace(".", "/"));
            if (target.isMissingNode() || target.isNull()) {
                return "";
            }
            return target.asText();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 描述 Pod
     */
    private String describePod(String namespace, String podName, JsonNode pod) {
        StringBuilder sb = new StringBuilder();

        // 基本信息
        sb.append("Name:         ").append(getText(pod, "metadata.name")).append("\n");
        sb.append("Namespace:    ").append(getText(pod, "metadata.namespace")).append("\n");
        sb.append("Node:         ").append(getText(pod, "spec.nodeName")).append("\n");

        // 状态信息
        String phase = getText(pod, "status.phase");
        String startTime = getText(pod, "status.startTime");
        sb.append("Phase:        ").append(phase).append("\n");
        sb.append("Start Time:   ").append(startTime).append("\n");

        // 最近事件
        sb.append("\nRecent Events:\n");
        try {
            JsonNode events = k8sUtil.getEvents(namespace);
            if (events != null && events.has("items")) {
                String podNameLower = podName.toLowerCase();
                int count = 0;
                for (int i = events.get("items").size() - 1; i >= 0 && count < 5; i--) {
                    JsonNode event = events.get("items").get(i);
                    JsonNode involvedObj = event.get("involvedObject");
                    if (involvedObj != null) {
                        String objName = getText(involvedObj, "name");
                        if (objName.toLowerCase().equals(podNameLower)) {
                            String type = getText(event, "type");
                            String reason = getText(event, "reason");
                            sb.append("  ").append(type).append("    ").append(reason).append("\n");
                            count++;
                        }
                    }
                }
                if (count == 0) {
                    sb.append("  No recent events\n");
                }
            }
        } catch (Exception ignored) {
            sb.append("  Unable to fetch events\n");
        }

        return sb.toString();
    }
}
