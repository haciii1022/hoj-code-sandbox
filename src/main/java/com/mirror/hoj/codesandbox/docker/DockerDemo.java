package com.mirror.hoj.codesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;

/**
 * @author Mirror
 * @date 2024/8/28
 */
public class DockerDemo {
    public static void main(String[] args) throws InterruptedException {
        //拉取镜像
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "hello-world:latest";
//        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
//        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
//            @Override
//            public void onNext(PullResponseItem item) {
//                System.out.println("下载镜像：" + item.getStatus());
//                super.onNext(item);
//            }
//        };
//        pullImageCmd
//                .exec(pullImageResultCallback)
//                .awaitCompletion();
//        System.out.println("下载完成");

        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd
//                .withCmd("echo", "Hello Docker222")
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();
        //查看容器状态
//        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
//        List<Container> containerList = listContainersCmd.withShowAll(true).exec();
//        for (Container container : containerList) {
//            System.out.println(container);
//        }
        //启动容器
//        String containerId ="bfafa57abe011234f9da2d2267d4555174c2c7a192ff28abc366b8b1a0604bc5";
        dockerClient.startContainerCmd(containerId).exec();


        // 查看日志
        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item) {
                System.out.println(item.getStreamType());
                System.out.println("日志：" + new String(item.getPayload()));
                super.onNext(item);
            }
        };

        // 阻塞等待日志输出
        dockerClient.logContainerCmd(containerId)
                .withStdErr(true)
                .withStdOut(true)
                .exec(logContainerResultCallback)
                .awaitCompletion();

    }
}
